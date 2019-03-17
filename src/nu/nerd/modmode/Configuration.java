package nu.nerd.modmode;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
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

    /**
     * If true, player data loads and saves are logged to the console.
     */
    public boolean DEBUG;

    /**
     * If true, players in ModMode will be able to fly.
     */
    public boolean ALLOW_FLIGHT;

    public boolean ALLOW_COLLISIONS;

    private final HashMap<UUID, String> SUPPRESSED_JOIN_MESSAGES = new HashMap<>();

    private final HashMap<Integer, ItemStack> MOD_KIT = new HashMap<>();

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
    private final HashSet<UUID> LOGGED_OUT_VANISHED = new HashSet<>();

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    public Configuration() {
        reload();
        for (String uuidString : _config.getStringList("modmode-cache")) {
            UUID uuid = asUUID(uuidString);
            if (uuid != null) {
                ModMode.MODMODE.add(uuid);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Load the configuration.
     */
    void reload() {
        ModMode.PLUGIN.saveDefaultConfig();
        ModMode.PLUGIN.reloadConfig();
        _config = ModMode.PLUGIN.getConfig();

        DEBUG = _config.getBoolean("debug.playerdata");
        ALLOW_FLIGHT = _config.getBoolean("allow.flight", true);
        ALLOW_COLLISIONS = _config.getBoolean("allow.collisions", true);

        LOGGED_OUT_VANISHED.clear();
        _config.getStringList("logged-out-vanished").stream()
            .map(Configuration::asUUID)
            .filter(Objects::nonNull)
            .forEach(LOGGED_OUT_VANISHED::add);

        MOD_KIT.clear();
        ConfigurationSection modKit = _config.getConfigurationSection("mod-kit");
        if (modKit != null) {
            for (String key : modKit.getKeys(false)) {
                try {
                    Integer i = Integer.valueOf(key);
                    ItemStack item = _config.getItemStack("mod-kit." + i, null);
                    MOD_KIT.put(i, item);
                } catch (Exception e) {
                    ModMode.log("Bad entry in mod-kit in config.yml: " + key);
                }
            }
        }

        BEFORE_ACTIVATION_COMMANDS = getCommandList("commands.activate.before");
        AFTER_ACTIVATION_COMMANDS = getCommandList("commands.activate.after");
        BEFORE_DEACTIVATION_COMMANDS = getCommandList("commands.deactivate.before");
        AFTER_DEACTIVATION_COMMANDS = getCommandList("commands.deactivate.after");
    }

    // ------------------------------------------------------------------------
    /**
     * Save the configuration.
     */
    void save() {
        _config.set("logged-out-vanished", new ArrayList<>(
            LOGGED_OUT_VANISHED.stream().map(UUID::toString).collect(Collectors.toList()))
        );
        _config.set("modmode-cache", new ArrayList<>(
            ModMode.MODMODE.stream().map(UUID::toString).collect(Collectors.toList())
        ));
        for (Integer i : MOD_KIT.keySet()) {
            _config.set("mod-kit." + i, MOD_KIT.get(i));
        }
        ModMode.PLUGIN.saveConfig();
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the current mod kit as a map from inventory index to ItemStack.
     *
     * @return the current mod kit.
     */
    public HashMap<Integer, ItemStack> getModKit() {
        return new HashMap<>(MOD_KIT);
    }

    void saveModKit(PlayerInventory inventory) {
        MOD_KIT.clear();
        // slots 0 - 8 are hotbar
        for (int i = 0; i <= 8; i++) {
            MOD_KIT.put(i, inventory.getItem(i));
        }
    }

    private static UUID asUUID(String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            ModMode.log("Error: bad UUID in config.yml (" + uuidString +").");
            return null;
        }
    }

    private LinkedHashSet<String> getCommandList(String key) {
        return new LinkedHashSet<>(_config.getStringList(key));
    }

    public String getJoinMessage(Player player) {
        UUID uuid = player.getUniqueId();
        String message = SUPPRESSED_JOIN_MESSAGES.get(uuid);
        SUPPRESSED_JOIN_MESSAGES.remove(uuid);
        return message;
    }

    public void suppressJoinMessage(Player player, String message) {
        SUPPRESSED_JOIN_MESSAGES.put(player.getUniqueId(), message);
    }

    // ------------------------------------------------------------------------
    /**
     * Returns true if the given player logged out while vanished.
     *
     * @param player the player.
     * @return true if the given player logged out while vanished.
     */
    public boolean loggedOutVanished(Player player) {
        return LOGGED_OUT_VANISHED.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Sets the player's logged-out-vanished state to the given state.
     *
     * @param player the player.
     * @param state the new state.
     */
    public void setLoggedOutVanished(Player player, boolean state) {
        if (state) {
            LOGGED_OUT_VANISHED.add(player.getUniqueId());
        } else {
            LOGGED_OUT_VANISHED.remove(player.getUniqueId());
        }
    }

    /**
     * Commands executed immediately before ModMode is activated.
     */
    public LinkedHashSet<String> BEFORE_ACTIVATION_COMMANDS = new LinkedHashSet<>();

    /**
     * Commands executed immediately after ModMode is activated.
     */
    public LinkedHashSet<String> AFTER_ACTIVATION_COMMANDS = new LinkedHashSet<>();

    /**
     * Commands executed immediately before ModMode is deactivated.
     */
    public LinkedHashSet<String> BEFORE_DEACTIVATION_COMMANDS = new LinkedHashSet<>();

    /**
     * Commands executed immediately after ModMode is deactivated.
     */
    public LinkedHashSet<String> AFTER_DEACTIVATION_COMMANDS = new LinkedHashSet<>();

}
