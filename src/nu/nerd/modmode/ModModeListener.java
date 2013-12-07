package nu.nerd.modmode;

import org.bukkit.ChatColor;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.kitteh.tag.PlayerReceiveNameTagEvent;

import de.diddiz.LogBlock.events.BlockChangePreLogEvent;

public class ModModeListener implements Listener {
	final ModMode plugin;

	ModModeListener(ModMode instance) {
		plugin = instance;
	}

	@EventHandler
	public void onLogBlockPreLogEvent(BlockChangePreLogEvent event) {
		Player p = plugin.getServer().getPlayerExact(event.getOwner());
		if (p != null && plugin.isModMode(p)) {
			event.setOwner(plugin.getCleanModModeName(p));
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onNameTag(PlayerReceiveNameTagEvent event) {
		if (plugin.isModMode(event.getNamedPlayer())) {
			event.setTag(ChatColor.GREEN + event.getNamedPlayer().getName() + ChatColor.WHITE);
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		// Is the player a moderator or admin?
		if (player.hasPermission(Permissions.TOGGLE)) {
			boolean inModMode = plugin.isModMode(player);
			boolean vanished = false;

			if (plugin.isAdmin(player)) {
				// Admins log in vanished if they logged out vanished, or if
				// they logged out in ModMode (vanished or not).
				vanished = plugin.getPersistentVanishState(player) || inModMode;
			} else {
				// Moderators log in vanished if they must have logged out in
				// ModMode while vanished.
				vanished = inModMode && plugin.getPersistentVanishState(player);
			}

			if (vanished) {
				plugin.setVanish(player, true);
				event.setJoinMessage(null);
			}
			plugin.restoreFlight(player, inModMode);
		}
		plugin.updateAllPlayersSeeing();
	}

	/**
	 * In order for plugin.isVanished() to return the correct result, this event
	 * must be processed before VanishNoPacket handles it, hence the low
	 * priority.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		// Suppress quit messages when vanished.
		if (plugin.isVanished(player)) {
			event.setQuitMessage(null);
		}

		// For staff who can use ModMode, store the vanish state of Moderators
		// in ModMode and Admins who are not in ModMode between logins.
		if (player.hasPermission(Permissions.TOGGLE) &&
			plugin.isModMode(player) != plugin.isAdmin(player)) {
			plugin.setPersistentVanishState(player);
			plugin.saveConfiguration();
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		if (plugin.isVanished(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		if (plugin.isVanished(event.getPlayer()) || plugin.isModMode(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler
	public void onEntityTarget(EntityTargetEvent event) {
		if (!(event.getTarget() instanceof Player))
			return;

		Player player = (Player) event.getTarget();
		if (plugin.isModMode(player) || plugin.isVanished(player))
			event.setCancelled(true);
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		// block PVP with a message
		if (event instanceof EntityDamageByEntityEvent) {
			EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
			if (e.getDamager() instanceof Player && e.getEntity() instanceof Player) {
				Player damager = (Player) e.getDamager();
				Player victim = (Player) e.getEntity();
				if (plugin.isModMode(damager) || plugin.isVanished(damager)) {
					event.setCancelled(true);
				}
				// only show message if they aren't invisible
				else if (plugin.isModMode(victim) && !plugin.isVanished(victim)) {
					damager.sendMessage("This moderator is in ModMode.");
					damager.sendMessage("ModMode should only be used for official server business.");
					damager.sendMessage("Please let an admin know if a moderator is abusing ModMode.");
				}
			}
		}

		// block all damage to invisible and modmode players
		if (event.getEntity() instanceof Player) {
			Player victim = (Player) event.getEntity();
			if (plugin.isModMode(victim) || plugin.isVanished(victim)) {
				// Extinguish view-obscuring fires.
				victim.setFireTicks(0);
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
		if (e.getPlayer().getGameMode() == GameMode.CREATIVE)
			return;

		if (plugin.allowFlight) {
			e.getPlayer().setAllowFlight(plugin.isModMode(e.getPlayer()));
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			if (plugin.isModMode(player)) {
				if (player.getFoodLevel() != 20) {
					player.setFoodLevel(20);
				}
				event.setCancelled(true);
			}
		}
	}
}
