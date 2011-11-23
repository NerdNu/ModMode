package cc.co.traviswatkins;

import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTrackEvent;


public class ModModeEntityListener extends EntityListener 
{
    private final ModMode plugin;

    public ModModeEntityListener(ModMode instance)
    {
        plugin = instance;
    }

    @Override
    public void onEntityDamage(EntityDamageEvent e)
    {
        // block PVP specifically
        if (e instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) e;
            if ((event.getDamager() instanceof Player) && (event.getEntity() instanceof Player)) {
                Player damager = (Player)event.getDamager();
                Player damagee = (Player)event.getEntity();
                if (plugin.isPlayerModMode(damager) || plugin.isPlayerInvisible(damager)) {
                    event.setCancelled(true);
                }
                else if (plugin.isPlayerModMode(damagee) && !plugin.isPlayerInvisible(damagee)) {
                    damager.sendMessage("This mod is in mod-mode.");
                    damager.sendMessage("This means you cannot damage them and they cannot deal damage.");
                    damager.sendMessage("Mod mod should only be used for official server business.");
                    damager.sendMessage("Please let an admin know if a mod is abusing mod-mode.");
                    event.setCancelled(true);
                }
            }
        }

        // block all damage to invisible people and people in mod mode
        if (e.getEntity() instanceof Player) {
            Player damagee = (Player)e.getEntity();
            if (plugin.isPlayerModMode(damagee) || plugin.isPlayerInvisible(damagee))
                e.setCancelled(true);
        }
    }

    @Override
    public void onEntityTarget(EntityTargetEvent e)
    {
        if (!(e.getTarget() instanceof Player))
            return;
        
        Player player = (Player)e.getTarget();
        
        if (!plugin.isPlayerInvisible(player))
            return;

        if (!plugin.isPlayerModMode(player))
            return;
        
        e.setCancelled(true);
        return;
    }

    @Override
    public void onEntityTrack(EntityTrackEvent e)
    {
        if (!(e.getEntity() instanceof Player))
            return;
        e.setHandled(true);

        Player player = (Player)e.getEntity();
        if (plugin.isPlayerInvisible(player))
            if (!plugin.shouldSee(e.getTracker(), player))
                e.setCancelled(true);
    }

}
