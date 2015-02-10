package nu.nerd.modmode;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.kitteh.tag.AsyncPlayerReceiveNameTagEvent;

public class TagAPIListener implements Listener {
	final ModMode plugin;

	TagAPIListener(ModMode instance) {
		plugin = instance;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onNameTag(AsyncPlayerReceiveNameTagEvent event) {
		if (plugin.isModMode(event.getNamedPlayer())) {
			event.setTag(ChatColor.GREEN + event.getNamedPlayer().getName() + ChatColor.WHITE);
		}
	}
}
