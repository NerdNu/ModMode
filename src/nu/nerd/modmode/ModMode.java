package nu.nerd.modmode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;

import net.minecraft.server.v1_6_R2.EntityPlayer;
import net.minecraft.server.v1_6_R2.NBTCompressedStreamTools;
import net.minecraft.server.v1_6_R2.NBTTagCompound;
import net.minecraft.server.v1_6_R2.WorldNBTStorage;
import net.minecraft.server.v1_6_R2.WorldServer;

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
import org.bukkit.craftbukkit.v1_6_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
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
	private String playerDir;
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
		return modmode.contains(player.getDisplayName());
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
	 * Save the player's data to different files for ModMode and normal player
	 * mode.
	 * 
	 * See
	 * {@link ModModeListener#onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent)}
	 * for the rationale of why we can't trust playername.dat to store the
	 * player's inventory in all circumstances.
	 * 
	 * @param entityhuman the NMS player.
	 * @param player the player.
	 * @param isModMode true if the saved data is for the ModMode inventory.
	 */
	public void savePlayerData(EntityPlayer entityhuman, Player player, boolean isModMode) {
		try {
			String fullName = ((isModMode) ? "modmode_" : "normal_") + player.getName();
			if (debugPlayerData) {
				getLogger().info("savePlayerData(): " + fullName);
			}
			NBTTagCompound nbttagcompound = new NBTTagCompound();
			entityhuman.e(nbttagcompound);

			File file1 = new File(playerDir, fullName + ".dat.tmp");
			File file2 = new File(playerDir, fullName + ".dat");

			NBTCompressedStreamTools.a(nbttagcompound, new FileOutputStream(file1));
			if (file2.exists()) {
				file2.delete();
			}

			file1.renameTo(file2);
		} catch (Exception exception) {
			getLogger().warning("Failed to save player data for " + entityhuman.getName());
		}
	}

	/**
	 * Load the player's data from different files for ModMode and normal player
	 * mode.
	 * 
	 * @param entityhuman the NMS player.
	 * @param player the player.
	 * @param isModMode true if the loaded data is for the ModMode inventory.
	 */
	public void loadPlayerData(EntityPlayer entityhuman, Player player, boolean isModMode) {
		String fullName = ((isModMode) ? "modmode_" : "normal_") + player.getName();
		if (debugPlayerData) {
			getLogger().info("loadPlayerData(): " + fullName);
		}
		WorldServer worldServer = entityhuman.server.getWorldServer(0);
		WorldNBTStorage playerFileData = (WorldNBTStorage) worldServer.getDataManager().getPlayerFileData();
		NBTTagCompound nbttagcompound = playerFileData.getPlayerData(fullName);
		if (nbttagcompound != null) {
			entityhuman.f(nbttagcompound);
		} else {
			if (debugPlayerData) {
				getLogger().info("loadPlayerData(): no player data for: " + fullName);
			}
		}
	}

	public void toggleModMode(final Player player, boolean enabled, boolean onJoin) {
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

		Location loc = player.getLocation();
		final EntityPlayer entityplayer = ((CraftPlayer) player).getHandle();

		// Save player data for the old ModMode state and load for the new.
		// Clear potions in between so only those in the loaded file apply.
		savePlayerData(entityplayer, player, !enabled);
		for (PotionEffect potion : player.getActivePotionEffects()) {
			player.removePotionEffect(potion.getType());
		}
		loadPlayerData(entityplayer, player, enabled);

		// Teleport to avoid speedhack
		if (!enabled || onJoin) {
			loc = new Location(entityplayer.world.getWorld(), entityplayer.locX, entityplayer.locY, entityplayer.locZ, entityplayer.yaw,
			entityplayer.pitch);
		}
		player.teleport(loc);

		// Hopefully stop some minor falls
		player.setFallDistance(0F);

		// Chunk error ( resend to all clients )
		World w = player.getWorld();
		Chunk c = w.getChunkAt(player.getLocation());
		w.refreshChunk(c.getX(), c.getZ());

		// Visibility changes need to occur after the modmode list is updated.
		if (enabled) {
			if (willBeVanishedInModMode(player)) {
				enableVanish(player);
			}
		} else {
			disableVanish(player);
		}

		if (allowFlight || player.getGameMode() == GameMode.CREATIVE) {
			player.setAllowFlight(enabled);
		}
		saveConfig();
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
		File worldDir = new File(Bukkit.getServer().getWorldContainer(), worldname);
		playerDir = new File(worldDir, "players").getAbsolutePath();
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
			if (modmode.remove(player.getDisplayName())) {
				toggleModMode(player, false, false);
			} else {
				modmode.add(player.getDisplayName());
				toggleModMode(player, true, false);
			}
		}

		return true;
	}
}
