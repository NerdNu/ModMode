package cc.co.traviswatkins;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nijiko.permissions.PermissionHandler;
import java.util.List;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.Packet20NamedEntitySpawn;
import net.minecraft.server.Packet29DestroyEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;


public class ModMode extends JavaPlugin
{
    public static PermissionHandler permissionHandler;
    private SerializedPersistance persistance;

    private Set<String> invisible = new HashSet<String>();
    public Set<String> modmode  = new HashSet<String>();

    private final ModModeEntityListener entityListener = new ModModeEntityListener(this);
    private final ModModePlayerListener playerListener = new ModModePlayerListener(this);
    protected static final Logger log = Logger.getLogger("Minecraft");

    public boolean isPlayerInvisible(String name)
    {
        return invisible.contains(name);
    }

    public boolean isPlayerModMode(String name)
    {
        return modmode.contains(name);
    }

    @Override
    public void onDisable()
    {
        // get everyone out of mod mode to avoid issues
        Player[] onlinePlayers = this.getServer().getOnlinePlayers();
        for (Player p : onlinePlayers)
            if (isPlayerModMode(p.getDisplayName()))
                disableModMode(p);

        log.log(Level.INFO, "[" + getDescription().getName() + ")] " + getDescription().getVersion() + " disabled.");
    }

    @Override
    public void onEnable()
    {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.ENTITY_TARGET, entityListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.ENTITY_TRACK,  entityListener, Priority.Low,    this);
        pm.registerEvent(Event.Type.ENTITY_DAMAGE, entityListener, Priority.Normal, this);

        pm.registerEvent(Event.Type.PLAYER_JOIN,        playerListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_QUIT,        playerListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_RESPAWN,     playerListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_DROP_ITEM,   playerListener, Priority.Normal, this);

        log.log(Level.INFO, "[" + getDescription().getName() +"] " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage("Sorry, this plugin cannot be used from console");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vanish"))
        {
            if ((args.length == 1) && (args[0].equalsIgnoreCase("list")))
            {
                return vanishList((Player)sender);
            }

            return enableVanish((Player)sender);
        }

        if (command.getName().equalsIgnoreCase("unvanish"))
        {
            return disableVanish((Player)sender);
        }

        if (command.getName().equalsIgnoreCase("modmode"))
        {
            if (args.length == 1 && args[0].equalsIgnoreCase("on"))
                return enableModMode((Player)sender);
            if (args.length == 1 && args[0].equalsIgnoreCase("off"))
                return disableModMode((Player)sender);
        }

        return false;
    }

    public boolean disableModMode(Player player)
    {
        if (!Permissions.hasPermission(player, Permissions.MODMODE_TOGGLE))
            return false;

        if (!modmode.contains(player.getDisplayName()))
        {
            player.sendMessage(ChatColor.RED + "You are not in mod mode!");
            return false;
        }

        modmode.remove(player.getDisplayName());
        if (isPlayerInvisible(player.getName()))
            invisible.remove(player.getName());

        ((CraftPlayer)player).getHandle().name = player.getDisplayName();
        player.kickPlayer("You are no longer in mod mode!");

        return true;
    }

    public boolean enableModMode(Player player)
    {
        if (!Permissions.hasPermission(player, Permissions.MODMODE_TOGGLE))
            return false;

        if (modmode.contains(player.getDisplayName()))
        {
            player.sendMessage(ChatColor.RED + "You are already in mod mode!");
            return false;
        }

        modmode.add(player.getDisplayName());
        String newname = player.getDisplayName().length() > 11 ? player.getDisplayName().substring(0, 11) : player.getDisplayName();
        ((CraftPlayer)player).getHandle().name = ChatColor.GREEN + newname + ChatColor.WHITE;
        player.getInventory().clear();
        player.sendMessage(ChatColor.RED + "You are now in mod mode.");

        boolean unlimited = Permissions.hasPermission(player, Permissions.UNVANISH_UNLIMITED);
        if (!unlimited)
            invisible.add(player.getName());

        Player[] onlinePlayers = this.getServer().getOnlinePlayers();
        for (Player p : onlinePlayers)
        {
            if (p.getEntityId() == player.getEntityId())
                continue;

            if (unlimited || Permissions.hasPermission(p, Permissions.VANISH_SHOWHIDDEN))
            {
                ((CraftPlayer)p).getHandle().netServerHandler.sendPacket(new Packet29DestroyEntity(player.getEntityId()));
                ((CraftPlayer)p).getHandle().netServerHandler.sendPacket(new Packet20NamedEntitySpawn((EntityHuman) ((CraftPlayer)player).getHandle()));
            }
            else
            {
                ((CraftPlayer)p).getHandle().netServerHandler.sendPacket(new Packet29DestroyEntity(player.getEntityId()));
            }
        }

        return true;
    }

    public boolean disableVanish(Player player)
    {
        if (!Permissions.hasPermission(player, Permissions.UNVANISH))
            return false;

        if (!isPlayerInvisible(player.getName()))
        {
            player.sendMessage(ChatColor.RED + "You are not vanished!");
            return true;
        }

        invisible.remove(player.getName());

        Player[] onlinePlayers = this.getServer().getOnlinePlayers();
        for (Player p : onlinePlayers)
        {
            if (p.getEntityId() == player.getEntityId())
                continue;
            if (Permissions.hasPermission(p, Permissions.VANISH_SHOWHIDDEN))
                continue;

            List<Player> trackers = p.getTrackers();
            trackers.add(player);
            p.setTrackers(trackers);

            ((CraftPlayer)p).getHandle().netServerHandler.sendPacket(new Packet20NamedEntitySpawn((EntityHuman) ((CraftPlayer)player).getHandle()));
        }

        log.log(Level.INFO, player.getName() + " reappeared.");
        player.sendMessage(ChatColor.RED + "You have reappeared!");

        return true;
    }

    public boolean enableVanish(Player player)
    {
        if (!Permissions.hasPermission(player, Permissions.VANISH))
            return false;

        if (!isPlayerInvisible(player.getName()))
            invisible.add(player.getName());

        Player[] onlinePlayers = this.getServer().getOnlinePlayers();
        for (Player p : onlinePlayers)
        {
            if (p.getEntityId() == player.getEntityId())
                continue;
            if (Permissions.hasPermission(p, Permissions.VANISH_SHOWHIDDEN))
                continue;

            List<Player> trackers = p.getTrackers();
            trackers.remove(player);
            p.setTrackers(trackers);

            ((CraftPlayer)p).getHandle().netServerHandler.sendPacket(new Packet29DestroyEntity(player.getEntityId()));
        }

        log.log(Level.INFO, player.getName() + " disappeared.");
        player.sendMessage(ChatColor.RED + "Poof!");

        return true;
    }

    private boolean vanishList(Player player)
    {
        if (!Permissions.hasPermission(player, Permissions.VANISH_LIST))
            return false;

        if (invisible.isEmpty())
        {
            player.sendMessage(ChatColor.RED + "Everyone is visible");
            return true;
        }

        String message = "Invisible Players: ";
        int i = 0;
        for (String name : invisible)
        {
            message += name;
            i++;
            if (i != invisible.size())
            {
                message += " ";
            }
        }
        player.sendMessage(ChatColor.RED + message);

        return true;
    }

}
