package nu.nerd.modmode;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.events.BlockChangePreLogEvent;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LogBlockListener implements Listener {
	final ModMode plugin;

	LogBlockListener(ModMode instance) {
		plugin = instance;
	}

	@EventHandler
	public void onLogBlockPreLogEvent(BlockChangePreLogEvent event) {
		Player p = plugin.getServer().getPlayerExact(event.getOwner());
		if (p != null && plugin.isModMode(p)) {
			Actor actor = new Actor(plugin.getCleanModModeName(p));
			event.setOwner(actor);
		}
	}
}
