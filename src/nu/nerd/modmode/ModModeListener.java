package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
        if (Permissions.canModMode(player)) {
            boolean isAdmin = Permissions.isAdmin(player);
            boolean inModMode = ModMode.PLUGIN.isModMode(player);
            boolean loggedOutVanished = ModMode.CONFIG.loggedOutVanished(player);
            boolean vanished = isAdmin ? loggedOutVanished : inModMode && loggedOutVanished;
            if (vanished) {
                ModMode.CONFIG.suppressJoinMessage(player, event.getJoinMessage());
                event.setJoinMessage(null);
                ModMode.PLUGIN.setVanished(player, true);
            }
            ModMode.PLUGIN.restoreFlight(player, inModMode);
        }
        ModMode.NERDBOARD.reconcilePlayerWithVanishState(player);
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
        CONFIG.getJoinMessage(player); // removes it
        boolean isVanished = ModMode.PLUGIN.isVanished(player);
        if (isVanished) {
            event.setQuitMessage(null);
            boolean inModMode = ModMode.PLUGIN.isModMode(player);
            boolean isAdmin = player.hasPermission(Permissions.ADMIN);
            if (isAdmin || inModMode) {
                CONFIG.setLoggedOutVanished(player, true);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Disallows vanished players and players in ModMode from dropping items.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (ModMode.PLUGIN.isTranscendental(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Disallows entities from targeting vanished players and players in
     * ModMode, e.g. hostile mobs, parrots.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player) {
            Player player = (Player) e.getTarget();
            if (ModMode.PLUGIN.isTranscendental(player)) {
                e.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Disallows vanished players and players in ModMode from damaging other
     * players.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (ModMode.PLUGIN.isTranscendental(victim)) {
                // Extinguish view-obscuring fires.
                victim.setFireTicks(0);
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
