package nu.nerd.modmode;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

// ----------------------------------------------------------------------------

/**
 * Handles and exposes this plugin's configuration.
 */
class Configuration {

    private ModMode plugin;
    private TabAPI TABAPI;
    private HashMap<String, ModModeGroup> groupMap;
    private HashMap<UUID, ModModeGroup> playerGroupMap;

    private FileConfiguration _config;
    private File _configFile;
    private FileConfiguration _memberConfig;
    private File _memberConfigFile;
    private String SERVER_NAME;

    public Configuration(ModMode plugin, TabAPI TABAPI) {
        this.plugin = plugin;
        this.TABAPI = TABAPI;
        this.groupMap = plugin.getGroups();
        this.playerGroupMap = plugin.getPlayerGroupMap();
        plugin.logInfo("RELOADING PPGOME");
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        createMemberConfig();
        reload();
    }

    // ------------------------------------------------------------------------

    /**
     * Fetches the latest data from config.yml and members.yml and saves it to existing groups if in memory, makes
     * new ones if not.
     */
    public void reload() {
        // Fetch the data asynchronously.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HashMap<String, ModModeGroup> newGroupMap = new HashMap<>();
            HashMap<UUID, ModModeGroup> newPlayerGroupMap = new HashMap<>();
            plugin.reloadConfig();
            _config = plugin.getPLUGIN().getConfig();
            _configFile = new File(plugin.getDataFolder(), "config.yml");
            _memberConfig = YamlConfiguration.loadConfiguration(_memberConfigFile);

            boolean issue = false;
            SERVER_NAME = _config.getString("server");

            // Group builder
            for (String groupName : _config.getConfigurationSection("groups").getKeys(false)) {
                // commandMeta
                String description = _config.getString("groups." + groupName + ".commandMeta.description");
                String permission = _config.getString("groups." + groupName + ".commandMeta.permission");
                List<String> aliases = _config.getStringList("groups." + groupName + ".commandMeta.aliases");

                // actions
                List<String> activateBefore = _config.getStringList("groups." + groupName + ".actions.activate.before");
                List<String> activateAfter = _config.getStringList("groups." + groupName + ".actions.activate.after");
                List<String> deactivateBefore = _config.getStringList("groups." + groupName + ".actions.deactivate.before");
                List<String> deactivateAfter = _config.getStringList("groups." + groupName + ".actions.deactivate.after");

                // details
                String trackName = _config.getString("groups." + groupName + ".details.trackName");
                String prefix = _config.getString("groups." + groupName + ".details.prefix");
                boolean allowFlight = _config.getBoolean("groups." + groupName + ".details.allowFlight", false);
                boolean allowCollisions = _config.getBoolean("groups." + groupName + ".details.allowCollisions", true);
                boolean suppressJoinMessages = _config.getBoolean("groups." + groupName + ".details.suppressJoinMessages", false);
                boolean interactWithItems = _config.getBoolean("groups." + groupName + ".details.interactWithItems", false);

                if (!_memberConfig.contains("groups." + groupName)) {
                    _memberConfig.set("groups." + groupName + ".members", new ArrayList<String>());
                    saveMemberConfig();
                }

                // members
                List<UUID> members = getGroupMembers(groupName);

                if (description == null) issue = true;
                if (permission == null) issue = true;
                if (trackName == null) issue = true;
                if (prefix == null) issue = true;

                // If no issues arise, set up the hashmaps.
                if (!issue) {
                    ModModeGroup existingGroup = plugin.getGroups().get(groupName);
                    // If the group already exists (only happens from the reload command)...
                    if (existingGroup != null) {
                        plugin.logInfo("RELOADING EXISTING GROUP " + groupName);
                        existingGroup.updateGroup(activateBefore, activateAfter, deactivateBefore, deactivateAfter,
                                trackName, prefix, allowFlight, allowCollisions, suppressJoinMessages, interactWithItems);
                        newGroupMap.put(groupName, existingGroup);
                        for (UUID member : members) {
                            newPlayerGroupMap.put(member, existingGroup);
                        }
                    }
                    // If the group is new (always happens on server start)...
                    else {
                        plugin.logInfo("LOADING NEW GROUP " + groupName);
                        ModModeCommand newCommand = new ModModeCommand(groupName.toLowerCase(), description, permission,
                                aliases, interactWithItems, plugin);
                        ModModeGroup newGroup = new ModModeGroup(groupName, newCommand, activateBefore, activateAfter,
                                deactivateBefore, deactivateAfter, trackName, prefix, allowFlight, allowCollisions,
                                suppressJoinMessages, interactWithItems, members, plugin, TABAPI);
                        newGroupMap.put(groupName, newGroup);
                        for (UUID member : members) {
                            newPlayerGroupMap.put(member, newGroup);
                        }
                    }
                } else {
                    plugin.logError("There was an issue with group " + groupName + ". This group has not been loaded.");
                }
            }
            // Save the data on the main thread.
            Bukkit.getScheduler().runTask(plugin, () -> {
                groupMap.putAll(newGroupMap);
                playerGroupMap.putAll(newPlayerGroupMap);

                for(ModModeGroup group : newGroupMap.values()) {
                    Bukkit.getCommandMap().register(group.getName().toLowerCase(), group.getCommand());
                }
            });
        });
    }

    // ------------------------------------------------------------------------

    /**
     * Saves all groups present in the members.yml file.
     */
    public void save() {
        _config.set("server", SERVER_NAME);
        for (ModModeGroup group : plugin.getGroups().values()) {
            this.saveGroup(group);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Saves a specific group in the members.yml file.
     * @param group the group being saved.
     */
    public void saveGroup(ModModeGroup group) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String groupName = group.getName();
            try {

                // Members
                List<String> uuidStrings = new ArrayList<>();
                for (UUID uuid : group.getMembers()) {
                    uuidStrings.add(uuid.toString());
                }
                _memberConfig.set("groups." + groupName + ".members", uuidStrings);
                saveMemberConfig();
            } catch (Exception e) {
                plugin.logError("Failed to save group " + groupName + ": " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------------

    /**
     * Creates the members.yml file, should it not exist. If it does, load it into memory.
     */
    public void createMemberConfig() {
        _memberConfigFile = new File(plugin.getDataFolder(), "members.yml");

        if (!_memberConfigFile.exists()) {
            if (!_memberConfigFile.getParentFile().exists()) {
                _memberConfigFile.getParentFile().mkdirs();
            }
            try {
                _memberConfigFile.createNewFile();
            } catch (IOException exception) {
                plugin.logError("Failed to create members config file: " + exception.getMessage());
            }
        }

        _memberConfig = YamlConfiguration.loadConfiguration(_memberConfigFile);
    }

    // ------------------------------------------------------------------------

    /**
     * Saves the entire members.yml file.
     */
    private void saveMemberConfig() {
        try {
            _memberConfig.save(_memberConfigFile);
        } catch (IOException e) {
            plugin.logError("Failed to save members config file: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Adds a member to a specific group in the members.yml file.
     * @param groupName the name of the group being added to.
     * @param playerUUID the UUID of the player being added.
     */
    public void addMemberToGroup(String groupName, UUID playerUUID) {
        List<String> members = _memberConfig.getStringList("groups." + groupName + ".members");
        String uuidString = playerUUID.toString();

        if (!members.contains(uuidString)) {
            members.add(uuidString);
            _memberConfig.set("groups." + groupName + ".members", members);
            saveMemberConfig();
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Removes a member from a specific group in the members.yml file.
     * @param groupName the name of the group being removed from.
     * @param playerUUID the UUID of the player being removed.
     */
    public void removeMemberFromGroup(String groupName, UUID playerUUID) {
        List<String> members = _memberConfig.getStringList("groups." + groupName + ".members");
        String uuidString = playerUUID.toString();

        if (members.remove(uuidString)) {
            _memberConfig.set("groups." + groupName + ".members", members);
            saveMemberConfig();
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Fetches all members of a group in the members.yml file.
     * @param groupName the name of the group being fetched from.
     * @return a list of member UUIDs.
     */
    public List<UUID> getGroupMembers(String groupName) {
        List<String> memberStrings = _memberConfig.getStringList("groups." + groupName + ".members");
        List<UUID> members = new ArrayList<>();

        for (String memberString : memberStrings) {
            members.add(UUID.fromString(memberString));
        }

        return members;
    }

    // ------------------------------------------------------------------------

    /**
     * Return the File used to store the player's normal or ModMode state.
     *
     * @param player    the player.
     * @param isMode true if the data is for the ModMode state.
     * @return the File used to store the player's normal or ModMode state.
     */
    public File getStateFile(Player player, boolean isMode) {
        File playersDir = new File(plugin.getDataFolder(), "players");
        playersDir.mkdirs();

        String fileName = player.getUniqueId().toString() + ((isMode) ? "_mode" : "_normal") + ".yml";
        return new File(playersDir, fileName);
    }

    // ------------------------------------------------------------------------

    /**
     * Save the player's data to a YAML configuration file.
     *
     * @param player    the player.
     * @param isMode true if the saved data is for the ModMode inventory.
     */
    public void savePlayerData(Player player, boolean isMode) {
        File stateFile = getStateFile(player, isMode);

        // Keep 2 backups of saved player data.
        try {
            File backup1 = new File(stateFile.getPath() + ".1");
            File backup2 = new File(stateFile.getPath() + ".2");
            backup1.renameTo(backup2);
            stateFile.renameTo(backup1);
        } catch (Exception ex) {
            String msg = " raised saving state file backups for " + player.getName()
                    + " (" + player.getUniqueId().toString() + ").";
            plugin.logError(ex.getClass().getName() + msg);
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("health", player.getHealth());
        config.set("food", player.getFoodLevel());
        config.set("experience", player.getLevel() + player.getExp());
        config.set("fireticks", player.getFireTicks());
        config.set("falldistance", player.getFallDistance());
        config.set("awaketicks", player.getStatistic(Statistic.TIME_SINCE_REST));

        config.set("world", player.getLocation().getWorld().getName());
        config.set("x", player.getLocation().getX());
        config.set("y", player.getLocation().getY());
        config.set("z", player.getLocation().getZ());
        config.set("pitch", player.getLocation().getPitch());
        config.set("yaw", player.getLocation().getYaw());

        config.set("helmet", player.getInventory().getHelmet());
        config.set("chestplate", player.getInventory().getChestplate());
        config.set("leggings", player.getInventory().getLeggings());
        config.set("boots", player.getInventory().getBoots());
        config.set("off-hand", player.getInventory().getItemInOffHand());

        for (PotionEffect potion : player.getActivePotionEffects()) {
            config.set("potions." + potion.getType().getName(), potion);
        }
        ItemStack[] inventory = player.getInventory().getContents();
        for (int slot = 0; slot < inventory.length; ++slot) {
            config.set("inventory." + slot, inventory[slot]);
        }

        ItemStack[] enderChest = player.getEnderChest().getContents();
        for (int slot = 0; slot < enderChest.length; ++slot) {
            config.set("enderchest." + slot, enderChest[slot]);
        }

        try {
            config.save(stateFile);
        } catch (Exception exception) {
            plugin.logError("Failed to save player data for " + player.getName() + "(" + player.getUniqueId().toString() + ")");
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Load the player's data from a YAML configuration file.
     *
     * @param player    the player.
     * @param isMode true if the loaded data is for the ModMode inventory.
     */
    public void loadPlayerData(Player player, boolean isMode) {
        File stateFile = getStateFile(player, isMode);

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(stateFile);
            final double MAX_HEALTH = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            player.setHealth(Math.min(MAX_HEALTH, config.getDouble("health")));
            player.setFoodLevel(config.getInt("food"));
            float level = (float) config.getDouble("experience");
            player.setLevel((int) Math.floor(level));
            player.setExp(level - player.getLevel());
            player.setFireTicks(config.getInt("fireticks"));
            player.setFallDistance((float) config.getDouble("falldistance"));
            player.setStatistic(Statistic.TIME_SINCE_REST, config.getInt("awaketicks"));

            if (!isMode) {
                String world = config.getString("world");
                double x = config.getDouble("x");
                double y = config.getDouble("y");
                double z = config.getDouble("z");
                float pitch = (float) config.getDouble("pitch");
                float yaw = (float) config.getDouble("yaw");
                player.teleport(new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch));
            }

            player.getEnderChest().clear();
            player.getInventory().clear();
            player.getInventory().setHelmet(config.getItemStack("helmet"));
            player.getInventory().setChestplate(config.getItemStack("chestplate"));
            player.getInventory().setLeggings(config.getItemStack("leggings"));
            player.getInventory().setBoots(config.getItemStack("boots"));
            player.getInventory().setItemInOffHand(config.getItemStack("off-hand"));

            for (PotionEffect potion : player.getActivePotionEffects()) {
                player.removePotionEffect(potion.getType());
            }

            ConfigurationSection potions = config.getConfigurationSection("potions");
            if (potions != null) {
                for (String key : potions.getKeys(false)) {
                    PotionEffect potion = (PotionEffect) potions.get(key);
                    player.addPotionEffect(potion);
                }
            }

            ConfigurationSection inventory = config.getConfigurationSection("inventory");
            if (inventory != null) {
                for (String key : inventory.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack item = inventory.getItemStack(key);
                        player.getInventory().setItem(slot, item);
                    } catch (Exception ex) {
                        plugin.logError("Exception while loading " + player.getName() + "'s inventory: " + ex);
                    }
                }
            }

            ConfigurationSection enderChest = config.getConfigurationSection("enderchest");
            if (enderChest != null) {
                for (String key : enderChest.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack item = enderChest.getItemStack(key);
                        player.getEnderChest().setItem(slot, item);
                    } catch (Exception ex) {
                        plugin.logError("Exception while loading " + player.getName() + "'s ender chest: " + ex);
                    }
                }
            }
        } catch (Exception ex) {
            plugin.logError("Failed to load player data for " + player.getName() + "(" + player.getUniqueId().toString()
                    + "): " + ex.getMessage());
        }
    }

}