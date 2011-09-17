package cc.co.traviswatkins;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;


public class ModModePlayerListener extends PlayerListener
{
    private final ModMode plugin;

    public ModModePlayerListener(ModMode instance)
    {
        plugin = instance;
    }
        
    @Override
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        vanishForJoinOrRespawn(event.getPlayer(), true);
        if (plugin.isPlayerInvisible(event.getPlayer().getName()))
            event.setJoinMessage(null);

        if (plugin.isPlayerModMode(event.getPlayer().getDisplayName()))
            plugin.modmode.remove(event.getPlayer().getDisplayName());
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        if (plugin.isPlayerInvisible(event.getPlayer().getName()))
            event.setQuitMessage(null);

        if (plugin.isPlayerModMode(event.getPlayer().getDisplayName()))
            plugin.modmode.remove(event.getPlayer().getDisplayName());
    }
        
    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event)
    {
        vanishForJoinOrRespawn(event.getPlayer(), false);
    }
                
    private void vanishForJoinOrRespawn(Player player, boolean notify)
    {
        if(plugin.isPlayerInvisible(player.getName()))
        {
            if(notify)
            {
                player.sendMessage(ChatColor.RED + "You are currently invisible!");    
            }
            
            plugin.enableVanish(player);
        }
    }
    
    @Override    
    public void onPlayerPickupItem(PlayerPickupItemEvent event)
    {
        Player player = event.getPlayer();
    
        if(plugin.isPlayerInvisible(player.getName()))
            event.setCancelled(true);
    }

    @Override
    public void onPlayerDropItem (PlayerDropItemEvent event)
    {
        if (plugin.isPlayerModMode(event.getPlayer().getDisplayName()) || plugin.isPlayerInvisible(event.getPlayer().getName()))
            event.setCancelled(true);
    }

}
