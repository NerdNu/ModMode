package nu.nerd.modmode;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.events.BlockChangePreLogEvent;
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
            Actor actor = new Actor(getCleanModModeName(player));
            event.setOwner(actor);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Returns a "clean" ModMode name for the given player by prepending the
     * player's name with "modmode_".
     *
     * @param player the player.
     * @return the player's ModMode name.
     */
    private static String getCleanModModeName(Player player) {
        return "modmode_" + player.getName();
    }

} // LogBlockListener
