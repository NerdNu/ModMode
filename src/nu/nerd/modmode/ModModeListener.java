package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;

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
        NerdBoardHook.reconcilePlayerWithVanishState(player);
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

} // ModModeListener