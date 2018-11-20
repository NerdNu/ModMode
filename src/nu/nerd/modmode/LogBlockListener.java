package nu.nerd.modmode;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.events.BlockChangePreLogEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

// ------------------------------------------------------------------------
/**
 * The LogBlock event-handling class.
 */
public class LogBlockListener implements Listener {

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    LogBlockListener() { }

    // ------------------------------------------------------------------------
    /**
     * Ensures edits made by players in ModMode are logged with that player's
     * ModMode name.
     */
    @EventHandler
    public void onLogBlockPreLogEvent(BlockChangePreLogEvent event) {
        UUID uuid = UUID.fromString(event.getOwnerActor().getUUID());
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && ModMode.PLUGIN.isModMode(player)) {
            Actor actor = new Actor(ModMode.PLUGIN.getCleanModModeName(player));
            event.setOwner(actor);
        }
    }

} // LogBlockListener
