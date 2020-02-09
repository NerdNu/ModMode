package nu.nerd.modmode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

// ----------------------------------------------------------------------------
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
     * Returns true if the given player logged out while vanished.
     *
     * @param player the player.
     * @return true if the given player logged out while vanished.
     */
    boolean loggedOutVanished(Player player) {
        return LOGGED_OUT_VANISHED.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Sets the player's logged-out-vanished state to the given state.
     *
     * @param player the player.
     * @param state the new state.
     */
    void setLoggedOutVanished(Player player, boolean state) {
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

        VANISH_DELAY = this._config.getInt("vanish-delay-ticks", 1);
        if (VANISH_DELAY <= 0) {
            ModMode.log("Configuration error: vanish-delay-ticks must be a positive integer");
            VANISH_DELAY = 1;
        }

        // clear logged-out-vanished list and repopulate from config
        LOGGED_OUT_VANISHED.clear();
        _config.getStringList("logged-out-vanished").stream()
        .map(UUID::fromString)
        .forEach(LOGGED_OUT_VANISHED::add);

        MODMODE_CACHE.load(_config);
        MODMODE_PENDING.load(_config);

        joinedVanished = new HashMap<>();
        allowFlight = _config.getBoolean("allow.flight", true);

        // update collisions for players in ModMode
        NerdBoardHook.setAllowCollisions(_config.getBoolean("allow.collisions", true));

        MODMODE_TRACK_NAME = _config.getString("permissions.tracks.moderators", "modmode-track");
        FOREIGN_SERVER_ADMIN_MODMODE_TRACK_NAME = _config.getString("permissions.tracks.foreign-server-admins",
                                                                    "foreign-server-admins-modmode-track");

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
        _config.set("logged-out-vanished", new ArrayList<>(LOGGED_OUT_VANISHED.stream()
        .map(UUID::toString)
        .collect(Collectors.toList())));
        MODMODE_CACHE.save(_config);
        MODMODE_PENDING.save(_config);
        _config.set("allow.flight", allowFlight);
        _config.set("allow.collisions", NerdBoardHook.allowsCollisions());
        ModMode.PLUGIN.saveConfig();
    }

    // ------------------------------------------------------------------------
    /**
     * Name of the track that Moderators are promoted along to enter ModMode.
     */
    String MODMODE_TRACK_NAME;

    /**
     * Name of the track that foreign server admins are promoted along to enter
     * ModMode.
     */
    String FOREIGN_SERVER_ADMIN_MODMODE_TRACK_NAME;

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
    Set<UUID> LOGGED_OUT_VANISHED = new HashSet<>();

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

    /**
     * Delay in ticks to wait before setting the player's vanish state.
     */
    int VANISH_DELAY;

    /**
     * A cache of players (UUIDs) currently in ModMode.
     */
    PlayerUuidSet MODMODE_CACHE = new PlayerUuidSet("modmode");

    /**
     * A cache of players (UUIDs) currently in ModMode.
     */
    PlayerUuidSet MODMODE_PENDING = new PlayerUuidSet("modmode-pending");

}
