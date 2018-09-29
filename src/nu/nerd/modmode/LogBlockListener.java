package nu.nerd.modmode;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.events.BlockChangePreLogEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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
		Player player = ModMode.PLUGIN.getServer().getPlayerExact(event.getOwner());
		if (player != null && ModMode.PLUGIN.isModMode(player)) {
			Actor actor = new Actor(ModMode.PLUGIN.getCleanModModeName(player));
			event.setOwner(actor);
		}
	}

} // LogBlockListener
