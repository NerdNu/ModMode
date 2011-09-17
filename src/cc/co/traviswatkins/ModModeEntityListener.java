package cc.co.traviswatkins;

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
        if (e instanceof EntityDamageByEntityEvent)
        {
            EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) e;
            if ((event.getDamager() instanceof Player) && (event.getEntity() instanceof Player)) {
                Player damager = (Player)event.getDamager();
                Player damagee = (Player)event.getEntity();
                if (plugin.isPlayerModMode(damager.getDisplayName()) || plugin.isPlayerInvisible(damager.getName())) {
                    event.setCancelled(true);
                }
                else if (plugin.isPlayerModMode(damagee.getDisplayName()) && !plugin.isPlayerInvisible(damagee.getName())) {
                    damager.sendMessage("This mod is in mod-mode.");
                    damager.sendMessage("This means you cannot damage them and they cannot deal damage.");
                    damager.sendMessage("Mod mod should only be used for official server business.");
                    damager.sendMessage("Please let an admin know if a mod is abusing mod-mode.");
                    event.setCancelled(true);
                }
            }
        }
        else if (e.getEntity() instanceof Player)
        {
            Player damagee = (Player)e.getEntity();
            if (plugin.isPlayerModMode(damagee.getDisplayName()) || plugin.isPlayerInvisible(damagee.getName()))
                e.setCancelled(true);
        }
    }

    @Override
    public void onEntityTarget(EntityTargetEvent e)
    {
        if (!(e.getTarget() instanceof Player))
            return;
        
        Player player = (Player)e.getTarget();
        
        if (!plugin.isPlayerInvisible(player.getName()))
            return;
        
        e.setCancelled(true);
        return;
    }

    @Override
    public void onEntityTrack(EntityTrackEvent e)
    {
        if (!(e.getEntity() instanceof Player))
            return;

        Player player = (Player)e.getEntity();
        if (plugin.isPlayerInvisible(player.getName()))
            if (!Permissions.hasPermission(e.getTracker(), Permissions.VANISH_SHOWHIDDEN))
                e.setCancelled(true);
    }

}
