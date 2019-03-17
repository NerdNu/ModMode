package nu.nerd.modmode;

import de.diddiz.LogBlock.LogBlock;
import me.lucko.luckperms.LuckPerms;
import nu.nerd.nerdboard.NerdBoard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.Consumer;

// ------------------------------------------------------------------------
/**
 * The main plugin class.
 */
public class ModMode extends JavaPlugin {

    /**
     * This plugin.
     */
    public static ModMode PLUGIN;

    /**
     * This plugin's configuration.
     */
    public static Configuration CONFIG;

    /**
     * Encapsulation of LuckPerms-related methods and permission constants.
     */
    private static Permissions PERMISSIONS;

    /**
     * Encapsulation of NerdBoard-related methods.
     */
    static NerdBoardHook NERDBOARD;

    /**
     * Cache of players currently in ModMode.
     */
    static final HashSet<UUID> MODMODE = new HashSet<>();

    /**
     * Cache of players currently vanished.
     */
    static final HashSet<UUID> VANISHED = new HashSet<>();

    // ------------------------------------------------------------------------
    /**
     * @see JavaPlugin#onEnable().
     */
    @Override
    public void onEnable() {
        PLUGIN = this;
        CONFIG = new Configuration();
        new ModModeListener();
        new Commands();

        assertDependency("NerdBoard", NerdBoard.class, true,  p -> NERDBOARD = new NerdBoardHook((NerdBoard) p));
        assertDependency("LogBlock",  LogBlock.class,  false, p -> new LogBlockListener());
        assertDependency("LuckPerms", null,            true,  p -> PERMISSIONS = new Permissions(LuckPerms.getApi()));

        Bukkit.getScheduler().runTaskTimer(ModMode.PLUGIN, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (MODMODE.contains(uuid)) {
                    player.sendActionBar(String.format("%sYou are currently %s", ChatColor.GREEN, "in ModMode"));
                } else if (VANISHED.contains(uuid)) {
                    player.sendActionBar(String.format("%sYou are currently %s", ChatColor.BLUE, "vanished"));
                }
            }
        }, 1, 20);

        Bukkit.getScheduler().runTaskTimer(ModMode.PLUGIN, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                    if (!otherPlayer.canSee(player)) {
                        otherPlayer.hidePlayer(this, player);
                    } else {
                        otherPlayer.showPlayer(this, player);
                    }
                }
            }
        }, 1, 10);
    }

    // ------------------------------------------------------------------------
    /**
     * @see JavaPlugin#onDisable().
     */
    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        CONFIG.save();
    }

    // ------------------------------------------------------------------------
    /**
     * Asserts a plugin dependency by fetching the plugin from the loader by
     * name, checking if it is an instance of pluginClass, and then supplies it
     * to the consumer. If it is a hard dependency (if required is true), then
     * this plugin will disable if the dependency is not found.
     *
     * @param name the name of the plugin.
     * @param pluginClass the plugin class.
     * @param required true if the plugin is a hard dependency.
     * @param consumer the action to take if the plugin is found.
     */
    private void assertDependency(String name, Class pluginClass, boolean required, Consumer<Object> consumer) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (pluginClass != null && pluginClass.isInstance(plugin)) {
            consumer.accept(pluginClass.cast(plugin));
            return;
        } else {
            try {
                consumer.accept(null);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (required) {
            log("Fatal error: " + name + " is required but could not be found.");
            Bukkit.getPluginManager().disablePlugin(this);
        } else {
            log("Warn: " + name + " missing or unloaded. Integration disabled.");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Toggles the player's ModMode state.
     *
     * @param player the player.
     */
    void toggleModMode(Player player) {
        final long timeStart = System.currentTimeMillis();
        boolean isAdmin = player.hasPermission(Permissions.ADMIN);
        boolean enteringModMode = !isModMode(player);
        boolean vanished = enteringModMode || (isAdmin && CONFIG.loggedOutVanished(player));
        runCommands(player, enteringModMode ? CONFIG.BEFORE_ACTIVATION_COMMANDS : CONFIG.BEFORE_DEACTIVATION_COMMANDS);
        PERMISSIONS
            .updatePermissions(player, enteringModMode) // off main thread
            .thenRun(() -> { // back on main thread
                if (enteringModMode) {
                    MODMODE.add(player.getUniqueId());
                } else {
                    if (CONFIG.loggedOutVanished(player)) {
                        if (!isAdmin) {
                            getServer().broadcastMessage(CONFIG.getJoinMessage(player));
                        }
                        // we don't need to know this anymore
                        CONFIG.setLoggedOutVanished(player, false);
                    }
                    MODMODE.remove(player.getUniqueId());
                }
                // Save player data for the old ModMode state and load for the new.
                PlayerState.savePlayerData(player, !enteringModMode);
                PlayerState.loadPlayerData(player, enteringModMode);

                setVanished(player, vanished);
                setTranscendentalAttributes(player);
                runCommands(player, enteringModMode ? CONFIG.AFTER_ACTIVATION_COMMANDS : CONFIG.AFTER_DEACTIVATION_COMMANDS);
                long duration = System.currentTimeMillis() - timeStart;
                player.sendMessage(String.format("%sYou are %s in ModMode %s(took %d ms, %.2f ticks)",
                    ChatColor.RED,
                    enteringModMode ? "now" : "no longer",
                    ChatColor.GRAY,
                    duration,
                    (double) duration/50));
                log("Task took " + duration + " ms.");
            });
    }

    // ------------------------------------------------------------------------
    /**
     * Sets a player's transcendental attributes, i.e. makes them "leave no
     * footprints," or so to speak.
     *
     * @param player the player.
     */
    private void setTranscendentalAttributes(Player player) {
        boolean inModMode = PLUGIN.isModMode(player);
        boolean isVanished = PLUGIN.isVanished(player);
        boolean isTranscendental = PLUGIN.isTranscendental(player);
        player.setAffectsSpawning(isTranscendental);
        player.setAllowFlight((CONFIG.ALLOW_FLIGHT && inModMode) || player.getGameMode() == GameMode.CREATIVE);
        player.setCanPickupItems(!isVanished);
        player.setInvulnerable(isTranscendental);
        player.setSleepingIgnored(isTranscendental);
        player.setSilent(isTranscendental);
    }

    // ------------------------------------------------------------------------
    /**
     * Sets the player's current vanish state.
     *
     * @param player the player.
     * @param vanishing true to vanish; false to unvanish.
     */
    void setVanished(Player player, boolean vanishing) {
        UUID uuid = player.getUniqueId();
        if (vanishing) {
            VANISHED.add(uuid);
            player.sendMessage(ChatColor.DARK_AQUA + "You have vanished. Poof.");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.getOnlinePlayers().forEach(p -> {
                    if (!Permissions.isAdmin(p) && !isModMode(p)) {
                        p.hidePlayer(this, player);
                    }
                });
            }, 1);
        } else {
            VANISHED.remove(uuid);
            player.sendMessage(ChatColor.DARK_AQUA + "You are no longer vanished.");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.getOnlinePlayers().forEach(p -> p.showPlayer(this, player));
            }, 1);
        }
        NERDBOARD.reconcilePlayerWithVanishState(player);
        setTranscendentalAttributes(player);
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the player is currently in ModMode.
     *
     * @param player the Player.
     * @return true if the player is currently in ModMode.
     */
    public boolean isModMode(Player player) {
        return MODMODE.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Returns true if the player is in ModMode *or* is vanished, i.e. in a
     * transcendental state.
     *
     * @param player the player.
     * @return true if the player is in ModMode or is vanished.
     */
    public boolean isTranscendental(Player player) {
        return player != null && (isModMode(player) || isVanished(player));
    }

    // ------------------------------------------------------------------------
    /**
     * Returns true if the given player is vanished.
     *
     * @param player the player.
     * @return true if the player is vanished.
     */
    public boolean isVanished(Player player) {
        return player != null && VANISHED.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Restore flight ability if in ModMode or creative game mode.
     *
     * @param player the player.
     * @param isInModMode true if the player is in ModMode.
     */
    void restoreFlight(Player player, boolean isInModMode) {
        player.setAllowFlight((isInModMode && CONFIG.ALLOW_FLIGHT) || player.getGameMode() == GameMode.CREATIVE);
    }

    // ------------------------------------------------------------------------
    /**
     * Run all of the commands in the List of Strings.
     *
     * @param player the moderator causing the commands to run.
     * @param commands the commands to run.
     */
    private void runCommands(Player player, LinkedHashSet<String> commands) {
        for (String command : commands) {
            // dispatchCommand() doesn't cope with a leading '/' in commands
            if (command.length() > 0 && command.charAt(0) == '/') {
                command = command.substring(1);
            }
            try {
                if (!Bukkit.getServer().dispatchCommand(player, command)) {
                    log("Command \"" + command + "\" could not be executed.");
                }
            } catch (Exception ex) {
                log("Command \"" + command + "\" raised " + ex.getClass().getName());
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * A logging method used instead of {@link java.util.logging.Logger} to
     * faciliate prefix coloring.
     *
     * @param msg the message to log.
     */
    static void log(String msg) {
        System.out.println(PREFIX + msg);
    }

    // ------------------------------------------------------------------------
    /**
     * This plugin's prefix as a string; for logging.
     */
    private static final String PREFIX = String.format("%s[%sModMode%s]%s",
        ChatColor.DARK_GRAY,
        ChatColor.GREEN,
        ChatColor.DARK_GRAY,
        ChatColor.RESET);

} // ModMode
