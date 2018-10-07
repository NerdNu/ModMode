package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static nu.nerd.modmode.ModMode.CONFIG;

// ------------------------------------------------------------------------
/**
 * The plugin's main event-handling class.
 */
public class ModModeListener implements Listener {

	// ------------------------------------------------------------------------
	/**
	 * Constructor.
	 */
	ModModeListener() {
		Bukkit.getPluginManager().registerEvents(this, ModMode.PLUGIN);
	}

	// ------------------------------------------------------------------------
	/**
	 * Facilitates the persistence of ModMode state across logins.
	 */
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		// Is the player a moderator or admin?
		if (player.hasPermission(Permissions.TOGGLE)) {
			boolean inModMode = ModMode.PLUGIN.isModMode(player);
			boolean vanished;
			if (ModMode.getPermissions().isAdmin(player)) {
				// Admins log in vanished if they logged out vanished, or if
				// they logged out in ModMode (vanished or not).
				vanished = ModMode.PLUGIN.getPersistentVanishState(player) || inModMode;
			} else {
				// Moderators log in vanished if they must have logged out in
				// ModMode while vanished.
				vanished = inModMode && ModMode.PLUGIN.getPersistentVanishState(player);
			}

			if (vanished) {
				ModMode.PLUGIN.setVanish(player, true);
				CONFIG.joinedVanished.put(player.getUniqueId().toString(), event.getJoinMessage());
				event.setJoinMessage(null);
			}

			ModMode.PLUGIN.restoreFlight(player, inModMode);
		}

		NerdBoardHook.reconcilePlayerWithVanishState(player);
		ModMode.PLUGIN.updateAllPlayersSeeing();
	}

	// ------------------------------------------------------------------------
	/**
	 * In order for ModMode.PLUGIN.isVanished() to return the correct result,
	 * this event must be processed before VanishNoPacket handles it, hence the
	 * low priority.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		CONFIG.joinedVanished.remove(player.getUniqueId().toString());

		// Suppress quit messages when vanished.
		if (ModMode.PLUGIN.isVanished(player)) {
			event.setQuitMessage(null);
		}

		// For staff who can use ModMode, store the vanish state of Moderators
		// in ModMode and Admins who are not in ModMode between logins.
		if (player.hasPermission(Permissions.TOGGLE) &&
			ModMode.PLUGIN.isModMode(player) != ModMode.getPermissions().isAdmin(player)) {
			ModMode.PLUGIN.setPersistentVanishState(player);
			CONFIG.save();
		}
	}

	// ------------------------------------------------------------------------
	/**
	 * Disallows vanished players from picking up items.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		if (ModMode.PLUGIN.isVanished(event.getPlayer()))
			event.setCancelled(true);
	}

	// ------------------------------------------------------------------------
	/**
	 * Disallows vanished players and players in ModMode from dropping items.
	 */
	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		if (ModMode.PLUGIN.isVanished(event.getPlayer()) || ModMode.PLUGIN.isModMode(event.getPlayer()))
			event.setCancelled(true);
	}

	// ------------------------------------------------------------------------
	/**
	 * Disallows entities from targeting vanished players and players in
	 * ModMode, e.g. hostile mobs, parrots.
	 */
	@EventHandler
	public void onEntityTarget(EntityTargetEvent event) {
		if (!(event.getTarget() instanceof Player))
			return;

		Player player = (Player) event.getTarget();
		if (ModMode.PLUGIN.isModMode(player) || ModMode.PLUGIN.isVanished(player))
			event.setCancelled(true);
	}

	// ------------------------------------------------------------------------
	/**
	 * Disallows vanished players and players in ModMode from damaging other
	 * players.
	 */
	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		// block PVP with a message
		if (event instanceof EntityDamageByEntityEvent) {
			EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
			if (e.getDamager() instanceof Player && e.getEntity() instanceof Player) {
				Player damager = (Player) e.getDamager();
				Player victim = (Player) e.getEntity();
				if (ModMode.PLUGIN.isModMode(damager) || ModMode.PLUGIN.isVanished(damager)) {
					event.setCancelled(true);
				}
				// only show message if they aren't invisible
				else if (ModMode.PLUGIN.isModMode(victim) && !ModMode.PLUGIN.isVanished(victim)) {
					damager.sendMessage("This moderator is in ModMode.");
					damager.sendMessage("ModMode should only be used for official server business.");
					damager.sendMessage("Please let an admin know if a moderator is abusing ModMode.");
				}
			}
		}

		// block all damage to invisible and modmode players
		if (event.getEntity() instanceof Player) {
			Player victim = (Player) event.getEntity();
			if (ModMode.PLUGIN.isModMode(victim) || ModMode.PLUGIN.isVanished(victim)) {
				// Extinguish view-obscuring fires.
				victim.setFireTicks(0);
				event.setCancelled(true);
			}
		}
	}

	// ------------------------------------------------------------------------
	/**
	 * Updates the player's WorldeditCache and allow-flight status upon
	 * changing worlds.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
		
		final Player player = event.getPlayer();
		if (player.hasPermission(Permissions.TOGGLE)) {
			// update the player WorldeditCache after a necessary delay
			Bukkit.getScheduler().runTaskLater(ModMode.PLUGIN, () -> {
				ModMode.refreshWorldeditRegionsCache(player);
			}, 10);
		}

		if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
			if (CONFIG.allowFlight) {
				boolean flightState = ModMode.PLUGIN.isModMode(event.getPlayer());
				event.getPlayer().setAllowFlight(flightState);
			}
		}
	}

	// ------------------------------------------------------------------------
	/**
	 * Restores a player's flight ability upon changing game modes.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {
		Bukkit.getScheduler().runTask(ModMode.PLUGIN, () -> {
			boolean flightState = ModMode.PLUGIN.isModMode(event.getPlayer());
			ModMode.PLUGIN.restoreFlight(event.getPlayer(), flightState);
		});
	}

	// ------------------------------------------------------------------------
	/**
	 * Prevents the depletion of hunger level for players in ModMode.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			if (ModMode.PLUGIN.isModMode(player)) {
				if (player.getFoodLevel() != 20) {
					player.setFoodLevel(20);
				}
				event.setCancelled(true);
			}
		}
	}

} // ModModeListener
