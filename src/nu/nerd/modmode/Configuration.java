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
    // ------------------------------------------------------------------------
    /**
     * Name of the track that Moderators are promoted along to enter ModMode.
     */
    public String MODMODE_TRACK_NAME;

    /**
     * Name of the track that local server admins and higher can be promoted along when entering AdminMode.
     */
    public String LOCAL_SERVER_ADMIN_MODMODE_TRACK_NAME;

    /**
     * Name of the track that foreign server admins are promoted along to enter
     * ModMode.
     */
    public String FOREIGN_SERVER_ADMIN_MODMODE_TRACK_NAME;

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
    public Set<UUID> LOGGED_OUT_VANISHED = new HashSet<>();

    /**
     * This is a map of player UUID to login announcement message for those
     * players that joined vanished. It is used to replay that login message
     * when the player unvanishes on leaving ModMode if that message was
     * initially suppressed when they logged in vanished.
     */
    public Map<UUID, String> SUPPRESSED_LOGIN_MESSAGE = new HashMap<>();

    /**
     * If true, players in ModMode will be able to fly.
     */
    public boolean ALLOW_FLIGHT;

    /**
     * If true, player data loads and saves are logged to the console.
     */
    public boolean DEBUG_PLAYER_DATA;

    /**
     * Commands executed immediately before ModMode is activated.
     */
    public List<String> MODMODE_BEFORE_ACTIVATION_COMMANDS;

    /**
     * Commands executed immediately after ModMode is activated.
     */
    public List<String> MODMODE_AFTER_ACTIVATION_COMMANDS;

    /**
     * Commands executed immediately before ModMode is deactivated.
     */
    public List<String> MODMODE_BEFORE_DEACTIVATION_COMMANDS;

    /**
     * Commands executed immediately after ModMode is deactivated.
     */
    public List<String> MODMODE_AFTER_DEACTIVATION_COMMANDS;

    /**
     * Commands executed immediately before AdminMode is activated.
     */
    public List<String> ADMINMODE_BEFORE_ACTIVATION_COMMANDS;

    /**
     * Commands executed immediately after AdminMode is activated.
     */
    public List<String> ADMINMODE_AFTER_ACTIVATION_COMMANDS;

    /**
     * Commands executed immediately before AdminMode is deactivated.
     */
    public List<String> ADMINMODE_BEFORE_DEACTIVATION_COMMANDS;

    /**
     * Commands executed immediately after AdminMode is deactivated.
     */
    public List<String> ADMINMODE_AFTER_DEACTIVATION_COMMANDS;

    /**
     * Delay in ticks to wait before setting the player's vanish state.
     */
    public int VANISH_DELAY;

    /**
     * A cache of players (UUIDs) currently in ModMode.
     */
    public PlayerUuidSet MODMODE_CACHE = new PlayerUuidSet("modmode");

    /**
     * A cache of players (UUIDs) currently in AdminMode.
     */
    public PlayerUuidSet ADMINMODE_CACHE = new PlayerUuidSet("adminmode");

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
        ADMINMODE_CACHE.load(_config);
        MODMODE_PENDING.load(_config);

        SUPPRESSED_LOGIN_MESSAGE.clear();
        ALLOW_FLIGHT = _config.getBoolean("allow.flight", true);

        MODMODE_TRACK_NAME = _config.getString("permissions.tracks.moderators", "modmode-track");
        LOCAL_SERVER_ADMIN_MODMODE_TRACK_NAME = _config.getString("permissions.tracks.local-server-admins",
                "local-server-admins-modmode-track");
        FOREIGN_SERVER_ADMIN_MODMODE_TRACK_NAME = _config.getString("permissions.tracks.foreign-server-admins",
                "foreign-server-admins-modmode-track");

        DEBUG_PLAYER_DATA = _config.getBoolean("debug.playerdata");

        MODMODE_BEFORE_ACTIVATION_COMMANDS = _config.getStringList("commands.modmode.activate.before");
        MODMODE_AFTER_ACTIVATION_COMMANDS = _config.getStringList("commands.modmode.activate.after");
        MODMODE_BEFORE_DEACTIVATION_COMMANDS = _config.getStringList("commands.modmode.deactivate.before");
        MODMODE_AFTER_DEACTIVATION_COMMANDS = _config.getStringList("commands.modmode.deactivate.after");

        ADMINMODE_BEFORE_ACTIVATION_COMMANDS = _config.getStringList("commands.adminmode.activate.before");
        ADMINMODE_AFTER_ACTIVATION_COMMANDS = _config.getStringList("commands.adminmode.activate.after");
        ADMINMODE_BEFORE_DEACTIVATION_COMMANDS = _config.getStringList("commands.adminmode.deactivate.before");
        ADMINMODE_AFTER_DEACTIVATION_COMMANDS = _config.getStringList("commands.adminmode.deactivate.after");
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
        _config.set("allow.flight", ALLOW_FLIGHT);
        ModMode.PLUGIN.saveConfig();
    }

    // ------------------------------------------------------------------------
    /**
     * The configuration.
     */
    private FileConfiguration _config;

}
