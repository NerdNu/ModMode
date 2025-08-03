package nu.nerd.modmode;

import java.io.File;
import java.util.*;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.neznamy.tab.api.tablist.TabListFormatManager;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nu.nerd.modmode.listeners.CoreProtectListener;
import nu.nerd.modmode.listeners.ModModeListener;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.kitteh.vanish.VanishPerms;
import org.kitteh.vanish.VanishPlugin;

import static org.bukkit.Bukkit.getPluginManager;

// ------------------------------------------------------------------------
/**
 * The main plugin and command-handling class.
 */
public class ModMode extends JavaPlugin {

    /**
     * This plugin.
     */
    private ModMode PLUGIN;

    /**
     * The permissions handler.
     */
    private Permissions permissions;

    /**
     * This plugin's configuration.
     */
    private Configuration CONFIG;

    /**
     * The vanish plugin.
     */
    private VanishPlugin vanish;

    /**
     * The API for the TAB plugin.
     */
    private TabAPI TABAPI;

    /**
     * The part of the TAB API that lets us manage player nametags.
     */
    private NameTagManager nameTagManager;

    /**
     * The part of the TAB API that lets us manage the TAB list format.
     */
    private TabListFormatManager tabListFormatManager;

    /**
     * All currently loaded groups.
     */
    private HashMap<String, ModModeGroup> groupMap = new HashMap<>();

    /**
     * A map of players and groups they're members of for easy fetching.
     */
    private HashMap<UUID, ModModeGroup> playerGroupMap = new HashMap<>();

    /**
     * A set of UUIDs of players that have joined the server silently.
     */
    private Set<UUID> silentJoinSet = new HashSet<>();

    /**
     * Bossbar displayed when vanished and not in a mode.
     */
    private BossBar vanishedBar;

    /**
     * Bossbar displayed when in a mode and unvanished.
     */
    private BossBar modeUnvanishedBar;

    // ------------------------------------------------------------------------

    @Override
    public void onEnable() {
        PLUGIN = this;
        permissions = new Permissions(this);

        // Check if TAB is loaded.
        Plugin tabPlugin = getPluginManager().getPlugin("TAB");
        if(tabPlugin == null) {
            logError("TAB is required. https://modrinth.com/plugin/tab-was-taken");
            getPluginManager().disablePlugin(this);
            return;
        } else {
            TABAPI = TabAPI.getInstance();
            nameTagManager = TABAPI.getNameTagManager();
            tabListFormatManager = TABAPI.getTabListFormatManager();
        }

        // Load the config and commands.
        CONFIG = new Configuration(this, TABAPI);

        // Load the main listener class.
        new ModModeListener(this);

        // Check if VanishNoPacket is loaded.
        vanish = (VanishPlugin) getPluginManager().getPlugin("VanishNoPacket");
        if(vanish == null) {
            logError("VanishNoPacket is required. http://dev.bukkit.org/server-mods/vanish/");
            getPluginManager().disablePlugin(this);
            return;
        } else {
            CommandExecutor ownExecutor = getCommand("reloadmodmode").getExecutor();
            PluginCommand vanishCommand = vanish.getCommand("vanish");
            vanishCommand.setExecutor(ownExecutor);
            vanishCommand.setPermission(Permissions.VANISH);
            logInfo("Successfully hooked into VanishNoPacket.");
        }

        Plugin coreProtectPlugin = getServer().getPluginManager().getPlugin("CoreProtect");
        if(coreProtectPlugin != null && coreProtectPlugin.isEnabled()) {
            CoreProtectAPI coreProtectAPI = ((CoreProtect) coreProtectPlugin).getAPI();
            if(coreProtectAPI.isEnabled()) {
                new CoreProtectListener(this);
                logInfo("Successfully hooked into CoreProtect.");
            }
        }

        vanishedBar = BossBar.bossBar(Component.text("You're currently vanished and not in a mode.",
                NamedTextColor.BLUE), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);

        modeUnvanishedBar = BossBar.bossBar(Component.text("⚠ You're unvanished! ⚠",
                NamedTextColor.RED), 1, BossBar.Color.RED, BossBar.Overlay.PROGRESS);

        final int TEN_MINUTES = 10 * 60 * 20;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for(ModModeGroup group : groupMap.values()) {
                for(UUID uuid : group.getMembers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if(player != null) {
                        player.setStatistic(Statistic.TIME_SINCE_REST, 0);
                    }
                }
            }
        }, TEN_MINUTES, TEN_MINUTES);

    }

    // ------------------------------------------------------------------------

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        CONFIG.save();
    }

    // ------------------------------------------------------------------------

    /**
     * Return true if the player is currently vanished.
     *
     * @return true if the player is currently vanished.
     */
    public boolean isVanished(Player player) {
        return vanish.getManager().isVanished(player);
    }

    // ------------------------------------------------------------------------

    /**
     * Set the vanish state of the player and enable/disable bossbar notifiers.
     *
     * @param player   the Player.
     * @param vanished true if he should be vanished.
     */
    public void setVanish(Player player, boolean vanished) {
        if (vanish.getManager().isVanished(player) != vanished) {
            vanish.getManager().toggleVanish(player);

            if(isInMode(player)) {
                if(vanished) player.hideBossBar(modeUnvanishedBar);
                else player.showBossBar(modeUnvanishedBar);
            } else {
                if(vanished) player.showBossBar(vanishedBar);
                else player.hideBossBar(vanishedBar);
            }

            // Update the permissions that VanishNoPacket caches to match the
            // new permissions of the player. This is highly dependent on this
            // API method not doing anything more than what it currently does:
            // to simply remove the cached VanishUser (permissions) object.
            VanishPerms.userQuit(player);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Try to coerce VanishNoPacket into showing or hiding players to each other
     * based on their current vanish state and permissions.
     *
     * Just calling resetSeeing() when a moderator toggles ModMode (and hence
     * permissions and vanish state) is apparently insufficient.
     */
    public void updateAllPlayersSeeing() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            vanish.getManager().playerRefresh(player);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Returns a "clean" mode name for the given player by prepending the
     * player's name with their group's name.
     *
     * @param player the player.
     * @return the player's mode name.
     */
    public String getCleanModeName(Player player) {
        return playerGroupMap.get(player.getUniqueId()).getName() + "_" + player.getName();
    }

    // ------------------------------------------------------------------------

    /**
     * Clear the player's incoming damage before entering ModMode.
     *
     * @param player The player.
     */
    public void clearDamage(Player player) {
        player.setFireTicks(0);
        player.setFallDistance(0F);

        // Moderators should not spawn phantoms.
        player.setStatistic(Statistic.TIME_SINCE_REST, 0);
    }

    // ------------------------------------------------------------------------

    /**
     * Restore flight ability if in ModMode or creative game mode.
     *
     * @param player      the player.
     * @param isInMode true if the player is in ModMode.
     */
    public void restoreFlight(Player player, ModModeGroup group, boolean isInMode) {
        player.setAllowFlight((isInMode && group.isAllowFlight()) || player.getGameMode() == GameMode.CREATIVE);
    }

    // ------------------------------------------------------------------------

    /**
     * Handles a player whose ModMode state has failed. Remove them from all possible groups and demote them from all
     * tracks. This will fix the uncommon transition bug and the player should be able to try their command again.
     * @param player The player who triggered the failed state.
     * @param uuid The player's UUID.
     */
    public void stateFail(Player player, UUID uuid) {
        player.sendMessage(Component.text("State change failed. Please try again in a bit. If this issue" +
                " persists, let an admin know.", NamedTextColor.RED, TextDecoration.BOLD));
        for(ModModeGroup group : groupMap.values()) {
            group.removeMember(uuid);
            permissions.demote(player, group);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Adds a player to the list of people who are currently on the server after rejoining silently.
     * @param uuid the UUID of the player.
     */
    public void addSilentJoin(UUID uuid) {
        silentJoinSet.add(uuid);
    }

    // ------------------------------------------------------------------------

    /**
     * Removes a player from the list of people who are currently on the server after rejoining silently.
     * @param uuid the UUID of the player.
     */
    public void removeSilentJoin(UUID uuid) {
        silentJoinSet.remove(uuid);
    }

    // ------------------------------------------------------------------------

    /**
     * Logs a simple white message to the console.
     * @param message The message to be sent.
     */
    public void logInfo(String message) {
        this.getComponentLogger().info(Component.text("[ModMode] " + message));
    }

    // ------------------------------------------------------------------------

    /**
     * Logs a simple red message to the console.
     * @param message The message to be sent.
     */
    public void logError(String message) {
        this.getComponentLogger().error(Component.text("[ModMode] " + message));
    }

    // ------------------------------------------------------------------------

    /*
    Used for certain commands that don't fit into the auto-generated command objects for groups.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        String commandName = command.getName();
        if(commandName.equalsIgnoreCase("reloadmodmode")) {
            if(sender instanceof Player player) {
                if(player.hasPermission("modmode.reload")) {
                    CONFIG.reload();
                    player.sendMessage(Component.text("ModMode's configuration has been reloaded.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                }
            } else if(sender instanceof ConsoleCommandSender) {
                CONFIG.reload();
                logInfo("Plugin configuration reloaded.");
            }
            return true;
        }

        if(sender instanceof Player player) {
            UUID playerUUID = player.getUniqueId();
            if(commandName.equalsIgnoreCase("vanish")) {
                if(isVanished(player)) {
                    player.sendMessage(Component.text("You're already vanished.", NamedTextColor.RED));
                } else {
                    setVanish(player, true);
                    TabPlayer tabPlayer = TABAPI.getPlayer(playerUUID);
                    if(!playerGroupMap.containsKey(playerUUID)) {
                        nameTagManager.setPrefix(tabPlayer, "<blue>");
                        tabListFormatManager.setPrefix(tabPlayer, "<blue>");
                    } else {
                        playerGroupMap.get(playerUUID).updateMemberName(tabPlayer, true);
                    }
                }
            } else if(commandName.equalsIgnoreCase("unvanish")) {
                if(!isVanished(player)) {
                    player.sendMessage(Component.text("You're already unvanished.", NamedTextColor.RED));
                } else {
                    setVanish(player, false);
                    TabPlayer tabPlayer = TABAPI.getPlayer(playerUUID);
                    if(!playerGroupMap.containsKey(playerUUID)) {
                        nameTagManager.setPrefix(tabPlayer, null);
                        tabListFormatManager.setPrefix(tabPlayer, null);
                    } else {
                        playerGroupMap.get(playerUUID).updateMemberName(tabPlayer, true);
                    }
                }
            }
        }

        return true;
    }

    /*
    ------------------------------------------------------------------------
    GETTERS AND SETTERS
    ------------------------------------------------------------------------
     */

    /**
     * Returns an instance of this plugin.
     * @return an instance of this plugin.
     */
    public ModMode getPLUGIN() {
        return PLUGIN;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns an instance of this plugin's configuration class.
     * @return an instance of this plugin's configuration class.
     */
    public Configuration getCONFIG() {
        return CONFIG;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns an instance of the TAB plugin's API.
     * @return an instance of the TAB plugin's API.
     */
    public TabAPI getTABAPI() {
        return TABAPI;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns a list containing all instances of groups.
     * @return a list containing all instances of groups.
     */
    public HashMap<String, ModModeGroup> getGroups() {
        return groupMap;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns a hashmap containing player UUIDs and the groups they belong to.
     * @return a hashmap containing player UUIDs and the groups they belong to.
     */
    public HashMap<UUID, ModModeGroup> getPlayerGroupMap() {
        return playerGroupMap;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the instance of this plugin's permissions class.
     * @return the instance of this plugin's permissions class.
     */
    public Permissions getPermissions() {
        return permissions;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the bossbar displayed when a player is in a mode and unvanished.
     * @return the bossbar displayed when a player is in a mode and unvanished.
     */
    public BossBar getModeUnvanishedBar() {
        return modeUnvanishedBar;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the bossbar displayed when a player is vanished but not in a mode.
     * @return the bossbar displayed when a player is vanished but not in a mode.
     */
    public BossBar getVanishedBar() {
        return vanishedBar;
    }

    // ------------------------------------------------------------------------

    /**
     * Checks if a player is currently on the server after joining silently and returns the result.
     * @param uuid the UUID of the player being checked.
     * @return true if they are on silently, false if not.
     */
    public boolean isSilentJoin(UUID uuid) {
        return silentJoinSet.contains(uuid);
    }

    // ------------------------------------------------------------------------

    /**
     * Gets the group the player belonging to the provided UUID belongs to.
     * @param uuid the UUID of the player being checked.
     * @return the instance of the group the player is in.
     */
    public ModModeGroup getGroupPlayerMemberOf(UUID uuid) {
        return playerGroupMap.get(uuid);
    }

    // ------------------------------------------------------------------------

    /**
     * Checks if a player is in a group.
     * @param player the player being checked.
     * @return true if the player is in a group, false if not.
     */
    public boolean isInMode(Player player) {
        return playerGroupMap.containsKey(player.getUniqueId());
    }

} // ModMode
