package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// ------------------------------------------------------------------------
/**
 * Handles and exposes this plugin's configuration.
 */
class Configuration {

    /**
     * The configuration.
     */
    private FileConfiguration _config;

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    Configuration() {
        reload();
    }

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
     * Returns true if the given player logged out while vanished.
     *
     * @param player the player.
     * @return true if the given player logged out while vanished.
     */
    static synchronized boolean loggedOutVanished(Player player) {
        return LOGGED_OUT_VANISHED.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Sets the player's logged-out-vanished state to the given state.
     *
     * @param player the player.
     * @param state the new state.
     */
    static synchronized void setLoggedOutVanished(Player player, boolean state) {
        if (state) {
            LOGGED_OUT_VANISHED.add(player.getUniqueId());
        } else {
            LOGGED_OUT_VANISHED.remove(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Load the configuration.
     */
    synchronized void reload() {
        ModMode.PLUGIN.saveDefaultConfig();
        ModMode.PLUGIN.reloadConfig();
        _config = ModMode.PLUGIN.getConfig();

        // clear logged-out-vanished list and repopulate from config
        LOGGED_OUT_VANISHED.clear();
        _config.getStringList("logged-out-vanished").stream()
                                                    .map(UUID::fromString)
                                                    .forEach(LOGGED_OUT_VANISHED::add);

        // reload modmode cache
        ModMode.getModModeCache().load(_config);

        joinedVanished = new HashMap<>();
        allowFlight = _config.getBoolean("allow.flight", true);

        // update collisions for players in ModMode
        NerdBoardHook.setAllowCollisions(_config.getBoolean("allow.collisions", true));

        // load persistent groups
        PERSISTENT_GROUPS = new HashSet<>(_config.getStringList("permissions.persistent-groups"));

        // load permission worlds
        PERMISSION_WORLDS = _config.getStringList("permissions.worlds")
                                   .stream()
                                   .map(Bukkit::getWorld)
                                   .collect(Collectors.toCollection(HashSet::new));

        // store reference to the serialized groups configuration section
        SERIALIZED_GROUPS = _config.getConfigurationSection("serialized-groups");
        if (SERIALIZED_GROUPS == null) {
            SERIALIZED_GROUPS = _config.createSection("serialized-groups");
        }

        MODERATOR_GROUP = _config.getString("permissions.moderator-group", "moderators");
        MODMODE_GROUP = _config.getString("permissions.modmode-group", "ModMode");

        debugPlayerData = _config.getBoolean("debug.playerdata");

        beforeActivationCommands = _config.getStringList("commands.activate.before");
        afterActivationCommands = _config.getStringList("commands.activate.after");
        beforeDeactivationCommands = _config.getStringList("commands.deactivate.before");
        afterDeactivationCommands = _config.getStringList("commands.deactivate.after");
    }

    // ------------------------------------------------------------------------
    /**
     * Save the configuration.
     */
    synchronized void save() {
        _config.set("logged-out-vanished", new ArrayList<>(LOGGED_OUT_VANISHED));
        ModMode.getModModeCache().save(_config);
        _config.set("allow.flight", allowFlight);
        _config.set("allow.collisions", NerdBoardHook.allowsCollisions());
        _config.set("permissions.persistent-groups", new ArrayList<>(PERSISTENT_GROUPS));
        _config.set("permissions.worlds", PERMISSION_WORLDS.stream()
                                                           .map(World::getName)
                                                           .collect(Collectors.toList()));
        _config.set("permissions.modmode-group", MODMODE_GROUP);
        _config.set("permissions.moderator-group", MODERATOR_GROUP);
        ModMode.PLUGIN.saveConfig();
    }

    /**
     * For Moderators in ModMode, this is persistent storage for their vanish
     * state when they log out. Moderators out of ModMode are assumed to always
     * be visible.
     *
     * For Admins who can vanish without transitioning into ModMode, this
     * variable stores their vanish state between logins only when not in
     * ModMode. If they log out in ModMode, they are re-vanished automatically
     * when they log in. When they leave ModMode, their vanish state is set
     * according to membership in this set.
     *
     * This is NOT the set of currently vanished players, which is instead
     * maintained by the VanishNoPacket plugin.
     */
    private static Set<UUID> LOGGED_OUT_VANISHED = new HashSet<>();

    Map<String, String> joinedVanished;

    /**
     * If true, players in ModMode will be able to fly.
     */
    boolean allowFlight;

    /**
     * If true, player data loads and saves are logged to the console.
     */
    boolean debugPlayerData;

    /**
     * The name of the moderator permission group.
     */
    String MODERATOR_GROUP;

    /**
     * The name of the ModMode permission group.
     */
    String MODMODE_GROUP;

    /**
     * A set of groups which should be retained when entering ModMode.
     */
    private static HashSet<String> PERSISTENT_GROUPS = new HashSet<>();

    /**
     * A set of worlds with relevant permissions, i.e. when a player enters
     * ModMode, their permissions from these worlds will be serialized and
     * reapplied after they exit. Usually just "WORLD".
     */
    static HashSet<World> PERMISSION_WORLDS = new HashSet<>();

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
    List<String> beforeActivationCommands;

    /**
     * Commands executed immediately after ModMode is activated.
     */
    List<String> afterActivationCommands;

    /**
     * Commands executed immediately before ModMode is deactivated.
     */
    List<String> beforeDeactivationCommands;

    /**
     * Commands executed immediately after ModMode is deactivated.
     */
    List<String> afterDeactivationCommands;

}
