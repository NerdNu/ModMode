package nu.nerd.modmode;

import static nu.nerd.modmode.ModMode.CONFIG;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
            if (ModMode.PERMISSIONS.isAdmin(player)) {
                // Admins log in vanished if they logged out vanished, or if
                // they logged out in ModMode (vanished or not).
                vanished = CONFIG.loggedOutVanished(player) || inModMode;
            } else {
                // Moderators log in vanished if they must have logged out in
                // ModMode while vanished.
                vanished = inModMode && CONFIG.loggedOutVanished(player);
            }

            if (vanished) {
                ModMode.PLUGIN.setVanish(player, true);
                CONFIG.SUPPRESSED_LOGIN_MESSAGE.put(player.getUniqueId(), event.getJoinMessage());
                event.setJoinMessage(null);
            }

            ModMode.PLUGIN.restoreFlight(player, inModMode);

            // Player logged out while permissions were updating. Finish the
            // ModMode transition.
            if (CONFIG.MODMODE_PENDING.contains(player)) {
                ModMode.PLUGIN.finishToggleModMode(player, inModMode);
            }
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
        CONFIG.SUPPRESSED_LOGIN_MESSAGE.remove(player.getUniqueId());

        // Suppress quit messages when vanished.
        if (ModMode.PLUGIN.isVanished(player)) {
            event.setQuitMessage(null);
        }

        // For staff who can use ModMode, store the vanish state of Moderators
        // in ModMode and Admins who are not in ModMode between logins.
        if (player.hasPermission(Permissions.TOGGLE) && ModMode.PLUGIN.isModMode(player) != ModMode.PERMISSIONS.isAdmin(player)) {
            ModMode.PLUGIN.setPersistentVanishState(player);
            CONFIG.save();
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Disallows vanished players from picking up items.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (ModMode.PLUGIN.isVanished(player)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Disallows vanished players and players in ModMode from dropping items.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (ModMode.PLUGIN.isVanished(event.getPlayer()) || ModMode.PLUGIN.isModMode(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Disallows entities from targeting vanished players and players in
     * ModMode, e.g. hostile mobs, parrots.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getTarget();
        if (ModMode.PLUGIN.isModMode(player) || ModMode.PLUGIN.isVanished(player)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Disallows vanished players and players in ModMode from damaging other
     * players.
     * 
     * Vanished or ModMode staff do not take damage or acquire fire ticks.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();

        // Block PVP with a message.
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.getDamager() instanceof Player) {
                Player damager = (Player) e.getDamager();

                // Prevent staff from doing PvP damage.
                if (ModMode.PLUGIN.isModMode(damager) || ModMode.PLUGIN.isVanished(damager)) {
                    event.setCancelled(true);
                }

                // Only show message if staff are visible.
                else if (ModMode.PLUGIN.isModMode(victim) && !ModMode.PLUGIN.isVanished(victim)) {
                    damager.sendMessage("This moderator is in ModMode.");
                    damager.sendMessage("ModMode should only be used for official server business.");
                    damager.sendMessage("Please let an admin know if a moderator is abusing ModMode.");
                }
            }
        }

        // Block all damage to invisible and modmode players.
        if (ModMode.PLUGIN.isModMode(victim) || ModMode.PLUGIN.isVanished(victim)) {
            // Extinguish view-obscuring fires.
            victim.setFireTicks(0);
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Updates the player's WorldeditCache and allow-flight status upon changing
     * worlds.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (CONFIG.ALLOW_FLIGHT) {
                boolean flightState = ModMode.PLUGIN.isModMode(player);
                player.setAllowFlight(flightState);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Restores a player's flight ability upon changing game modes.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(ModMode.PLUGIN, () -> {
            boolean flightState = ModMode.PLUGIN.isModMode(player);
            ModMode.PLUGIN.restoreFlight(player, flightState);
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
