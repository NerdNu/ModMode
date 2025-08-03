package nu.nerd.modmode.listeners;

import net.coreprotect.event.CoreProtectPreLogEvent;
import nu.nerd.modmode.ModMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

// ----------------------------------------------------------------------------

/**
 * The CoreProtect event-handling class.
 */
public class CoreProtectListener implements Listener {

    private ModMode plugin;

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    public CoreProtectListener(ModMode plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------------------
    /**
     * Ensures edits made by players in ModMode are logged with that player's
     * ModMode name.
     */
    @EventHandler
    public void onLogBlockPreLogEvent(CoreProtectPreLogEvent event) {
        Player player;
        try {
            player = plugin.getServer().getPlayer(event.getUser());
        } catch (Exception e) {
            // probably liquid flow or something
            return;
        }
        if (player != null && plugin.isInMode(player)) {
            String user = plugin.getCleanModeName(player);
            event.setUser(user);
        }
    }

} // CoreProtectListener
