package nu.nerd.modmode;

import java.io.File;
import java.util.List;

import me.neznamy.tab.api.TabPlayer;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.util.Result;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitScheduler;
import org.kitteh.vanish.VanishPerms;
import org.kitteh.vanish.VanishPlugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.track.UserDemoteEvent;
import net.luckperms.api.event.user.track.UserPromoteEvent;
import net.luckperms.api.event.user.track.UserTrackEvent;
import net.luckperms.api.model.user.User;

import static org.bukkit.Bukkit.getPluginManager;

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

    /**
     * The server's Bukkit scheduler.
     */
    private BukkitScheduler scheduler = Bukkit.getScheduler();

    // ------------------------------------------------------------------------
    /**
     * @see JavaPlugin#onEnable().
     */
    @Override
    public void onEnable() {
        PLUGIN = this;

        // find and load NerdBoard
        Plugin tabPlugin = getPluginManager().getPlugin("TAB");
        if (tabPlugin == null) {
            log("TAB is required. https://modrinth.com/plugin/tab-was-taken");
            getPluginManager().disablePlugin(this);
            return;
        }

        vanish = (VanishPlugin) getPluginManager().getPlugin("VanishNoPacket");
        if (vanish == null) {
            log("VanishNoPacket required. Download it here http://dev.bukkit.org/server-mods/vanish/");
            getPluginManager().disablePlugin(this);
            return;
        } else {
            // Make sure that VanishNoPacket is enabled.
            getPluginManager().enablePlugin(vanish);

            // Intercept the /vanish command and handle it here.
            CommandExecutor ownExecutor = getCommand("modmode").getExecutor();
            PluginCommand vanishCommand = vanish.getCommand("vanish");
            vanishCommand.setExecutor(ownExecutor);
            vanishCommand.setPermission(Permissions.VANISH);
        }

        Plugin coreProtectPlugin = getServer().getPluginManager().getPlugin("CoreProtect");
        if (coreProtectPlugin != null && coreProtectPlugin.isEnabled()) {
            CoreProtectAPI coreProtectAPI = ((CoreProtect) coreProtectPlugin).getAPI();
            if(coreProtectAPI.isEnabled()) {
                new CoreProtectListener();
            }
        } else {
            log("CoreProtect missing or unloaded. Integration disabled.");
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
                if (CONFIG.MODMODE_CACHE.contains(player)) {
                    player.setStatistic(Statistic.TIME_SINCE_REST, 0);
                }
            }
        }, TEN_MINUTES, TEN_MINUTES);

        RegisteredServiceProvider<LuckPerms> svcProvider = Bukkit.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (svcProvider != null) {
            LuckPerms api = svcProvider.getProvider();
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
     * Return true if the player is currently in AdminMode.
     *
     * @param player the Player.
     * @return true if the player is currently in AdminMode.
     */
    boolean isAdminMode(Player player) {
        return CONFIG.ADMINMODE_CACHE.contains(player);
    }

    // ------------------------------------------------------------------------
    /**
     * Set the vanish state of the player.
     *
     * @param player   the Player.
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
     * @param player    the player.
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
     * @param player    the player.
     * @param isModMode true if the saved data is for the ModMode inventory.
     */
    private void savePlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);
        if (CONFIG.DEBUG_PLAYER_DATA) {
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
     * @param player    the player.
     * @param isModMode true if the loaded data is for the ModMode inventory.
     */
    private void loadPlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);
        if (CONFIG.DEBUG_PLAYER_DATA) {
            log("loadPlayerData(): " + stateFile);
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(stateFile);
            final double MAX_HEALTH = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(MAX_HEALTH, config.getDouble("health")));
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
            log("Failed to load player data for " + player.getName() + "(" + player.getUniqueId().toString() + "): " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Clear the player's incoming damage before entering ModMode.
     *
     * @param player The player.
     */
    private void clearDamage(Player player) {
        player.setFireTicks(0);
        player.setFallDistance(0F);

        // Moderators should not spawn phantoms.
        player.setStatistic(Statistic.TIME_SINCE_REST, 0);
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
     * @param player  the affected player.
     * @param enabled true if the player is entering the ModMode state. If true,
     *                the player is enabling ModMode; if false, the player is
     *                disabling ModMode.
     */
    private void toggleMode(final Player player, boolean enabled, String command) {

        TabPlayer tabPlayer = TABHook.playerToTabPlayer(player);
        ModModeGroup group = TABHook.getOrCreateGroup(command);

        if(command.equalsIgnoreCase("adminmode")) {
            if(group.isMember(tabPlayer) && !isAdminMode(player)) {
                player.sendMessage(Component.text("You are still changing mode! Please wait for your previous" +
                        " /adminmode command to complete.", NamedTextColor.RED));
            } else if(isModMode(player)) {
                player.sendMessage(Component.text("You are in ModMode! Please leave ModMode before switching" +
                        " to AdminMode.", NamedTextColor.RED));
            }
        } else if(command.equalsIgnoreCase("modmode")) {
            if(group.isMember(tabPlayer) && !isModMode(player)) {
                player.sendMessage(Component.text("You are still changing mode! Please wait for your previous" +
                        " /modmode command to complete.", NamedTextColor.RED));
            } else if(isAdminMode(player)) {
                player.sendMessage(Component.text("You are in AdminMode! Please leave ModMode before switching" +
                        " to ModMode.", NamedTextColor.RED));
            }
        }

        group.addMember(tabPlayer);

        Result result;
        if (enabled) {
            // Entering ModMode:
            // Promote moderators and foreign server admins to ModMode.
            result = PERMISSIONS.promote(player, command);
            if(command.equalsIgnoreCase("modmode")) {
                runCommands(player, CONFIG.MODMODE_BEFORE_ACTIVATION_COMMANDS);
            } else {
                runCommands(player, CONFIG.ADMINMODE_BEFORE_ACTIVATION_COMMANDS);
            }
        } else {
            // Leaving ModMode:
            // Demote from ModMode back to moderator or foreign server
            // admin group.
            result = PERMISSIONS.demote(player, command);
            if(command.equalsIgnoreCase("modmode")) {
                runCommands(player, CONFIG.MODMODE_BEFORE_DEACTIVATION_COMMANDS);
            } else {
                runCommands(player, CONFIG.ADMINMODE_BEFORE_DEACTIVATION_COMMANDS);
            }
        }

        // If the promotion/demotion fails, give error, remove from cached group, and quit.
        if(result.equals(Result.GENERIC_FAILURE)) {
            player.sendMessage(Component.text("State change failed. Please try again in a bit. If this issue" +
                    " persists, let an admin know.", NamedTextColor.RED, TextDecoration.BOLD));
            group.removeMember(tabPlayer);
            return;
        }

        // Swap inventories, clear damage, and set the vanish status
        savePlayerData(player, !enabled);
        loadPlayerData(player, enabled);
        if(enabled) clearDamage(player);
        setVanish(player, enabled);

        if(enabled) {
            if(command.equalsIgnoreCase("modmode")) {
                CONFIG.MODMODE_CACHE.add(player);
            } else {
                CONFIG.ADMINMODE_CACHE.add(player);
            }
        } else {
            if(command.equalsIgnoreCase("modmode")) {
                CONFIG.MODMODE_CACHE.remove(player);
            } else {
                CONFIG.ADMINMODE_CACHE.remove(player);
            }
        }
        CONFIG.save();

        // Update who can see who and nametag colours
        VanishPerms.userQuit(player);
        TABHook.reconcilePlayerWithVanishState(player);
        updateAllPlayersSeeing();

        // Refresh the chunk for all players to avoid errors
        World world = player.getWorld();
        Chunk chunk = world.getChunkAt(player.getLocation());
        world.refreshChunk(chunk.getX(), chunk.getZ());

        restoreFlight(player, enabled);

        if (enabled) {
            if(command.equalsIgnoreCase("modmode")) {
                runCommands(player, CONFIG.MODMODE_AFTER_ACTIVATION_COMMANDS);
                player.sendMessage(Component.text("You are now in ModMode!"));
            } else {
                runCommands(player, CONFIG.ADMINMODE_AFTER_ACTIVATION_COMMANDS);
                player.sendMessage(Component.text("You are now in AdminMode!"));
            }
        } else {
            if(command.equalsIgnoreCase("modmode")) {
                runCommands(player, CONFIG.MODMODE_AFTER_DEACTIVATION_COMMANDS);
                player.sendMessage(Component.text("You are no longer in ModMode!"));
            } else {
                runCommands(player, CONFIG.ADMINMODE_AFTER_DEACTIVATION_COMMANDS);
                player.sendMessage(Component.text("You are no longer in AdminMode!"));
            }
            if(CONFIG.SUPPRESSED_LOGIN_MESSAGE.containsKey(player.getUniqueId())) {
                Bukkit.getServer().broadcast(Component.text(CONFIG.SUPPRESSED_LOGIN_MESSAGE.get(player.getUniqueId())));
            }
        }

    }

    // ------------------------------------------------------------------------
    /**
     * Restore flight ability if in ModMode or creative game mode.
     *
     * @param player      the player.
     * @param isInModMode true if the player is in ModMode.
     */
    void restoreFlight(Player player, boolean isInModMode) {
        player.setAllowFlight((isInModMode && CONFIG.ALLOW_FLIGHT) || player.getGameMode() == GameMode.CREATIVE);
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
                TABHook.reconcilePlayerWithVanishState(player);
            }
        } else if (command.getName().equalsIgnoreCase("unvanish")) {
            if (isVanished(player)) {
                setVanish(player, false);
                TABHook.reconcilePlayerWithVanishState(player);
            } else {
                player.sendMessage(ChatColor.DARK_AQUA + "You are already visible.");
            }
        }
        return true;
    } // onCommand

    // ------------------------------------------------------------------------
    /**
     * Handle /modmode [save|reload|on|off] both for players in-game and the console.
     *
     * @param sender the command sender.
     * @param args   command arguments.
     */
    private void cmdModMode(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (isInGame(sender)) {
                Player player = (Player) sender;
                TabPlayer tabPlayer = TABHook.playerToTabPlayer(player);
                toggleMode(player, !isModMode(player), "modmode");
            }
        } else if (args.length == 1) {
            String argument = args[0];
            switch(argument) {
                case "on":
                    // TODO
                    break;
                case "off":
                    // TODO
                    break;
                case "save":
                    if (!sender.hasPermission(Permissions.OP)) {
                        CONFIG.save();
                        sender.sendMessage(ChatColor.GOLD + "ModMode configuration saved.");
                    }
                    break;
                case "reload":
                    if (!sender.hasPermission(Permissions.OP)) {
                        CONFIG.reload();
                        sender.sendMessage(ChatColor.GOLD + "ModMode configuration reloaded.");
                    }
                    break;
                default:
                    sender.sendMessage(Component.text("Usage: /modmode [save | reload | on | off]", NamedTextColor.RED));
            }
        } else {
            sender.sendMessage(Component.text("Usage: /modmode [save | reload | on | off]", NamedTextColor.RED));
        }
    }

    // ------------------------------------------------------------------------
    //TODO
    /**
     * Handle /adminmode [on|off] both for players in-game and the console.
     *
     * @param sender the command sender.
     * @param args   command arguments.
     */
    private void cmdAdminMode(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (isInGame(sender)) {
                Player player = (Player) sender;
                TabPlayer tabPlayer = TABHook.playerToTabPlayer(player);
                if (TABHook.getOrCreateGroup("adminmode").isMember(tabPlayer) && !CONFIG.ADMINMODE_CACHE.contains(player)) {
                    sender.sendMessage(Component.text("You are still changing mode. Wait until your previous" +
                            " /adminmode command completes.", NamedTextColor.RED));
                    return;
                }

                toggleMode(player, !isModMode(player), "adminmode");
            }
        } else if (args.length == 1) {
            String argument = args[0];
            switch(argument) {
                case "on":
                    // TODO
                    break;
                case "off":
                    // TODO
                    break;
                default:
                    sender.sendMessage(Component.text("Usage: /modmode [save | reload | on | off]", NamedTextColor.RED));
            }
        } else {
            sender.sendMessage(Component.text("Usage: /modmode [save | reload | on | off]", NamedTextColor.RED));
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
     * @param player   the moderator causing the commands to run.
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
     * Logging of general messages.
     *
     * @param msg the message to log.
     */
    static void log(String msg) {
        PLUGIN.getLogger().info(msg);
    }

} // ModMode
