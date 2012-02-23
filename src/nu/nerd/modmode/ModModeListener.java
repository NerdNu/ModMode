package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class ModModeListener implements Listener {
    private final ModMode plugin;

    public class ModModeRunnable implements Runnable {
        private final Player player;

        public ModModeRunnable(Player foo) {
            player = foo;
        }

        public void run() {
            plugin.toggleModMode(player, true, true);
        }
    }


    ModModeListener(ModMode instance) {
        plugin = instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.modmode.contains(player.getDisplayName()) && !player.getName().startsWith("\u00A7")) {
            event.setJoinMessage(null);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new ModModeRunnable(player));
        }

        if (plugin.vanished.contains(player.getName()))
            plugin.enableVanish(player);
        if (plugin.fullvanished.contains(player.getName()))
            plugin.enableFullVanish(player);

        plugin.updateVanishLists(player);

        // send our own join message only to people who can see the player
        if (plugin.isInvisible(player) && event.getJoinMessage() != null) {
            String message = event.getJoinMessage();
            event.setJoinMessage(null);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.canSee(player)) {
                    player.sendMessage(message);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // send our own quit message only to people who can see the player
        Player player = event.getPlayer();
        if (plugin.isInvisible(player) && event.getQuitMessage() != null) {
            String message = event.getQuitMessage();
            event.setQuitMessage(null);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.canSee(player)) {
                    player.sendMessage(message);
                }
            }
        }

        //make sure we're visible to everyone when leaving (hack, CB update fixes it)
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (plugin.isInvisible(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.isInvisible(event.getPlayer()))
            event.setCancelled(true);
        if (plugin.isModMode(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player))
            return;

        Player player = (Player) event.getTarget();
        if (plugin.isModMode(player) || plugin.isInvisible(player))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // block PVP with a message
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.getDamager() instanceof Player && e.getEntity() instanceof Player) {
                Player damager = (Player) e.getDamager();
                Player victim = (Player) e.getEntity();
                if (plugin.isModMode(damager) || plugin.isInvisible(damager)) {
                    event.setCancelled(true);
                }
                // only show message if they aren't invisible
                else if (plugin.isModMode(victim) && !plugin.isInvisible(victim)) {
                    damager.sendMessage("This moderator is in ModMode.");
                    damager.sendMessage("ModMode should only be used for official server business.");
                    damager.sendMessage("Please let an admin know if a moderator is abusing ModMode.");
                }
            }
        }

        // block all damage to invisible and modmode players
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (plugin.isModMode(victim) || plugin.isInvisible(victim)) {
                event.setCancelled(true);
            }
        }
    }
}
