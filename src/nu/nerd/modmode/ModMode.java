package nu.nerd.modmode;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.util.CalculableType;
import java.util.List;
import java.util.logging.Level;
import net.minecraft.server.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ModMode extends JavaPlugin {

    private final ModModeListener listener = new ModModeListener(this);
    public List<String> vanished;
    public List<String> fullvanished;
    public List<String> modmode;
    public boolean allowFlight;
    public boolean usingbperms;
    public String bPermsModGroup;
    public String bPermsModModeGroup;

    public boolean isInvisible(Player player) {
        return vanished.contains(player.getName()) || fullvanished.contains(player.getName());
    }

    public boolean isModMode(Player player) {
        return modmode.contains(player.getDisplayName());
    }

    public void enableVanish(Player player) {
        for (Player other : getServer().getOnlinePlayers()) {
            if (Permissions.hasPermission(other, Permissions.SHOWVANISHED)) {
                continue;
            }
            if (Permissions.hasPermission(other, Permissions.SHOWMODS)) {
                continue;
            }
            other.hidePlayer(player);
        }

        player.sendMessage(ChatColor.RED + "Poof!");
    }

    public void enableFullVanish(Player player) {
        for (Player other : getServer().getOnlinePlayers()) {
            if (Permissions.hasPermission(other, Permissions.SHOWVANISHED)) {
                continue;
            }
            other.hidePlayer(player);
        }

        player.sendMessage(ChatColor.RED + "You are fully vanished!");
    }

    public void disableVanish(Player player) {
        if (vanished.remove(player.getName()) || fullvanished.remove(player.getName())) {
            for (Player other : getServer().getOnlinePlayers()) {
                other.showPlayer(player);
            }

            player.sendMessage(ChatColor.RED + "You have reappeared!");
        } else {
            player.sendMessage(ChatColor.RED + "You are not vanished!");
        }
    }

    public void showVanishList(Player player) {
        String result = "";
        boolean first = true;
        for (String hidden : vanished) {
            if (getServer().getPlayerExact(hidden) == null) {
                continue;
            }

            if (first) {
                result += hidden + ChatColor.RED;
                first = false;
                continue;
            }

            result += ", " + hidden + ChatColor.RED;
        }

        for (String hidden : fullvanished) {
            if (getServer().getPlayerExact(hidden) == null) {
                continue;
            }

            if (first) {
                result += hidden + ChatColor.RED;
                first = false;
                continue;
            }

            result += ", " + hidden + ChatColor.RED;
        }

        if (result.length() == 0) {
            player.sendMessage(ChatColor.RED + "All players are visible!");
        } else {
            player.sendMessage(ChatColor.RED + "Vanished players: " + result);
        }
    }

    public void toggleModMode(Player player, boolean toggle, boolean onJoin) {
        String displayName = player.getName();
        String name = ChatColor.GREEN + player.getDisplayName() + ChatColor.WHITE;
        if (!toggle) {
            displayName = player.getDisplayName();
            name = displayName;
            if (usingbperms) {
                List<org.bukkit.World> worlds = getServer().getWorlds();
                for (org.bukkit.World world : worlds) {
                    ApiLayer.addGroup(world.getName(), CalculableType.USER, name, bPermsModModeGroup);
                }
            }
            player.sendMessage(ChatColor.RED + "You are no longer in ModMode!");
        } else {
            if (usingbperms) {
                List<org.bukkit.World> worlds = getServer().getWorlds();
                for (org.bukkit.World world : worlds) {
                    ApiLayer.removeGroup(world.getName(), CalculableType.USER, name, bPermsModModeGroup);
                }
            }
            player.sendMessage(ChatColor.RED + "You are now in ModMode!");
        }

        Location loc = player.getLocation();
        EntityPlayer entityplayer = ((CraftPlayer) player).getHandle();
        MinecraftServer server = entityplayer.server;

        //send fake quit message
        if (!onJoin) {
            PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(player, "\u00A7e" + entityplayer.name + " left the game.");
            getServer().getPluginManager().callEvent(playerQuitEvent);
            if ((playerQuitEvent.getQuitMessage() != null) && (playerQuitEvent.getQuitMessage().length() > 0)) {
                server.getServerConfigurationManager().sendAll(new Packet3Chat(playerQuitEvent.getQuitMessage()));
            }
        }

        //save with the old name, change it, then load with the new name
        server.getServerConfigurationManager().playerFileData.save(entityplayer);
        entityplayer.name = name;
        entityplayer.displayName = displayName;
        server.getServerConfigurationManager().playerFileData.load(entityplayer);

        //send fake join message
        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(player, "\u00A7e" + entityplayer.name + " joined the game.");
        getServer().getPluginManager().callEvent(playerJoinEvent);
        if ((playerJoinEvent.getJoinMessage() != null) && (playerJoinEvent.getJoinMessage().length() > 0)) {
            server.getServerConfigurationManager().sendAll(new Packet3Chat(playerJoinEvent.getJoinMessage()));
        }

        //untrack and track to show new name to clients
        ((WorldServer) entityplayer.world).tracker.untrackEntity(entityplayer);
        ((WorldServer) entityplayer.world).tracker.track(entityplayer);

        //teleport to avoid speedhack
        if (!toggle || onJoin) {
            loc = new Location(entityplayer.world.getWorld(), entityplayer.locX, entityplayer.locY, entityplayer.locZ, entityplayer.yaw, entityplayer.pitch);
        }
        player.teleport(loc);

        //unvanish the player when they leave modmode
        if (!toggle) {
            for (Player other : getServer().getOnlinePlayers()) {
                other.showPlayer(player);
            }
        }

        //toggle flight, set via the config path "allow.flight"
        if (allowFlight) {
            player.setAllowFlight(toggle);
        }

        /*
         * EntityPlayer oldplayer = ((CraftPlayer) player).getHandle();
         * MinecraftServer server = oldplayer.server; NetServerHandler
         * netServerHandler = oldplayer.netServerHandler;
         *
         * // remove old entity String quitMessage =
         * server.serverConfigurationManager.disconnect(oldplayer); if
         * ((quitMessage != null) && (quitMessage.length() > 0)) {
         * server.serverConfigurationManager.sendAll(new
         * Packet3Chat(quitMessage)); }
         *
         * // ((WorldServer) oldplayer.world).tracker.untrackPlayer(oldplayer);
         * // oldplayer.die();
         *
         * // make new one with same NetServerHandler and ItemInWorldManager
         * EntityPlayer entityplayer = new EntityPlayer(server,
         * server.getWorldServer(0), name, oldplayer.itemInWorldManager); Player
         * newplayer = entityplayer.getBukkitEntity();
         *
         * entityplayer.displayName = displayName; entityplayer.listName =
         * displayName; entityplayer.netServerHandler = netServerHandler;
         * entityplayer.netServerHandler.player = entityplayer; entityplayer.id
         * = oldplayer.id;
         * server.serverConfigurationManager.playerFileData.load(entityplayer);
         * if (toggle) { entityplayer.locX = oldplayer.locX; entityplayer.locY =
         * oldplayer.locY; entityplayer.locZ = oldplayer.locZ; entityplayer.yaw
         * = oldplayer.yaw; entityplayer.pitch = oldplayer.pitch; }
         * server.serverConfigurationManager.c(entityplayer);
         * entityplayer.syncInventory();
         *
         * // untrack and track to make sure we can see everyone ((WorldServer)
         * entityplayer.world).tracker.untrackEntity(entityplayer);
         * ((WorldServer) entityplayer.world).tracker.track(entityplayer);
         *
         * // teleport to the player's location to avoid speedhack kick if
         * (!toggle) { Location loc = new
         * Location(entityplayer.world.getWorld(), entityplayer.locX,
         * entityplayer.locY, entityplayer.locZ, entityplayer.yaw,
         * entityplayer.pitch); newplayer.teleport(loc);
        }
         */
    }

    public void updateVanishLists(Player player) {
        //first show everyone, then decide who to hide
        for (Player other : getServer().getOnlinePlayers()) {
            player.showPlayer(other);
        }

        if (Permissions.hasPermission(player, Permissions.SHOWVANISHED)) {
            return;
        }

        for (String hidden : fullvanished) {
            Player hiddenPlayer = getServer().getPlayerExact(hidden);
            if (hiddenPlayer != null) {
                player.hidePlayer(hiddenPlayer);
            }
        }

        if (Permissions.hasPermission(player, Permissions.SHOWMODS)) {
            return;
        }

        for (String hidden : vanished) {
            Player hiddenPlayer = getServer().getPlayerExact(hidden);
            if (hiddenPlayer != null) {
                player.hidePlayer(hiddenPlayer);
            }
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(listener, this);
        getConfig().options().copyDefaults(true);
        vanished = getConfig().getStringList("vanished");
        fullvanished = getConfig().getStringList("fullvanished");
        modmode = getConfig().getStringList("modmode");
        allowFlight = getConfig().getBoolean("allow.flight", true);
        usingbperms = getConfig().getBoolean("bperms.enable", false);
        bPermsModGroup = getConfig().getString("bperms.modgroup", "Moderators");
        bPermsModModeGroup = getConfig().getString("bperms.modmodegroup", "ModMode");
        
        if (usingbperms) {
            de.bananaco.bpermissions.imp.Permissions bPermsPlugin = null;
            
            bPermsPlugin = (de.bananaco.bpermissions.imp.Permissions)getServer().getPluginManager().getPlugin("bPermissions");
            if (bPermsPlugin == null || !(bPermsPlugin instanceof de.bananaco.bpermissions.imp.Permissions)) {
                if (!bPermsPlugin.isEnabled()) {
                    getPluginLoader().enablePlugin(bPermsPlugin);
                }
                getLogger().log(Level.INFO, "bperms turned on, but plugin could not be loaded.");
                getPluginLoader().disablePlugin(this);
            }
        }
    }

    @Override
    public void onDisable() {
        getConfig().set("vanished", vanished);
        getConfig().set("fullvanished", fullvanished);
        getConfig().set("modmode", modmode);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("vanish")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                showVanishList(player);
            } else {
                if (vanished.contains(player.getName())) {
                    player.sendMessage(ChatColor.RED + "You are already vanished!");
                } else {
                    // special case, we need to appear to mods but not everyone
                    if (fullvanished.remove(player.getName())) {
                        for (Player other : getServer().getOnlinePlayers()) {
                            if (Permissions.hasPermission(other, Permissions.SHOWMODS)) {
                                other.showPlayer(player);
                            }
                        }
                    }
                    vanished.add(player.getName());
                    enableVanish(player);
                }
            }
        } else if (command.getName().equalsIgnoreCase("fullvanish")) {
            if (fullvanished.contains(player.getName())) {
                player.sendMessage(ChatColor.RED + "You are already vanished!");
            } else {
                fullvanished.add(player.getName());
                vanished.remove(player.getName());
                enableFullVanish(player);
            }
        } else if (command.getName().equalsIgnoreCase("unvanish")) {
            disableVanish(player);
        } else if (command.getName().equalsIgnoreCase("modmode")) {
            if (modmode.remove(player.getDisplayName())) {
                toggleModMode(player, false, false);
            } else {
                modmode.add(player.getDisplayName());
                toggleModMode(player, true, false);
            }
        }

        return true;
    }
}
