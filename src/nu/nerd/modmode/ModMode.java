package nu.nerd.modmode;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.util.CalculableType;
import de.diddiz.LogBlock.Consumer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import net.minecraft.server.v1_6_R2.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_6_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.kitteh.tag.TagAPI;
import org.kitteh.vanish.VanishPlugin;

public class ModMode extends JavaPlugin {

    private final ModModeListener listener = new ModModeListener(this);
    public List<String> vanished;
    public List<String> modmode;
    public boolean allowFlight;
    public boolean usingbperms;
    
    /**
     * The set of all possible permissions groups who can transition into 
     * ModMode, e.g. Moderators, PAdmins, SAdmins, etc.  These must be
     * mutually-exclusive; you can't be a Moderator and a PAdmin.
     */
    public List<String> bPermsModGroups;
    public String bPermsModModeGroup;
    
    /**
     * When entering ModMode, a staff member in any of bPermsModGroups (e.g. 
     * Moderators, PAdmins, etc.) will have that group removed and the "ModMode"
     * permission group added.  We use this section of the configuration file
     * as a map from player name to removed group name, so that the normal
     * group can be restored when they leave ModMode.
     */
    public ConfigurationSection groupMap;
    public HashMap<String, Collection<PotionEffect>> potionMap;
    private String worldname;
    private String playerDir;
    protected VanishPlugin vanish;
    protected TagAPI tagapi;
    

    public boolean isInvisible(Player player) {
        return vanish.getManager().isVanished(player);
    }

    public boolean isModMode(Player player) {
        return modmode.contains(player.getDisplayName());
    }

    public void enableVanish(Player player) {
        if (!vanish.getManager().isVanished(player)) {
            vanish.getManager().toggleVanish(player);
        }
    }

    public void disableVanish(Player player) {
        if (vanish.getManager().isVanished(player)) {
            vanish.getManager().toggleVanish(player);
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

        if (result.length() == 0) {
            player.sendMessage(ChatColor.RED + "All players are visible!");
        } else {
            player.sendMessage(ChatColor.RED + "Vanished players: " + result);
        }
    }
    
    public String getCleanModModeName(Player player) {
        return "modmode_" + player.getName();
    }
        
    public void savePlayerData(EntityPlayer entityhuman, String name) {
        try {
            NBTTagCompound nbttagcompound = new NBTTagCompound();
            entityhuman.e(nbttagcompound);
            
            File file1 = new File(playerDir, name + ".dat.tmp");
            File file2 = new File(playerDir, name + ".dat");

            NBTCompressedStreamTools.a(nbttagcompound, (OutputStream) (new FileOutputStream(file1)));
            if (file2.exists()) {
                file2.delete();
            }

            file1.renameTo(file2);
        } catch (Exception exception) {
            getLogger().warning("Failed to save player data for " + entityhuman.getName());
        }
    }
    
    public NBTTagCompound loadPlayerData(EntityPlayer entityhuman, String name) {
        WorldServer worldServer = entityhuman.server.getWorldServer(0);
        WorldNBTStorage playerFileData = (WorldNBTStorage) worldServer.getDataManager().getPlayerFileData();
        NBTTagCompound nbttagcompound = playerFileData.getPlayerData(name);
        if (nbttagcompound != null) {
            entityhuman.f(nbttagcompound);
        }
        
        return nbttagcompound;
    }

    public void toggleModMode(final Player player, boolean enabled, boolean onJoin) {
        String old_name = player.getName();
        String new_name = getCleanModModeName(player);
        player.setMetadata("modmode", new FixedMetadataValue(this, enabled));
        if (!enabled) {
            if (usingbperms) {
                List<org.bukkit.World> worlds = getServer().getWorlds();
                for (org.bukkit.World world : worlds) {
                    ApiLayer.removeGroup(world.getName(), CalculableType.USER, player.getName(), bPermsModModeGroup);
                    
                    // Players leaving ModMode are assumed to be mods if not otherwise specified.
                    String group = groupMap.getString(player.getName(), bPermsModGroups.get(0));
                    if (! ApiLayer.hasGroupRecursive(world.getName(), CalculableType.USER, player.getName(), group)) {
                        ApiLayer.addGroup(world.getName(), CalculableType.USER, player.getName(), group);
                    }
                }
            }
            player.sendMessage(ChatColor.RED + "You are no longer in ModMode!");
        } else {
            old_name = new_name;
            new_name = player.getName();
            if (usingbperms) {
                List<org.bukkit.World> worlds = getServer().getWorlds();
                for (org.bukkit.World world : worlds) {
                    ApiLayer.addGroup(world.getName(), CalculableType.USER, player.getName(), bPermsModModeGroup);
                    
                    for (String group : bPermsModGroups) {
                        if (! ApiLayer.hasGroupRecursive(world.getName(), CalculableType.USER, player.getName(), group)) {
                            groupMap.set(player.getName(), group);
                            ApiLayer.removeGroup(world.getName(), CalculableType.USER, player.getName(), group);
                        }
                        break;
                    }
                }
            }
            player.sendMessage(ChatColor.RED + "You are now in ModMode!");
        }

        Location loc = player.getLocation();
        final EntityPlayer entityplayer = ((CraftPlayer) player).getHandle();
        final MinecraftServer server = entityplayer.server;

        // Save current potion effects
        Collection<PotionEffect> activeEffects = player.getActivePotionEffects();
        potionMap.put(entityplayer.listName, activeEffects);

        //save with the old name, change it, then load with the new name
        savePlayerData(entityplayer, old_name);
        loadPlayerData(entityplayer, new_name);

        //teleport to avoid speedhack
        if (!enabled || onJoin) {
            loc = new Location(entityplayer.world.getWorld(), entityplayer.locX, entityplayer.locY, entityplayer.locZ, entityplayer.yaw, entityplayer.pitch);
        }
        player.teleport(loc);
        // Hopefully stop some minor falls
        player.setFallDistance(0F);
        // Chunk error ( resend to all clients )
        World w = player.getWorld();
        Chunk c = w.getChunkAt(player.getLocation());
        w.refreshChunk(c.getX(), c.getZ());


        //unvanish the player when they leave modmode
        if (!enabled) {
            disableVanish(player);
        }


        // Load new potion effects
        for (PotionEffect effect : activeEffects){
            player.removePotionEffect(effect.getType());
        }
        Collection<PotionEffect> newEffects = potionMap.get(entityplayer.listName);
        if (newEffects != null) {
            for (PotionEffect effect : newEffects){
                player.addPotionEffect(effect);
                // addPotionEffect doesn't send this packet for some reason, so we'll do it manually
                entityplayer.playerConnection.sendPacket(new Packet41MobEffect(entityplayer.id, new MobEffect(effect.getType().getId(), effect.getDuration(), effect.getAmplifier())));
            }
        }
        potionMap.remove(entityplayer.listName);


        //toggle flight, set via the config path "allow.flight"
        if (allowFlight) {
            player.setAllowFlight(enabled);
        }
    }

    public void updateVanishLists(Player player) {
        vanish.getManager().resetSeeing(player);
    }

    @Override
    public void onEnable() {
        vanish = (VanishPlugin)getServer().getPluginManager().getPlugin("VanishNoPacket");
        if (vanish == null) {
            getLogger().severe("VanishNoPacket required. Download it here http://dev.bukkit.org/server-mods/vanish/");
            getPluginLoader().disablePlugin(this);
            return;
        }
        
        Plugin plugin = getServer().getPluginManager().getPlugin("LogBlock");
        if (plugin != null && !plugin.isEnabled()) {
            getPluginLoader().enablePlugin(plugin);
        }
        
        if (!getServer().getPluginManager().isPluginEnabled("TagAPI")) {
            getLogger().info("TagAPI is required for coloured names while in ModMode.");
            getLogger().info("http://dev.bukkit.org/server-mods/tag/ ");
        }
        
        getServer().getPluginManager().registerEvents(listener, this);
        vanished = getConfig().getStringList("vanished");
        modmode = getConfig().getStringList("modmode");
        allowFlight = getConfig().getBoolean("allow.flight", true);
        usingbperms = getConfig().getBoolean("bperms.enabled", false);
        bPermsModGroups = getConfig().getStringList("bperms.modgroup");
        
        if (bPermsModGroups.isEmpty()) {
            bPermsModGroups.add("Moderators");
        }
        
        groupMap = getConfig().getConfigurationSection("groupmap");
        if (groupMap == null) {
            getConfig().createSection("groupmap");
        }
        
        bPermsModModeGroup = getConfig().getString("bperms.modmodegroup", "ModMode");
        worldname = getConfig().getString("worldname", "world");
        File worldDir = new File(Bukkit.getServer().getWorldContainer(), worldname);
        playerDir = new File(worldDir, "players").getAbsolutePath();
        
        potionMap = new HashMap<String, Collection<PotionEffect>>();
        
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
        getConfig().set("modmode", modmode);
        getConfig().set("allow.flight", allowFlight);
        getConfig().set("bperms.enabled", usingbperms);
        getConfig().set("bperms.modgroup", bPermsModGroups);
        getConfig().set("bperms.modmodegroup", bPermsModModeGroup);
        getConfig().set("worldname", worldname);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("vanishlist")) {
            showVanishList(player);
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
