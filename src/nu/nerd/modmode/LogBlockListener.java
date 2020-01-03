package nu.nerd.modmode;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.events.BlockChangePreLogEvent;

// ----------------------------------------------------------------------------
/**
 * The LogBlock event-handling class.
 */
public class LogBlockListener implements Listener {

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    LogBlockListener() {
        ModMode.PLUGIN.getServer().getPluginManager().registerEvents(this, ModMode.PLUGIN);
    }

    // ------------------------------------------------------------------------
    /**
     * Ensures edits made by players in ModMode are logged with that player's
     * ModMode name.
     */
    @EventHandler
    public void onLogBlockPreLogEvent(BlockChangePreLogEvent event) {
        Player player;
        try {
            UUID actorUUID = UUID.fromString(event.getOwnerActor().getUUID());
            player = ModMode.PLUGIN.getServer().getPlayer(actorUUID);
        } catch (Exception e) {
            // probably liquid flow or something
            return;
        }
        if (player != null && ModMode.PLUGIN.isModMode(player)) {
            Actor actor = new Actor(ModMode.PLUGIN.getCleanModModeName(player));
            event.setOwner(actor);
        }
    }

} // LogBlockListener
