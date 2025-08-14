package nu.nerd.modmode.listeners;


import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nu.nerd.modmode.ModMode;
import nu.nerd.modmode.ModModeGroup;
import nu.nerd.modmode.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.GameEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

// ------------------------------------------------------------------------
/**
 * The plugin's main event-handling class.
 */
public class ModModeListener implements Listener {

    private ModMode plugin;
    private TabAPI TABAPI;
    private Permissions permissions;

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    public ModModeListener(ModMode plugin) {
        this.plugin = plugin;
        this.TABAPI = plugin.getTABAPI();
        this.permissions = plugin.getPermissions();
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // TAB Events
        onPlayerLoad();
    }

    // ------------------------------------------------------------------------
    /**
     * Ensures players in a group rejoin in secret, if the group permits for that.
     * Join message will appear the next time the player leaves their mode.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ModModeGroup group = plugin.getGroupPlayerMemberOf(uuid);
        if(group != null) {
            if(group.isSuppressJoinMessages()) {
                if(permissions.promote(player, group)) {
                    event.joinMessage(null);
                    plugin.addSilentJoin(uuid);
                } else {
                    group.demote(player, uuid);
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Ensures players in a group leave without a quit message to maintain secrecy, should their group permit.
     */
    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ModModeGroup group = plugin.getGroupPlayerMemberOf(uuid);
        if(group != null) {
            if(group.isSuppressJoinMessages()) {
                if(permissions.demote(player, group)) {
                    event.quitMessage(null);
                    plugin.removeSilentJoin(uuid);
                } else {
                    group.demote(player, uuid);
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * An event run through the TAB API's event bus.
     * Used to handle extra toggles when rejoining the server in a mode like name colour, flight, and vanish state.
     */
    public void onPlayerLoad() {
        TABAPI.getEventBus().register(PlayerLoadEvent.class, event -> {
            // Wrap with a scheduler task to force the event back onto the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                UUID uuid = event.getPlayer().getUniqueId();
                Player player = Bukkit.getPlayer(uuid);
                if(player != null) {
                    ModModeGroup group = plugin.getGroupPlayerMemberOf(uuid);
                    if(group != null) {
                        group.sharedSetup(player, true);
                        plugin.updateAllPlayersSeeing();
                    }
                }
            });
        });
    }

    // ------------------------------------------------------------------------
    /**
     * Prevents players from picking up items while in a mode if their group forbids it, or they haven't toggled it on.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if(!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        ModModeGroup group = plugin.getGroupPlayerMemberOf(uuid);
        if (group != null) {
            if (!group.isInteractWithItems() || !group.isBypassingItemBlock(uuid)) {
                event.setCancelled(true);
            }
        } else if (plugin.isVanished(player)) {
            event.setCancelled(true);
        }

    }

    // ------------------------------------------------------------------------
    /**
     * Prevents players from dropping items while in a mode if their group forbids it, or they haven't toggled it on.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ModModeGroup group = plugin.getGroupPlayerMemberOf(uuid);
        if (group != null) {
            if (!group.isInteractWithItems() || !group.isBypassingItemBlock(uuid)) {
                event.setCancelled(true);
            }
        } else if (plugin.isVanished(player)) {
            event.setCancelled(true);
        }

    }

    // ------------------------------------------------------------------------
    /**
     * Prevents entities (hostile mobs, parrots, etc.) from targeting players in a mode.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if(!(event.getTarget() instanceof Player player)) return;
        if(plugin.isInMode(player) || plugin.isVanished(player)) event.setCancelled(true);
    }

    // ------------------------------------------------------------------------
    /**
     * Prevents players in a mode from damaging players or being damaged by anything.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if(!(event.getEntity() instanceof Player victim)) return;

        // Block PVP with a message.
        if(event instanceof  EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if(e.getDamager() instanceof Player damager) {

                // Prevent staff from doing PvP damage.
                if(plugin.isInMode(damager) || plugin.isVanished(damager)) {
                    event.setCancelled(true);
                }

                // Only show message if staff are visible.
                else if(plugin.isInMode(victim) && !plugin.isVanished(victim)) {
                    damager.sendMessage(Component.text("This moderator is in ModMode.", NamedTextColor.RED));
                    damager.sendMessage(Component.text("ModMode should only be used for official server" +
                            " business.", NamedTextColor.GREEN));
                    damager.sendMessage(Component.text("Please let an admin know if a moderator is" +
                            " abusing ModMode.", NamedTextColor.GREEN));
                }
            }

        }

        // Block all damage to invisible and modmode players.
        if(plugin.isInMode(victim) || plugin.isVanished(victim)) {
            // Extinguish view-obscuring fires.
            victim.setFireTicks(0);
            event.setCancelled(true);
        }

    }

    // ------------------------------------------------------------------------
    /**
     * Updates the player's WorldeditCache and allow-flight status upon changing
     * worlds.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        ModModeGroup group = plugin.getGroupPlayerMemberOf(player.getUniqueId());
        if(group != null) {
            if (player.getGameMode() != GameMode.CREATIVE && group.isAllowFlight()) {
                boolean flightState = plugin.isInMode(player);
                player.setAllowFlight(flightState);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Restores a player's flight ability upon changing game modes.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ModModeGroup group = plugin.getGroupPlayerMemberOf(player.getUniqueId());
            if(group != null) {
                boolean flightState = plugin.isInMode(player);
                plugin.restoreFlight(player, group, flightState);
            }
        });
    }

    // ------------------------------------------------------------------------
    /**
     * Prevents the depletion of hunger level for players in ModMode.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.isInMode(player)) {
                if (player.getFoodLevel() != 20) {
                    player.setFoodLevel(20);
                }
                event.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Prevent vanished players from triggering sculk sensors and shriekers.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSculkBlockActivate(BlockReceiveGameEvent event) {
        GameEvent gameEvent = event.getEvent();
        if(event.getEntity() instanceof Player player) {
            if(plugin.isVanished(player)) {
                if(gameEvent == GameEvent.SCULK_SENSOR_TENDRILS_CLICKING || gameEvent == GameEvent.SHRIEK) {
                    Material eventBlockType = event.getBlock().getType();
                    if(eventBlockType.equals(Material.SCULK_SENSOR) || eventBlockType.equals(Material.SCULK_SHRIEKER)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

} // ModModeListener
