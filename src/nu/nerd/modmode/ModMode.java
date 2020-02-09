package nu.nerd.modmode;

import java.io.File;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.kitteh.vanish.VanishPerms;
import org.kitteh.vanish.VanishPlugin;

import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.User;
import me.lucko.luckperms.api.event.EventBus;
import me.lucko.luckperms.api.event.user.track.UserDemoteEvent;
import me.lucko.luckperms.api.event.user.track.UserPromoteEvent;
import me.lucko.luckperms.api.event.user.track.UserTrackEvent;
import nu.nerd.nerdboard.NerdBoard;

// ------------------------------------------------------------------------
/**
 * The main plugin and command-handling class.
 */
public class ModMode extends JavaPlugin {

    /**
     * This plugin.
     */
    public static ModMode PLUGIN;

    /**
     * The permissions handler.
     */
    public static Permissions PERMISSIONS;

    /**
     * This plugin's configuration.
     */
    public static Configuration CONFIG;

    /**
     * The vanish plugin.
     */
    private VanishPlugin vanish;

    // ------------------------------------------------------------------------
    /**
     * @see JavaPlugin#onEnable().
     */
    @Override
    public void onEnable() {
        PLUGIN = this;

        // find and load NerdBoard
        NerdBoard nerdBoard = NerdBoardHook.getNerdBoard();
        if (nerdBoard != null) {
            new NerdBoardHook(nerdBoard);
        } else {
            log("NerdBoard is required. http://github.com/nerdnu/NerdBoard");
            getPluginLoader().disablePlugin(this);
            return;
        }

        vanish = (VanishPlugin) getServer().getPluginManager().getPlugin("VanishNoPacket");
        if (vanish == null) {
            log("VanishNoPacket required. Download it here http://dev.bukkit.org/server-mods/vanish/");
            getPluginLoader().disablePlugin(this);
            return;
        } else {
            // Make sure that VanishNoPacket is enabled.
            getPluginLoader().enablePlugin(vanish);

            // Intercept the /vanish command and handle it here.
            CommandExecutor ownExecutor = getCommand("modmode").getExecutor();
            PluginCommand vanishCommand = vanish.getCommand("vanish");
            vanishCommand.setExecutor(ownExecutor);
            vanishCommand.setPermission(Permissions.VANISH);
        }

        if (getServer().getPluginManager().isPluginEnabled("LogBlock")) {
            new LogBlockListener();
        } else {
            log("LogBlock missing or unloaded. Integration disabled.");
        }

        CONFIG = new Configuration();

        // instantiate the self-registering ModMode listener
        new ModModeListener();

        // instantiate permissions handler
        PERMISSIONS = new Permissions();

        // Repeating task to prevent phantom spawns for players in ModMode.
        // The time since rest statistic is also cleared when entering ModMode.
        // This just caters for staff who stay in ModMode for over 80 minutes.
        final int TEN_MINUTES = 10 * 60 * 20;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!CONFIG.MODMODE_PENDING.contains(player)
                    && CONFIG.MODMODE_CACHE.contains(player)) {
                    player.setStatistic(Statistic.TIME_SINCE_REST, 0);
                }
            }
        }, TEN_MINUTES, TEN_MINUTES);

        RegisteredServiceProvider<LuckPermsApi> svcProvider = Bukkit.getServer().getServicesManager().getRegistration(LuckPermsApi.class);
        if (svcProvider != null) {
            LuckPermsApi api = svcProvider.getProvider();
            EventBus eventBus = api.getEventBus();
            eventBus.subscribe(UserPromoteEvent.class, this::onUserTrackEvent);
            eventBus.subscribe(UserDemoteEvent.class, this::onUserTrackEvent);
        }
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
     * Return true if the player is currently vanished.
     *
     * @return true if the player is currently vanished.
     */
    boolean isVanished(Player player) {
        return vanish.getManager().isVanished(player);
    }

    // ------------------------------------------------------------------------
    /**
     * Save the current vanish state of the player as their persistent vanish
     * state.
     *
     * This means different things depending on whether the player could
     * normally vanish without being in ModMode. See the doc comment for
     * {@link Configuration#LOGGED_OUT_VANISHED}.
     */
    void setPersistentVanishState(Player player) {
        CONFIG.setLoggedOutVanished(player, vanish.getManager().isVanished(player));
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the player is currently in ModMode.
     *
     * @param player the Player.
     * @return true if the player is currently in ModMode.
     */
    boolean isModMode(Player player) {
        return CONFIG.MODMODE_CACHE.contains(player);
    }

    // ------------------------------------------------------------------------
    /**
     * Set the vanish state of the player.
     *
     * @param player the Player.
     * @param vanished true if he should be vanished.
     */
    void setVanish(Player player, boolean vanished) {
        if (vanish.getManager().isVanished(player) != vanished) {
            vanish.getManager().toggleVanish(player);

            // Update the permissions that VanishNoPacket caches to match the
            // new permissions of the player. This is highly dependent on this
            // API method not doing anything more than what it currently does:
            // to simply remove the cached VanishUser (permissions) object.
            VanishPerms.userQuit(player);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Try to coerce VanishNoPacket into showing or hiding players to each other
     * based on their current vanish state and permissions.
     *
     * Just calling resetSeeing() when a moderator toggles ModMode (and hence
     * permissions and vanish state) is apparently insufficient.
     */
    void updateAllPlayersSeeing() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            vanish.getManager().playerRefresh(player);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Sends a list of currently-vanished players to the given CommandSender.
     *
     * @param sender the CommandSender.
     */
    private void showVanishList(CommandSender sender) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (vanish.getManager().isVanished(player)) {
                if (first) {
                    first = false;
                } else {
                    result.append(", ");
                }
                result.append(player.getName());
            }
        }

        if (result.length() == 0) {
            sender.sendMessage(ChatColor.RED + "All players are visible!");
        } else {
            sender.sendMessage(ChatColor.RED + "Vanished players: " + result);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Returns a "clean" ModMode name for the given player by prepending the
     * player's name with "modmode_".
     *
     * @param player the player.
     * @return the player's ModMode name.
     */
    String getCleanModModeName(Player player) {
        return "modmode_" + player.getName();
    }

    // ------------------------------------------------------------------------
    /**
     * Return the File used to store the player's normal or ModMode state.
     *
     * @param player the player.
     * @param isModMode true if the data is for the ModMode state.
     * @return the File used to store the player's normal or ModMode state.
     */
    private File getStateFile(Player player, boolean isModMode) {
        File playersDir = new File(getDataFolder(), "players");
        playersDir.mkdirs();

        String fileName = player.getUniqueId().toString() + ((isModMode) ? "_modmode" : "_normal") + ".yml";
        return new File(playersDir, fileName);
    }

    // ------------------------------------------------------------------------
    /**
     * Save the player's data to a YAML configuration file.
     *
     * @param player the player.
     * @param isModMode true if the saved data is for the ModMode inventory.
     */
    private void savePlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);
        if (CONFIG.debugPlayerData) {
            log("savePlayerData(): " + stateFile);
        }

        // Keep 2 backups of saved player data.
        try {
            File backup1 = new File(stateFile.getPath() + ".1");
            File backup2 = new File(stateFile.getPath() + ".2");
            backup1.renameTo(backup2);
            stateFile.renameTo(backup1);
        } catch (Exception ex) {
            String msg = " raised saving state file backups for " + player.getName()
                         + " (" + player.getUniqueId().toString() + ").";
            log(ex.getClass().getName() + msg);
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
            log("Failed to save player data for " + player.getName() + "(" + player.getUniqueId().toString() + ")");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Load the player's data from a YAML configuration file.
     *
     * @param player the player.
     * @param isModMode true if the loaded data is for the ModMode inventory.
     */
    private void loadPlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);
        if (CONFIG.debugPlayerData) {
            log("loadPlayerData(): " + stateFile);
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(stateFile);
            player.setHealth(config.getDouble("health"));
            player.setFoodLevel(config.getInt("food"));
            float level = (float) config.getDouble("experience");
            player.setLevel((int) Math.floor(level));
            player.setExp(level - player.getLevel());
            player.setFireTicks(config.getInt("fireticks"));
            player.setFallDistance((float) config.getDouble("falldistance"));
            player.setStatistic(Statistic.TIME_SINCE_REST, config.getInt("awaketicks"));

            if (!isModMode) {
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
                        log("[ModMode] Exception while loading " + player.getName() + "'s inventory: " + ex);
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
                        log("[ModMode] Exception while loading " + player.getName() + "'s ender chest: " + ex);
                    }
                }
            }
        } catch (Exception ex) {
            log("Failed to load player data for " + player.getName() + "(" + player.getUniqueId().toString() + ")");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Handle the part of the process of changing the player's ModMode state
     * that precedes permission changes.
     * 
     * @param player the affected player.
     * @param enabled true if the player is entering the ModMode state. If true,
     *        the player is enabling ModMode; if false, the player is disabling
     *        ModMode.
     */
    private void startToggleModMode(Player player, boolean enabled) {
        // log("startToggleModMode()");
        if (enabled) {
            runCommands(player, CONFIG.beforeActivationCommands);
        } else {
            runCommands(player, CONFIG.beforeDeactivationCommands);
        }

        // Save player data for the old ModMode state and load for the new.
        savePlayerData(player, !enabled);
        loadPlayerData(player, enabled);

        // When entering ModMode, clear damage sources and phantoms.
        if (enabled) {
            player.setFireTicks(0);
            player.setFallDistance(0F);

            // Moderators should not spawn phantoms.
            player.setStatistic(Statistic.TIME_SINCE_REST, 0);
        }

        if (PERMISSIONS.isAdmin(player)) {
            // For Admins only:
            if (enabled) {
                // When entering ModMode, record old vanish state, then vanish.
                setPersistentVanishState(player);
                setVanish(player, true);
            } else {
                // When leaving ModMode, return to persistent vanish state.
                setVanish(player, CONFIG.loggedOutVanished(player));
            }
        } else {
            // For non-Admins:
            setVanish(player, enabled);
        }

        // Note: since the permissions change is async, possibly spanning
        // multiple ticks, and it's not immediately obvious how event handlers
        // should handle this limbo state. If we run into problems, we can
        // consult CONFIG.MODMODE_PENDING, which will not contain the player
        // once their permissions are finalised.
        //
        // For now, we will record the player as being in ModMode so there
        // are no surprises in the config. But it might be more reliable to
        // delay the state change until finishToggleModMode(). Alternatively,
        // perhaps we could change {@link #isModMode()} to factor in the
        // CONFIG.MODMODE_PENDING state as well.
        if (enabled) {
            CONFIG.MODMODE_CACHE.add(player);
        } else {
            CONFIG.MODMODE_CACHE.remove(player);
        }

        // Signify that there is a pending permission change for the player.
        // This prevents the repeating task from clearing the time since rest
        // statistic while the permissions plugin is thinking (over several
        // ticks).
        CONFIG.MODMODE_PENDING.add(player);
        CONFIG.save();
    }

    // ------------------------------------------------------------------------
    /**
     * Handle the part of the process of changing the player's ModMode state
     * that follows permission changes.
     * 
     * @param player the affected player.
     * @param enabled true if the player is entering the ModMode state. If true,
     *        the player is enabling ModMode; if false, the player is disabling
     *        ModMode.
     */
    void finishToggleModMode(Player player, boolean enabled) {
        // log("finishToggleModMode()");
        // Update the permissions that VanishNoPacket caches to match the new
        // permissions of the player. This is highly dependent on this API
        // method not doing anything more than what it currently does:
        // to simply remove the cached VanishUser (permissions) object.
        VanishPerms.userQuit(player);

        // Update the nametag for the player.
        NerdBoardHook.reconcilePlayerWithVanishState(player);

        // Update who sees whom AFTER permissions and vanish state changes.
        updateAllPlayersSeeing();

        // Chunk error (resend to all clients).
        World w = player.getWorld();
        Chunk c = w.getChunkAt(player.getLocation());
        w.refreshChunk(c.getX(), c.getZ());

        restoreFlight(player, enabled);

        if (enabled) {
            runCommands(player, CONFIG.afterActivationCommands);
        } else {
            runCommands(player, CONFIG.afterDeactivationCommands);
        }

        // All done bar the shouting.
        // We can now re-enable normal operation of the repeating task.
        CONFIG.MODMODE_PENDING.remove(player);
        CONFIG.save();

        // Send status messages last, so that client mods that scrape chat
        // trigger when everything has been done.
        if (enabled) {
            player.sendMessage(ChatColor.RED + "You are now in ModMode!");
        } else {
            if (!PERMISSIONS.isAdmin(player)) {
                if (CONFIG.joinedVanished.containsKey(player.getUniqueId().toString())) {
                    getServer().broadcastMessage(CONFIG.joinedVanished.get(player.getUniqueId().toString()));
                }
            }
            player.sendMessage(ChatColor.RED + "You are no longer in ModMode!");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Set the given player's ModMode state to enabled (true) or disabled
     * (false).
     * 
     * The transition into ModMode comprises three steps:
     * <ol>
     * <li>Actions appropriate for the player's permissions prior to changing
     * state and actions that are not affected by permissions are performed
     * first.</li>
     * <li>Asynchronous promotion or demotion of the player along a track to
     * enter or leave ModMode. Completion of this permission processing
     * eventually triggers an asynchronous event that is used to schedule a
     * synchronous task to perform the next step.</li>
     * <li>Actions that depend on the player's permissions to be in their final
     * (promoted or demoted state).</li>
     * </ol>
     * 
     * There is a slight wrinkle in that full admins inherit the ModMode group
     * and therefore don't get promoted or demoted. Thus, there are no promotion
     * or demotion events. Instead, we simply run the final step listed above
     * immediately.
     * 
     * @param player the affected player.
     * @param enabled true if the player is entering the ModMode state. If true,
     *        the player is enabling ModMode; if false, the player is disabling
     *        ModMode.
     */
    private void toggleModMode(final Player player, boolean enabled) {
        // log("toggleModMode()");
        startToggleModMode(player, enabled);

        // Full Admins already possess the ModMode group and therefore don't
        // get promoted or demoted, so steps 2 and 3 are synchronous.
        if (PERMISSIONS.isAdmin(player)) {
            finishToggleModMode(player, enabled);
        } else {
            // For non-Admins, call finishToggleModMode() in onUserPromote() or
            // onUserDemote(), as appropriate.
            if (enabled) {
                // Entering ModMode:
                // Promote moderators and foreign server admins to ModMode.
                PERMISSIONS.promote(player);
            } else {
                // Leaving ModMode:
                // Demote from ModMode back to moderator or foreign server
                // admin group.
                PERMISSIONS.demote(player);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * When a player is promoted up or demoted down a track (async), schedule a
     * main-thread- synchronous task to complete the ModMode transition.
     * 
     * This method is not a Bukkit event handler; it uses LuckPerms' EventBus.
     * 
     * @param event the UserPromoteEvent or UserDemoteEvent.
     */
    protected void onUserTrackEvent(UserTrackEvent event) {
        // log("onUserTrackEvent()");
        User user = event.getUser();
        // String action = event.getAction() == TrackAction.PROMOTION ?
        // "Promoted " : "Demoted ";
        // Track track = event.getTrack();
        // Optional<String> fromGroup = event.getGroupFrom();
        // Optional<String> toGroup = event.getGroupTo();

        // log(action + user.getName() +
        // " along " + track.getName() +
        // " from " + fromGroup.orElse("?") + " to " + toGroup.orElse("?"));

        Player player = Bukkit.getPlayer(user.getUuid());
        if (player != null) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                finishToggleModMode(player, isModMode(player));
            }, 0);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Restore flight ability if in ModMode or creative game mode.
     *
     * @param player the player.
     * @param isInModMode true if the player is in ModMode.
     */
    void restoreFlight(Player player, boolean isInModMode) {
        player.setAllowFlight((isInModMode && CONFIG.allowFlight) || player.getGameMode() == GameMode.CREATIVE);
    }

    // ------------------------------------------------------------------------
    /**
     * @see CommandExecutor#onCommand(CommandSender, Command, String, String[]).
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        if (command.getName().equalsIgnoreCase("vanishlist")) {
            showVanishList(sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("modmode")) {
            cmdModMode(sender, args);
            return true;
        }

        if (!isInGame(sender)) {
            return true;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("vanish")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
                String vanishText = isVanished(player) ? "vanished." : "visible.";
                player.sendMessage(ChatColor.DARK_AQUA + "You are " + vanishText);
            } else if (isVanished(player)) {
                player.sendMessage(ChatColor.DARK_AQUA + "You are already vanished.");
            } else {
                setVanish(player, true);
                NerdBoardHook.reconcilePlayerWithVanishState(player);
            }
        } else if (command.getName().equalsIgnoreCase("unvanish")) {
            if (isVanished(player)) {
                setVanish(player, false);
                NerdBoardHook.reconcilePlayerWithVanishState(player);
            } else {
                player.sendMessage(ChatColor.DARK_AQUA + "You are already visible.");
            }
        }
        return true;
    } // onCommand

    // ------------------------------------------------------------------------
    /**
     * Handle /modmode [save|reload] both for players in-game and the console.
     *
     * @param sender the command sender.
     * @param args command arguments.
     */
    private void cmdModMode(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (isInGame(sender)) {
                Player player = (Player) sender;
                if (CONFIG.MODMODE_PENDING.contains(player)) {
                    sender.sendMessage(ChatColor.RED + "You are still changing mode. Wait until your previous /modmode command completes.");
                    return;
                }

                toggleModMode(player, !isModMode(player));
            }
        } else if (args.length == 1 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("reload"))) {
            if (!sender.hasPermission(Permissions.OP)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use /modmode op commands.");
                return;
            }

            if (args[0].equalsIgnoreCase("save")) {
                CONFIG.save();
                sender.sendMessage(ChatColor.GOLD + "ModMode configuration saved.");
            } else if (args[0].equalsIgnoreCase("reload")) {
                CONFIG.reload();
                sender.sendMessage(ChatColor.GOLD + "ModMode configuration reloaded.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /modmode [save | reload]");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the CommandSender is a Player (in-game).
     *
     * If the command sender is not in game, tell them they need to be in-game
     * to use the current command.
     *
     * @param sender the command sender.
     * @return true if the CommandSender is a Player (in-game).
     */
    private boolean isInGame(CommandSender sender) {
        boolean inGame = (sender instanceof Player);
        if (!inGame) {
            sender.sendMessage("You need to be in-game to use this command.");
        }
        return inGame;
    }

    // ------------------------------------------------------------------------
    /**
     * Run all of the commands in the List of Strings.
     *
     * @param player the moderator causing the commands to run.
     * @param commands the commands to run.
     */
    private void runCommands(Player player, List<String> commands) {
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
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "ModMode" + ChatColor.DARK_GRAY + "] ";

} // ModMode
