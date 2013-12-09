package nu.nerd.modmode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.kitteh.tag.TagAPI;
import org.kitteh.vanish.VanishPerms;
import org.kitteh.vanish.VanishPlugin;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.util.CalculableType;

public class ModMode extends JavaPlugin {

	private final ModModeListener listener = new ModModeListener(this);

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
	public TreeSet<String> vanished;
	public List<String> modmode;
	public boolean allowFlight;
	public boolean usingbperms;

	/**
	 * If true, player data loads and saves are logged to the console.
	 */
	private boolean debugPlayerData;

	/**
	 * The set of all possible permissions groups who can transition into
	 * ModMode, e.g. Moderators, PAdmins, SAdmins, etc. These must be
	 * mutually-exclusive; you can't be a Moderator and a PAdmin.
	 */
	public List<String> bPermsModGroups;
	public String bPermsModModeGroup;

	/**
	 * When entering ModMode, a staff member in any of bPermsModGroups (e.g.
	 * Moderators, PAdmins, etc.) will have that group removed and the "ModMode"
	 * permission group added. We use this section of the configuration file as
	 * a map from player name to removed group name, so that the normal group
	 * can be restored when they leave ModMode.
	 */
	public ConfigurationSection groupMap;

	/**
	 * Commands executed immediately before ModMode is activated.
	 */
	public List<String> beforeActivationCommands;

	/**
	 * Commands executed immediately after ModMode is activated.
	 */
	public List<String> afterActivationCommands;

	/**
	 * Commands executed immediately before ModMode is deactivated.
	 */
	public List<String> beforeDeactivationCommands;

	/**
	 * Commands executed immediately after ModMode is deactivated.
	 */
	public List<String> afterDeactivationCommands;

	protected VanishPlugin vanish;
	protected TagAPI tagapi;

	/**
	 * Load the configuration.
	 */
	public void loadConfiguration() {
		reloadConfig();

		vanished = new TreeSet<String>(getConfig().getStringList("vanished"));
		modmode = getConfig().getStringList("modmode");
		allowFlight = getConfig().getBoolean("allow.flight", true);
		usingbperms = getConfig().getBoolean("bperms.enabled", false);
		bPermsModGroups = getConfig().getStringList("bperms.modgroup");

		if (bPermsModGroups.isEmpty()) {
			bPermsModGroups.add("Moderators");
		}

		groupMap = getConfig().getConfigurationSection("groupmap");
		if (groupMap == null) {
			getConfig().createSection("groupmap");
		}

		bPermsModModeGroup = getConfig().getString("bperms.modmodegroup", "ModMode");
		debugPlayerData = getConfig().getBoolean("debug.playerdata");

		beforeActivationCommands = getConfig().getStringList("commands.activate.before");
		afterActivationCommands = getConfig().getStringList("commands.activate.after");
		beforeDeactivationCommands = getConfig().getStringList("commands.deactivate.before");
		afterDeactivationCommands = getConfig().getStringList("commands.deactivate.after");
	}

	/**
	 * Save the configuration.
	 */
	public void saveConfiguration() {
		getConfig().set("vanished", new ArrayList<String>(vanished));
		getConfig().set("modmode", modmode);
		getConfig().set("allow.flight", allowFlight);
		getConfig().set("bperms.enabled", usingbperms);
		getConfig().set("bperms.modgroup", bPermsModGroups);
		getConfig().set("bperms.modmodegroup", bPermsModModeGroup);
		saveConfig();
	}

	/**
	 * Return true if the player is currently vanished.
	 * 
	 * @return true if the player is currently vanished.
	 */
	public boolean isVanished(Player player) {
		return vanish.getManager().isVanished(player);
	}

	/**
	 * Return the persistent (cross login) vanish state.
	 * 
	 * This means different things depending on whether the player could
	 * normally vanish without being in ModMode. See the doc comment for
	 * {@link #vanished}.
	 * 
	 * @param player the player.
	 * @return true if vanished.
	 */
	public boolean getPersistentVanishState(Player player) {
		return vanished.contains(player.getName());
	}

	/**
	 * Save the current vanish state of the player as his persistent vanish
	 * state.
	 * 
	 * This means different things depending on whether the player could
	 * normally vanish without being in ModMode. See the doc comment for
	 * {@link #vanished}.
	 */
	public void setPersistentVanishState(Player player) {
		if (vanish.getManager().isVanished(player)) {
			vanished.add(player.getName());
		} else {
			vanished.remove(player.getName());
		}
	}

	/**
	 * Return true if the player is currently in ModMode.
	 * 
	 * @param player the Player.
	 * @return true if the player is currently in ModMode.
	 */
	public boolean isModMode(Player player) {
		return modmode.contains(player.getName());
	}

	/**
	 * Return true if the player has Admin permissions.
	 * 
	 * That is, the player has permissions in excess of those of the ModMode
	 * permission group. This is a different concept from Permissions.ADMIN,
	 * which merely signifies that the player can administer this plugin.
	 * 
	 * @return true for Admins, false for Moderators and default players.
	 */
	public boolean isAdmin(Player player) {
		if (!player.hasPermission(Permissions.TOGGLE)) {
			// default player.
			return false;
		}

		String moderatorGroup = bPermsModGroups.get(0);
		World world = Bukkit.getWorlds().get(0);

		// If the player is currently in ModMode, check the groupmap for the
		// usual group of the player.
		if (isModMode(player)) {
			return !moderatorGroup.equals(groupMap.getString(player.getName()));
		} else {
			// Not in ModMode. Check for Moderator group.
			return !ApiLayer.hasGroup(world.getName(), CalculableType.USER, player.getName(), moderatorGroup);
		}
	}

	/**
	 * Set the vanish state of the player.
	 * 
	 * @param player the Player.
	 * @param vanished true if he should be vanished.
	 */
	public void setVanish(Player player, boolean vanished) {
		if (vanish.getManager().isVanished(player) != vanished) {
			vanish.getManager().toggleVanish(player);
		}
	}

	/**
	 * Try to coerce VanishNoPacket into showing or hiding players to each other
	 * based on their current vanish state and permissions.
	 * 
	 * Just calling resetSeeing() when a moderator toggles ModMode (and hence
	 * permissions and vanish state) is apparently insufficient.
	 */
	public void updateAllPlayersSeeing() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			vanish.getManager().playerRefresh(player);
		}
	}

	public void showVanishList(CommandSender sender) {
		String result = "";
		boolean first = true;
		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (vanish.getManager().isVanished(player)) {
				if (first) {
					first = false;
				} else {
					result += ", ";
				}
				result += player.getName();
			}
		}

		if (result.length() == 0) {
			sender.sendMessage(ChatColor.RED + "All players are visible!");
		} else {
			sender.sendMessage(ChatColor.RED + "Vanished players: " + result);
		}
	}

	public String getCleanModModeName(Player player) {
		return "modmode_" + player.getName();
	}

	/**
	 * Return the File used to store the player's normal or ModMode state.
	 * 
	 * @param player the player.
	 * @param isModMode true if the data is for the ModMode state.
	 * @return the File used to store the player's normal or ModMode state.
	 */
	public File getStateFile(Player player, boolean isModMode) {
		File playersDir = new File(getDataFolder(), "players");
		playersDir.mkdirs();

		String fileName = player.getName() + ((isModMode) ? "_modmode" : "_normal") + ".yml";
		return new File(playersDir, fileName);
	}

	/**
	 * Save the player's data to a YAML configuration file.
	 * 
	 * @param player the player.
	 * @param isModMode true if the saved data is for the ModMode inventory.
	 */
	public void savePlayerData(Player player, boolean isModMode) {
		File stateFile = getStateFile(player, isModMode);
		if (debugPlayerData) {
			getLogger().info("savePlayerData(): " + stateFile);
		}

		// Keep 2 backups of saved player data.
		try {
			File backup1 = new File(stateFile.getPath() + ".1");
			File backup2 = new File(stateFile.getPath() + ".2");
			backup1.renameTo(backup2);
			stateFile.renameTo(backup1);
		} catch (Exception ex) {
			getLogger().warning(ex.getClass().getName() + " raised saving state file backups for " + player.getName() + ".");
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
			getLogger().warning("Failed to save player data for " + player.getName());
		}
	}

	/**
	 * Load the player's data from a YAML configuration file.
	 * 
	 * @param player the player.
	 * @param isModMode true if the loaded data is for the ModMode inventory.
	 */
	public void loadPlayerData(Player player, boolean isModMode) {
		File stateFile = getStateFile(player, isModMode);
		if (debugPlayerData) {
			getLogger().info("loadPlayerData(): " + stateFile);
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
					}
				}
			}
		} catch (Exception ex) {
			getLogger().warning("Failed to load player data for " + player.getName());
		}
	}

	public void toggleModMode(final Player player, boolean enabled) {
		if (enabled) {
			runCommands(player, beforeActivationCommands);
		} else {
			runCommands(player, beforeDeactivationCommands);
		}

		if (!enabled) {
			// When leaving ModMode, Admins return to their persistent vanish
			// state; Moderators become visible
			if (isAdmin(player)) {
				setVanish(player, getPersistentVanishState(player));
			} else {
				setVanish(player, false);
			}

			if (usingbperms) {
				List<org.bukkit.World> worlds = getServer().getWorlds();
				for (org.bukkit.World world : worlds) {
					ApiLayer.removeGroup(world.getName(), CalculableType.USER, player.getName(), bPermsModModeGroup);

					// Players leaving ModMode are assumed to be mods if not
					// otherwise specified.
					String group = groupMap.getString(player.getName(), bPermsModGroups.get(0));
					if (!ApiLayer.hasGroup(world.getName(), CalculableType.USER, player.getName(), group)) {
						ApiLayer.addGroup(world.getName(), CalculableType.USER, player.getName(), group);
					}
				}
			}
			modmode.remove(player.getName());
			player.sendMessage(ChatColor.RED + "You are no longer in ModMode!");
		} else {
			if (usingbperms) {
				List<org.bukkit.World> worlds = getServer().getWorlds();
				for (org.bukkit.World world : worlds) {
					// Remove the player's normal top level permissions group to
					// account for negated permission nodes.
					for (String group : bPermsModGroups) {
						if (ApiLayer.hasGroup(world.getName(), CalculableType.USER, player.getName(), group)) {
							groupMap.set(player.getName(), group);
							ApiLayer.removeGroup(world.getName(), CalculableType.USER, player.getName(), group);
						}
					}

					ApiLayer.addGroup(world.getName(), CalculableType.USER, player.getName(), bPermsModModeGroup);
				}
			}
			if (!modmode.contains(player.getName())) {
				modmode.add(player.getName());
			}

			// Always vanish when entering ModMode. Record the old vanish state
			// for admins only.
			if (isAdmin(player)) {
				setPersistentVanishState(player);
			}
			setVanish(player, true);
			player.sendMessage(ChatColor.RED + "You are now in ModMode!");
		}

		// Update the permissions that VanishNoPacket caches to match the new
		// permissions of the player. This is highly dependent on this API
		// method not doing anything more than what it currently does:
		// to simply remove the cached VanishUser (permissions) object.
		VanishPerms.userQuit(player);

		// Update who sees whom AFTER permissions and vanish state changes.
		updateAllPlayersSeeing();

		// Save player data for the old ModMode state and load for the new.
		savePlayerData(player, !enabled);
		loadPlayerData(player, enabled);

		// Hopefully stop some minor falls
		player.setFallDistance(0F);

		// Chunk error (resend to all clients).
		World w = player.getWorld();
		Chunk c = w.getChunkAt(player.getLocation());
		w.refreshChunk(c.getX(), c.getZ());

		restoreFlight(player, enabled);
		saveConfiguration();

		if (enabled) {
			runCommands(player, afterActivationCommands);
		} else {
			runCommands(player, afterDeactivationCommands);
		}
	}

	/**
	 * Restore flight ability if in ModMode or creative game mode.
	 * 
	 * @param player the player.
	 * @param isInModMode true if the player is in ModMode.
	 */
	public void restoreFlight(Player player, boolean isInModMode) {
		player.setAllowFlight((isInModMode && allowFlight) || player.getGameMode() == GameMode.CREATIVE);
	}

	@Override
	public void onEnable() {
		// Save sane defaults when config.yml is deleted.
		saveDefaultConfig();
		loadConfiguration();

		vanish = (VanishPlugin) getServer().getPluginManager().getPlugin("VanishNoPacket");
		if (vanish == null) {
			getLogger().severe("VanishNoPacket required. Download it here http://dev.bukkit.org/server-mods/vanish/");
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

		Plugin logBlock = getServer().getPluginManager().getPlugin("LogBlock");
		if (logBlock != null && !logBlock.isEnabled()) {
			getPluginLoader().enablePlugin(logBlock);
		}

		if (!getServer().getPluginManager().isPluginEnabled("TagAPI")) {
			getLogger().info("TagAPI is required for coloured names while in ModMode.");
			getLogger().info("http://dev.bukkit.org/server-mods/tag/ ");
		}

		if (usingbperms) {
			de.bananaco.bpermissions.imp.Permissions bPermsPlugin = null;

			bPermsPlugin = (de.bananaco.bpermissions.imp.Permissions) getServer().getPluginManager().getPlugin("bPermissions");
			if (bPermsPlugin == null || !(bPermsPlugin instanceof de.bananaco.bpermissions.imp.Permissions)) {
				if (!bPermsPlugin.isEnabled()) {
					getPluginLoader().enablePlugin(bPermsPlugin);
				}
				getLogger().log(Level.INFO, "bperms turned on, but plugin could not be loaded.");
				getPluginLoader().disablePlugin(this);
				return;
			}
		}

		getServer().getPluginManager().registerEvents(listener, this);
	} // onEnable

	@Override
	public void onDisable() {
		saveConfiguration();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
		if (command.getName().equalsIgnoreCase("vanishlist")) {
			showVanishList(sender);
			return true;
		}

		if (!(sender instanceof Player)) {
			return false;
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
			}
		} else if (command.getName().equalsIgnoreCase("unvanish")) {
			if (isVanished(player)) {
				setVanish(player, false);
			} else {
				player.sendMessage(ChatColor.DARK_AQUA + "You are already visible.");
			}
		} else if (command.getName().equalsIgnoreCase("modmode")) {
			if (args.length == 0) {
				if (modmode.remove(player.getName())) {
					toggleModMode(player, false);
				} else {
					modmode.add(player.getName());
					toggleModMode(player, true);
				}
				return true;

			} else if (args.length == 1) {
				// Permissions check on /modmode admin commands.
				if (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("reload")) {
					if (!player.hasPermission(Permissions.ADMIN)) {
						player.sendMessage(ChatColor.RED + "You don't have permission to use /modmode admin commands.");
						return true;
					}
				}

				if (args[0].equalsIgnoreCase("save")) {
					saveConfiguration();
					player.sendMessage(ChatColor.GOLD + "ModMode configuration saved.");
					return true;
				} else if (args[0].equalsIgnoreCase("reload")) {
					loadConfiguration();
					player.sendMessage(ChatColor.GOLD + "ModMode configuration reloaded.");
					return true;
				}
			}

			// Fall through case for unrecognised arguments to /modmode.
			player.sendMessage(ChatColor.RED + "Usage: /modmode [save | reload]");
		}

		return true;
	} // onCommand

	/**
	 * Run all of the commands in the List of Strings.
	 * 
	 * @param player the moderator causing the commands to run.
	 * @param commands the commands to run.
	 */
	public void runCommands(Player player, List<String> commands) {
		for (String command : commands) {
			// dispatchCommand() doesn't cope with a leading '/' in commands.
			if (command.length() > 0 && command.charAt(0) == '/') {
				command = command.substring(1);
			}
			try {
				if (!Bukkit.getServer().dispatchCommand(player, command)) {
					getLogger().warning("Command \"" + command + "\" could not be executed.");
				}
			} catch (Exception ex) {
				getLogger().severe("Command \"" + command + "\" raised " + ex.getClass().getName());
			}
		}
	}
}
