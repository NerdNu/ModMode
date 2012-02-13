package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;


public class ModModeListener implements Listener {
    private final ModMode plugin;

    public ModModeListener(ModMode instance) {
        plugin = instance;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        // block PVP specifically
        if (e instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) e;
            if ((event.getDamager() instanceof Player) && (event.getEntity() instanceof Player)) {
                Player damager = (Player) event.getDamager();
                Player damagee = (Player) event.getEntity();
                if (plugin.isPlayerModMode(damager) || plugin.isPlayerInvisible(damager)) {
                    event.setCancelled(true);
                } else if (plugin.isPlayerModMode(damagee) && !plugin.isPlayerInvisible(damagee)) {
                    damager.sendMessage("This mod is in modmode.");
                    damager.sendMessage("Modmode should only be used for official server business.");
                    damager.sendMessage("Please let an admin know if a mod is abusing modmode.");
                    event.setCancelled(true);
                }
            }
        }

        // block all damage to invisible people and people in mod mode
        if (e.getEntity() instanceof Player) {
            Player damagee = (Player) e.getEntity();
            if (plugin.isPlayerModMode(damagee) || plugin.isPlayerInvisible(damagee)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChangeEvent(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) e.getEntity();

        if (!plugin.isPlayerInvisible(player)) {
            return;
        }

        if (!plugin.isPlayerModMode(player)) {
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player)) {
            return;
        }

        Player player = (Player) e.getTarget();

        if (!plugin.isPlayerInvisible(player)) {
            return;
        }

        if (!plugin.isPlayerModMode(player)) {
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        vanishForJoinOrRespawn(event.getPlayer());
        if (plugin.isPlayerInvisible(event.getPlayer())) {
            // send our own message only to people who can see the player
            String message = event.getJoinMessage();
            event.setJoinMessage(null);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.canSee(event.getPlayer())) {
                    player.sendRawMessage(message);
                }
            }
        }

        if (plugin.isPlayerModMode(event.getPlayer())) {
            plugin.modmode.remove(event.getPlayer().getDisplayName());
        }

        for (String name : plugin.invisible) {
            Player player = plugin.getServer().getPlayerExact(name);
            if (player != null && !plugin.shouldSee(event.getPlayer(), player)) {
                event.getPlayer().hidePlayer(player);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.isPlayerInvisible(event.getPlayer())) {
            // send our own message only to people who can see the player
            String message = event.getQuitMessage();
            event.setQuitMessage(null);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.canSee(event.getPlayer())) {
                    player.sendRawMessage(message);
                }
            }

            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        vanishForJoinOrRespawn(event.getPlayer());
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();

        if (plugin.isPlayerInvisible(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.isPlayerModMode(event.getPlayer()) || plugin.isPlayerInvisible(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private void vanishForJoinOrRespawn(Player player) {
        if (plugin.isPlayerInvisible(player)) {
            plugin.enableVanish(player);
        }
    }
}
