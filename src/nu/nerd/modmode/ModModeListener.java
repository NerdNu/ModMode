package nu.nerd.modmode;

import de.diddiz.LogBlock.events.BlockChangePreLogEvent;
import java.util.List;
import net.minecraft.server.v1_6_R2.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_6_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.kitteh.tag.PlayerReceiveNameTagEvent;
import org.kitteh.vanish.event.VanishStatusChangeEvent;


public class ModModeListener implements Listener {
    private final ModMode plugin;

    public class ModModeRunnable implements Runnable {
        private final Player player;

        public ModModeRunnable(Player foo) {
            player = foo;
        }

        public void run() {
            plugin.toggleModMode(player, true, true);
        }
    }


    ModModeListener(ModMode instance) {
        plugin = instance;
    }
    
    @EventHandler
    public void onLogBlockPreLogEvent(BlockChangePreLogEvent event) {
        Player p = plugin.getServer().getPlayerExact(event.getOwner());
        if (p != null && plugin.isModMode(p)) {
            event.setOwner(plugin.getCleanModModeName(p));
        }
    }
    
    @EventHandler(priority= EventPriority.LOWEST)
    public void onNameTag(PlayerReceiveNameTagEvent event) {
        if (plugin.isModMode(event.getNamedPlayer())) {
            event.setTag(ChatColor.GREEN + event.getNamedPlayer().getName() + ChatColor.WHITE);
        }
    }
    
    @EventHandler(priority= EventPriority.LOWEST)
    public void onVanishChange(VanishStatusChangeEvent event) {
        if (event.isVanishing()) {
            plugin.vanished.add(event.getPlayer().getName());
        }
        else {
            plugin.vanished.remove(event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        boolean modmode = false;
        List<MetadataValue> values = player.getMetadata("modmode"); 
        
        for(MetadataValue value : values){
            if(value.getOwningPlugin().getDescription().getName().equals(plugin.getDescription().getName())){
                modmode = value.asBoolean();
            }
        }

        if (plugin.modmode.contains(player.getDisplayName()) && !modmode) {
            event.setJoinMessage(null);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new ModModeRunnable(player));
        }

        if (plugin.vanished.contains(player.getName()))
            plugin.enableVanish(player);

        plugin.updateVanishLists(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        
        // When the player quits, reload their normal player data so that they don't save over it.
        if (plugin.isModMode(event.getPlayer())) {
            final EntityPlayer entityplayer = ((CraftPlayer) event.getPlayer()).getHandle();
            plugin.loadPlayerData(entityplayer, event.getPlayer().getName());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (plugin.isInvisible(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.isInvisible(event.getPlayer()) || plugin.isModMode(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player))
            return;

        Player player = (Player) event.getTarget();
        if (plugin.isModMode(player) || plugin.isInvisible(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // block PVP with a message
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.getDamager() instanceof Player && e.getEntity() instanceof Player) {
                Player damager = (Player) e.getDamager();
                Player victim = (Player) e.getEntity();
                if (plugin.isModMode(damager) || plugin.isInvisible(damager)) {
                    event.setCancelled(true);
                }
                // only show message if they aren't invisible
                else if (plugin.isModMode(victim) && !plugin.isInvisible(victim)) {
                    damager.sendMessage("This moderator is in ModMode.");
                    damager.sendMessage("ModMode should only be used for official server business.");
                    damager.sendMessage("Please let an admin know if a moderator is abusing ModMode.");
                }
            }
        }

        // block all damage to invisible and modmode players
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (plugin.isModMode(victim) || plugin.isInvisible(victim)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE)
            return;
        
        if (plugin.allowFlight) {
            e.getPlayer().setAllowFlight(plugin.isModMode(e.getPlayer()));
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player)event.getEntity();
            if( plugin.isModMode(player)){
                if (player.getFoodLevel() != 20) {
                    player.setFoodLevel(20);
                }
                event.setCancelled(true);
            }
        }
    }
}
