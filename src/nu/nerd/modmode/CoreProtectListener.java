package nu.nerd.modmode;

import net.coreprotect.event.CoreProtectPreLogEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

// ----------------------------------------------------------------------------
/**
 * The CoreProtect event-handling class.
 */
public class CoreProtectListener implements Listener {

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    CoreProtectListener() {
        ModMode.PLUGIN.getServer().getPluginManager().registerEvents(this, ModMode.PLUGIN);
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
            player = ModMode.PLUGIN.getServer().getPlayer(event.getUser());
        } catch (Exception e) {
            // probably liquid flow or something
            return;
        }
        if (player != null && ModMode.PLUGIN.isModMode(player)) {
            String user = ModMode.PLUGIN.getCleanModModeName(player);
            event.setUser(user);
        }
    }

} // CoreProtectListener
