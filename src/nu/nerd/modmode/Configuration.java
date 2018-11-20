package nu.nerd.modmode;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

// ------------------------------------------------------------------------
/**
 * Handles and exposes this plugin's configuration.
 */
class Configuration {

    /**
     * The configuration.
     */
    private static FileConfiguration _config;

    private Configuration() { }

    // ------------------------------------------------------------------------
    /**
     * Returns true if the given groups is configured to be persistent.
     *
     * @param group the group.
     * @return true if the given groups is configured to be persistent.
     */
    static boolean isPersistentGroup(String group) {
        return PERSISTENT_GROUPS.contains(group);
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the given player's serialized groups. If none exist, an empty
     * List will be returned.
     *
     * @param player the player.
     * @return the player's serialized groups; if none, an empty list.
     */
    static List<String> getSerializedGroups(Player player) {
        String playerUuid = player.getUniqueId().toString();
        return new ArrayList<>(SERIALIZED_GROUPS.getStringList(playerUuid));
    }

    // ------------------------------------------------------------------------
    /**
     * Serializes the given groups for the given player.
     *
     * @param player the player.
     * @param groups the groups to serialize.
     */
    static void serializeGroups(Player player, Collection<String> groups) {
        SERIALIZED_GROUPS.set(player.getUniqueId().toString(), new ArrayList<>(groups));
    }

    // ------------------------------------------------------------------------
    /**
     * Sanitizes the configuration file by removing artifacts from the player's
     * last ModMode toggle.
     *
     * @param player the player.
     */
    static void sanitizeSerializedGroups(Player player) {
        SERIALIZED_GROUPS.set(player.getUniqueId().toString(), null);
    }

    // ------------------------------------------------------------------------
    /**
     * Load the configuration.
     */
    synchronized static void reload() {
        ModMode.PLUGIN.saveDefaultConfig();
        ModMode.PLUGIN.reloadConfig();
        _config = ModMode.PLUGIN.getConfig();

        // reload modmode cache
        ModMode.getModModeCache().load(_config);

        // load persistent groups
        PERSISTENT_GROUPS = new HashSet<>(_config.getStringList("permissions.persistent-groups"));

        // store reference to the serialized groups configuration section
        SERIALIZED_GROUPS = _config.getConfigurationSection("serialized-groups");
        if (SERIALIZED_GROUPS == null) {
            SERIALIZED_GROUPS = _config.createSection("serialized-groups");
        }

        MODERATOR_GROUP = _config.getString("permissions.moderator-group", "moderators");
        MODMODE_GROUP = _config.getString("permissions.modmode-group", "ModMode");

        DEBUG_PLAYER_DATA = _config.getBoolean("debug.playerdata");

        BEFORE_ACTIVATION_COMMANDS.clear();
        BEFORE_ACTIVATION_COMMANDS.addAll(_config.getStringList("commands.activate.before"));

        AFTER_ACTIVATION_COMMANDS.clear();
        AFTER_ACTIVATION_COMMANDS.addAll(_config.getStringList("commands.activate.after"));

        BEFORE_DEACTIVATION_COMMANDS.clear();
        BEFORE_DEACTIVATION_COMMANDS.addAll(_config.getStringList("commands.deactivate.before"));

        AFTER_DEACTIVATION_COMMANDS.clear();
        AFTER_DEACTIVATION_COMMANDS.addAll(_config.getStringList("commands.deactivate.after"));
    }

    // ------------------------------------------------------------------------
    /**
     * Save the configuration.
     */
    synchronized static void save() {
        ModMode.getModModeCache().save(_config);
        _config.set("permissions.persistent-groups", new ArrayList<>(PERSISTENT_GROUPS));
        _config.set("permissions.modmode-group", MODMODE_GROUP);
        _config.set("permissions.moderator-group", MODERATOR_GROUP);
        ModMode.PLUGIN.saveConfig();
    }

    /**
     * If true, player data loads and saves are logged to the console.
     */
    static boolean DEBUG_PLAYER_DATA;

    /**
     * The name of the moderator permission group.
     */
    static String MODERATOR_GROUP;

    /**
     * The name of the ModMode permission group.
     */
    static String MODMODE_GROUP;

    /**
     * A set of groups which should be retained when entering ModMode.
     */
    private static HashSet<String> PERSISTENT_GROUPS = new HashSet<>();

    /**
     * When a player enters ModMode, their current permission groups will be
     * serialized into the mapping
     *
     *      Player -> {group_1, group_2, ..., group_n},
     *
     * of which all values (groups) will be re-applied after said player exits
     * ModMode.
     */
    private static ConfigurationSection SERIALIZED_GROUPS;

    /**
     * Commands executed immediately before ModMode is activated.
     */
    static final HashSet<String> BEFORE_ACTIVATION_COMMANDS = new HashSet<>();

    /**
     * Commands executed immediately after ModMode is activated.
     */
    static final HashSet<String> AFTER_ACTIVATION_COMMANDS = new HashSet<>();

    /**
     * Commands executed immediately before ModMode is deactivated.
     */
    static final HashSet<String> BEFORE_DEACTIVATION_COMMANDS = new HashSet<>();

    /**
     * Commands executed immediately after ModMode is deactivated.
     */
    static final HashSet<String> AFTER_DEACTIVATION_COMMANDS = new HashSet<>();

}
