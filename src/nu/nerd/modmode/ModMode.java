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
import org.kitteh.vanish.VanishPlugin;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.util.CalculableType;

public class ModMode extends JavaPlugin {

	private final ModModeListener listener = new ModModeListener(this);

	/**
	 * This is the set of moderators who should be re-vanished when they enter
	 * ModMode. It is NOT the set of currently vanished players.
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

	private String worldname;
	protected VanishPlugin vanish;
	protected TagAPI tagapi;

	/**
	 * Return true if the player is currently vanished.
	 * 
	 * @return true if the player is currently vanished.
	 */
	public boolean isVanished(Player player) {
		return vanish.getManager().isVanished(player);
	}

	/**
	 * Return true if the player should be vanished when he has permission (is
	 * an admin or is in ModMode).
	 * 
	 * @param player the player.
	 * @return true if should be vanished when next in ModMode, or is an admin.
	 */
	public boolean willBeVanishedInModMode(Player player) {
		return vanished.contains(player.getName());
	}

	public boolean isModMode(Player player) {
		return modmode.contains(player.getName());
	}

	public void enableVanish(Player player) {
		if (!vanish.getManager().isVanished(player)) {
			vanish.getManager().toggleVanish(player);
		}
	}

	public void disableVanish(Player player) {
		if (vanish.getManager().isVanished(player)) {
			vanish.getManager().toggleVanish(player);
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
			ex.printStackTrace();
		}
	}

	public void toggleModMode(final Player player, boolean enabled) {
		if (!enabled) {
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
			player.sendMessage(ChatColor.RED + "You are now in ModMode!");
		}

		// Save player data for the old ModMode state and load for the new.
		savePlayerData(player, !enabled);
		loadPlayerData(player, enabled);

		// Hopefully stop some minor falls
		player.setFallDistance(0F);

		// Chunk error (resend to all clients).
		World w = player.getWorld();
		Chunk c = w.getChunkAt(player.getLocation());
		w.refreshChunk(c.getX(), c.getZ());

		restoreTransientState(player, enabled);
		saveConfig();
	}

	/**
	 * Restore some player attributes that are affected by ModMode but not
	 * stored.
	 * 
	 * The attributes can be computed by the player current ModMode status.
	 * 
	 * @param player the player.
	 * @param isInModMode true if the player is in ModMode.
	 */
	public void restoreTransientState(Player player, boolean isInModMode) {
		// Visibility changes need to occur after the modmode list is updated.
		if (isInModMode) {
			if (willBeVanishedInModMode(player)) {
				enableVanish(player);
			}
		} else {
			disableVanish(player);
		}

		player.setAllowFlight((isInModMode && allowFlight) || player.getGameMode() == GameMode.CREATIVE);
	}

	public void updateVanishLists(Player player) {
		vanish.getManager().resetSeeing(player);
	}

	@Override
	public void onEnable() {
		// Save sane defaults when config.yml is deleted.
		saveDefaultConfig();
		reloadConfig();

		vanish = (VanishPlugin) getServer().getPluginManager().getPlugin("VanishNoPacket");
		if (vanish == null) {
			getLogger().severe("VanishNoPacket required. Download it here http://dev.bukkit.org/server-mods/vanish/");
			getPluginLoader().disablePlugin(this);
			return;
		} else {
			// Make sure that VanishNoPacket is enabled. Spigot doesn't seem
			// to treat harddepend as mandating a load order.
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

		getServer().getPluginManager().registerEvents(listener, this);
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
		worldname = getConfig().getString("worldname", "world");
		debugPlayerData = getConfig().getBoolean("debug.playerdata");

		if (usingbperms) {
			de.bananaco.bpermissions.imp.Permissions bPermsPlugin = null;

			bPermsPlugin = (de.bananaco.bpermissions.imp.Permissions) getServer().getPluginManager().getPlugin("bPermissions");
			if (bPermsPlugin == null || !(bPermsPlugin instanceof de.bananaco.bpermissions.imp.Permissions)) {
				if (!bPermsPlugin.isEnabled()) {
					getPluginLoader().enablePlugin(bPermsPlugin);
				}
				getLogger().log(Level.INFO, "bperms turned on, but plugin could not be loaded.");
				getPluginLoader().disablePlugin(this);
			}
		}
	}

	@Override
	public void onDisable() {
		getConfig().set("vanished", new ArrayList<String>(vanished));
		getConfig().set("modmode", modmode);
		getConfig().set("allow.flight", allowFlight);
		getConfig().set("bperms.enabled", usingbperms);
		getConfig().set("bperms.modgroup", bPermsModGroups);
		getConfig().set("bperms.modmodegroup", bPermsModModeGroup);
		getConfig().set("worldname", worldname);
		saveConfig();
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
				vanished.add(player.getName());
				enableVanish(player);
			}
		} else if (command.getName().equalsIgnoreCase("unvanish")) {
			if (isVanished(player)) {
				vanished.remove(player.getName());
				disableVanish(player);
			} else {
				player.sendMessage(ChatColor.DARK_AQUA + "You are already visible.");
			}
		} else if (command.getName().equalsIgnoreCase("modmode")) {
			if (modmode.remove(player.getName())) {
				toggleModMode(player, false);
			} else {
				modmode.add(player.getName());
				toggleModMode(player, true);
			}
		}

		return true;
	}
}
