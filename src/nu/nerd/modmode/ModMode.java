package nu.nerd.modmode;

import de.myzelyam.api.vanish.VanishAPI;
import nu.nerd.nerdboard.NerdBoard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

// ------------------------------------------------------------------------
/**
 * The main plugin and command-handling class.
 */
public class ModMode extends JavaPlugin {

    /**
     * This plugin.
     */
    static ModMode PLUGIN;

    /**
     * The permissions handler.
     */
    private static Permissions PERMISSIONS;

    /**
     * A cache of players (UUIDs) currently in ModMode.
     */
    static final AbstractPlayerCache MODMODE_CACHE = new AbstractPlayerCache("modmode");

    // ------------------------------------------------------------------------
    /**
     * @see JavaPlugin#onEnable().
     */
    @Override
    public void onEnable() {
        PLUGIN = this;
        Configuration.reload();
        findDependencies();
        new ModModeListener();
        PERMISSIONS = new Permissions();
    }

    private void findDependencies() {
        try {
            VanishAPI.getPlugin();
        } catch (Exception e) {
            log("SuperVanish is required. https://github.com/MyzelYam/SuperVanish");
            getPluginLoader().disablePlugin(this);
            return;
        }

        NerdBoard nerdBoard = NerdBoardHook.getNerdBoard();
        if (nerdBoard != null) {
            new NerdBoardHook(nerdBoard);
        } else {
            log("NerdBoard is required. http://github.com/nerdnu/NerdBoard");
            getPluginLoader().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().isPluginEnabled("LogBlock")) {
            new LogBlockListener();
        } else {
            log("LogBlock missing or unloaded. Integration disabled.");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * @see JavaPlugin#onDisable().
     */
    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Configuration.save();
    }

    // ------------------------------------------------------------------------
    /**
     * Returns a reference to the ModMode player cache.
     *
     * @return a reference to the ModMode player cache.
     */
    static AbstractPlayerCache getModModeCache() {
        return MODMODE_CACHE;
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the player is currently vanished.
     *
     * @return true if the player is currently vanished.
     */
    boolean isVanished(Player player) {
        return VanishAPI.isInvisible(player);
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the player is currently in ModMode.
     *
     * @param player the Player.
     * @return true if the player is currently in ModMode.
     */
    synchronized boolean isModMode(Player player) {
        return MODMODE_CACHE.contains(player);
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
        if (Configuration.DEBUG_PLAYER_DATA) {
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
        if (Configuration.DEBUG_PLAYER_DATA) {
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
     * Sets the given player's ModMode status to the given state.
     *
     * @param player the player.
     * @param enabled the state.
     */
    private void toggleModMode(final Player player, boolean enabled) {
        if (enabled) {
            runCommands(player, Configuration.BEFORE_ACTIVATION_COMMANDS);
        } else {
            runCommands(player, Configuration.BEFORE_DEACTIVATION_COMMANDS);
        }

        if (!enabled) {
            PERMISSIONS.removeGroup(player, Configuration.MODMODE_GROUP);

            // re-add the player's serialized groups
            List<String> serializedGroups = Configuration.getSerializedGroups(player);
            serializedGroups.forEach(group -> PERMISSIONS.addGroup(player, group));

            MODMODE_CACHE.remove(player);

            player.sendMessage(ChatColor.RED + "You are no longer in ModMode!");
        } else {
            // clean up old mappings
            Configuration.sanitizeSerializedGroups(player);

            // get & serialize player's current groups
            HashSet<String> groups = PERMISSIONS.getGroups(player);
            Configuration.serializeGroups(player, groups);

            // remove all groups that are not configured to be persistent
            groups.stream()
                  .filter(group -> !Configuration.isPersistentGroup(group))
                  .forEach(group -> PERMISSIONS.removeGroup(player, group));

            PERMISSIONS.addGroup(player, Configuration.MODMODE_GROUP);

            MODMODE_CACHE.add(player);

            player.sendMessage(ChatColor.RED + "You are now in ModMode!");
        }

        // update the nametag for the player
        NerdBoardHook.reconcilePlayerWithVanishState(player);

        // Save player data for the old ModMode state and load for the new.
        savePlayerData(player, !enabled);
        loadPlayerData(player, enabled);

        // Hopefully stop some minor falls
        player.setFallDistance(0F);

        Configuration.save();

        if (enabled) {
            runCommands(player, Configuration.AFTER_ACTIVATION_COMMANDS);
        } else {
            runCommands(player, Configuration.AFTER_DEACTIVATION_COMMANDS);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * @see CommandExecutor#onCommand(CommandSender, Command, String, String[]).
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        if (command.getName().equalsIgnoreCase("modmode")) {
            cmdModMode(sender, args);
        }
        return true;
    }

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
                toggleModMode(player, !isModMode(player));
            }
        } else if (args.length == 1 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("reload"))) {
            if (!sender.hasPermission(Permissions.OP)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use /modmode op commands.");
                return;
            }

            if (args[0].equalsIgnoreCase("save")) {
                Configuration.save();
                sender.sendMessage(ChatColor.GOLD + "ModMode configuration saved.");
            } else if (args[0].equalsIgnoreCase("reload")) {
                Configuration.reload();
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
     * Runs all of the given commands as the given player.
     *
     * @param player the moderator causing the commands to run.
     * @param commands the commands to run.
     */
    private void runCommands(Player player, Collection<String> commands) {
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
