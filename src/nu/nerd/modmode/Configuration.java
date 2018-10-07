package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

        modmode = new HashSet<>(_config.getStringList("modmode"));
        joinedVanished = new HashMap<>();
        allowFlight = _config.getBoolean("allow.flight", true);

        NerdBoardHook.setAllowCollisions(_config.getBoolean("allow.collisions", true));

        bPermsKeepGroups = new HashSet<>(_config.getStringList("bperms.keepgroups"));
        bPermsWorlds = new HashSet<>(_config.getStringList("bperms.worlds"));

        PERMISSION_WORLDS.clear();
        _config.getStringList("bperms.worlds").stream()
                                              .map(Bukkit::getWorld)
                                              .forEach(PERMISSION_WORLDS::add);

        if (bPermsWorlds.isEmpty()) {
            bPermsWorlds.add("world");
        }

        groupMap = _config.getConfigurationSection("groupmap");
        if (groupMap == null) {
            _config.createSection("groupmap");
        }

        bPermsModGroup = _config.getString("bperms.modgroup", "moderators");
        bPermsModModeGroup = _config.getString("bperms.modmodegroup", "ModMode");
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
        _config.set("modmode", modmode.toArray());
        _config.set("allow.flight", allowFlight);
        _config.set("allow.collisions", NerdBoardHook.allowsCollisions());
        _config.set("bperms.keepgroups", bPermsKeepGroups.toArray());
        _config.set("bperms.worlds", bPermsWorlds.toArray());
        _config.set("bperms.modmodegroup", bPermsModModeGroup);
        _config.set("bperms.modgroup", bPermsModGroup);
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
    private static final Set<UUID> LOGGED_OUT_VANISHED = new HashSet<>();


    Set<String> modmode;

    Map<String, String> joinedVanished;
    boolean allowFlight;

    /**
     * If true, player data loads and saves are logged to the console.
     */
    boolean debugPlayerData;

    /**
     * The set of all possible permissions groups who can transition into
     * ModMode, e.g. Moderators, PAdmins, SAdmins, etc. These must be
     * mutually-exclusive; you can't be a Moderator and a PAdmin.
     */
    String bPermsModGroup;
    String bPermsModModeGroup;
    Set<String> bPermsKeepGroups;
    Set<String> bPermsWorlds;

    static final HashSet<World> PERMISSION_WORLDS = new HashSet<>();

    /**
     * When entering ModMode, a staff member in any of bPermsModGroups (e.g.
     * Moderators, PAdmins, etc.) will have that group removed and the "ModMode"
     * permission group added. We use this section of the configuration file as
     * a map from player name to removed group name, so that the normal group
     * can be restored when they leave ModMode.
     */
    ConfigurationSection groupMap;

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
