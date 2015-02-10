package nu.nerd.modmode;

import de.diddiz.LogBlock.events.BlockChangePreLogEvent;
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
			event.setOwner(plugin.getCleanModModeName(p));
		}
	}
}
