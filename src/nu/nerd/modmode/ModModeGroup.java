package nu.nerd.modmode;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.neznamy.tab.api.tablist.TabListFormatManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.util.Result;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandException;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents a group in ModMode.
 * This class provides the structure for a group's data.
 * @version 1.0
 */
public class ModModeGroup {

    private String name;

    private ModModeCommand command;

    // Actions
    private List<String> activateBefore;
    private List<String> activateAfter;
    private List<String> deactivateBefore;
    private List<String> deactivateAfter;

    // Details
    private String trackName;
    private String prefix;
    private boolean allowFlight;
    private boolean allowCollisions;
    private boolean suppressJoinMessages;
    private boolean interactWithItems;
    private GameMode defaultGameMode;
    private GameMode defaultGameModeOnDeactivate;

    // Other necessary objects
    private ModMode plugin;
    private TabAPI TABAPI;
    private NameTagManager nameTagManager;
    private TabListFormatManager tabListFormatManager;
    private Permissions permissions;

    // Members
    private List<UUID> members;

    // Members bypassing the item interaction block
    private Set<UUID> membersBypassingItemBlock;

    // Bossbars when in mode
    private BossBar inModeBar;
    private BossBar itemInteractionEnabledBar;

    /**
     * Creates a new {@code ModModeGroup} instance.
     *
     * @param name The name of the group. This is internal only and will not be displayed in-game.
     * @param prefix The colour of this group. This is what the player's name colour will become
     *                 when part of this group.
     * @param allowCollisions If the player can collide with other entities.
     */
    public ModModeGroup(String name, ModModeCommand command, List<String> activateBefore,
                        List<String> activateAfter, List<String> deactivateBefore, List<String> deactivateAfter, String trackName,
                        String prefix, boolean allowFlight, boolean allowCollisions, boolean suppressJoinMessages,
                        boolean interactWithItems, GameMode defaultGameMode, GameMode defaultGameModeOnDeactivate,
                        List<UUID> members, ModMode plugin, TabAPI TABAPI) {
        this.name = name;
        this.command = command;
        this.activateBefore = activateBefore;
        this.activateAfter = activateAfter;
        this.deactivateBefore = deactivateBefore;
        this.deactivateAfter = deactivateAfter;
        this.trackName = trackName;
        this.prefix = prefix;
        this.allowFlight = allowFlight;
        this.allowCollisions = allowCollisions;
        this.suppressJoinMessages = suppressJoinMessages;
        this.interactWithItems = interactWithItems;
        this.defaultGameMode = defaultGameMode;
        this.defaultGameModeOnDeactivate = defaultGameModeOnDeactivate;
        this.members = members;
        this.plugin = plugin;
        this.TABAPI = TABAPI;
        this.nameTagManager = TABAPI.getNameTagManager();
        this.tabListFormatManager = TABAPI.getTabListFormatManager();
        this.permissions = plugin.getPermissions();
        inModeBar = BossBar.bossBar(Component.text("You're currently in " + this.getName(),
                        NamedTextColor.GREEN), 1, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        if(this.interactWithItems) {
            membersBypassingItemBlock = new HashSet<>();
            itemInteractionEnabledBar = BossBar.bossBar(Component.text("⚠ You have item interaction enabled! ⚠",
                    NamedTextColor.RED), 1, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        }
        command.setParentGroup(this);
    }

    // ------------------------------------------------------------------------

    /**
     * Promote a player into this group.
     * @param player The player being promoted.
     * @param uuid The UUID of the player being promoted.
     */
    public void promote(Player player, UUID uuid) {
        addMember(uuid);
        runCommands(player, activateBefore);
        boolean result = permissions.promote(player, this);
        if(result) {
            continueStateChange(player, uuid, true);
        } else {
            plugin.stateFail(player, uuid);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Demote a player from this group.
     * @param player The player being demoted.
     * @param uuid The UUID of the player being demoted.
     */
    public void demote(Player player, UUID uuid) {
        runCommands(player, deactivateBefore);
        boolean result = permissions.demote(player, this);
        if(result) {
            removeMember(uuid);
            if(isInteractWithItems()) {
                removeBypassingItemBlock(uuid);
            }
            continueStateChange(player, uuid, false);
        } else {
            // Trigger a state fail
            plugin.stateFail(player, uuid);
            /*
             Re-add them to the group so they can transition out properly and not be in a limbo state until
             they run the toggle command again.
             */
            addMember(uuid);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Add a player to this group's member list using their UUID.
     * @param uuid The UUID of the player being added.
     */
    private void addMember(UUID uuid) {
        this.members.add(uuid);
        plugin.getPlayerGroupMap().put(uuid, this);
    }

    // ------------------------------------------------------------------------

    /**
     * Remove a player from this group's member list using their UUID.
     * @param uuid The UUID of the player being removed.
     */
    public void removeMember(UUID uuid) {
        this.members.remove(uuid);
        plugin.getPlayerGroupMap().remove(uuid);
    }

    // ------------------------------------------------------------------------

    /**
     * Run the commands provided as the player moving through this group.
     * @param player The player being promoted/demoted.
     * @param commands The commands to be run.
     */
    public void runCommands(Player player, List<String> commands) {
        for(String command : commands) {
            if (command.length() > 0 && command.charAt(0) == '/') {
                command = command.substring(1);
            }
            try {
                if(!Bukkit.getServer().dispatchCommand(player, command)) {
                    plugin.logError("Command \"" + command + "\" could not be executed.");
                }
            } catch (CommandException e) {
                plugin.logError("Command \"" + command + "\" raised " + e.getClass().getName());
            }
        }
    }

    // ------------------------------------------------------------------------

    /**
     * The second phase of the promotion/demotion process that handles file I/O and alerting the player of their
     * status change.
     * @param player The player being promoted/demoted.
     * @param uuid The UUID player being promoted/demoted.
     * @param promotion True if the player is being promoted into the group, false if being demoted.
     */
    private void continueStateChange(Player player, UUID uuid, boolean promotion) {
        Configuration config = plugin.getCONFIG();
        if(promotion) {
            config.addMemberToGroup(this.getName(), uuid);
        } else {
            config.removeMemberFromGroup(this.getName(), uuid);
        }

        // Swap player inventories and other information.
        config.savePlayerData(player, !promotion);
        config.loadPlayerData(player, promotion);

        sharedSetup(player, promotion);

        if(promotion) {
            player.sendMessage(Component.text("You are now in " + name + "!"));
        } else {
            runCommands(player, deactivateAfter);
            player.sendMessage(Component.text("You are no longer in " + name + "!"));
            if(plugin.isSilentJoin(uuid)) {
                plugin.getServer().broadcast(Component.text(player.getName() + " joined the game",
                        NamedTextColor.YELLOW));
                plugin.removeSilentJoin(uuid);
            }
        }
    }

    // ------------------------------------------------------------------------

    /**
     * The third phase of initial promotion, hijhacked by some other locations of the plugin. Handles the player's
     * physical state, visibility, and other important things that need to happen through the normal promotion path,
     * but also through rejoining the game and other causes.
     * @param player The player being promoted/demoted.
     * @param promotion True if the player is being promoted into the group, false if being demoted.
     */
    public void sharedSetup(Player player, boolean promotion) {

        if(promotion) plugin.clearDamage(player);
        plugin.setVanish(player, promotion);

        World world = player.getWorld();
        Chunk chunk = world.getChunkAt(player.getLocation());
        world.refreshChunk(chunk.getX(), chunk.getZ());

        plugin.restoreFlight(player, this, promotion);
        updateMemberName(TABAPI.getPlayer(player.getUniqueId()), promotion);

        // Refresh vanished players so they can see each other.
        plugin.updateAllPlayersSeeing();

        if(promotion) {
            player.setGameMode(defaultGameMode);
            runCommands(player, activateAfter);
            player.showBossBar(inModeBar);
            player.hideBossBar(plugin.getVanishedBar());
        } else {
            player.setGameMode(defaultGameModeOnDeactivate);
            player.hideBossBar(inModeBar);
            player.hideBossBar(plugin.getModeUnvanishedBar());
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Updates the colour/prefix of the player depending on promotion/demotion status.
     * @param player The player having their name updated.
     * @param promotion True if the player is being promoted into the group, false if being demoted.
     */
    public void updateMemberName(TabPlayer player, boolean promotion) {
        if(promotion) {
            nameTagManager.setPrefix(player, prefix);
            nameTagManager.setCollisionRule(player, isAllowCollisions());
            tabListFormatManager.setPrefix(player, prefix);
        } else {
            nameTagManager.setPrefix(player, null);
            nameTagManager.setCollisionRule(player, true);
            tabListFormatManager.setPrefix(player, null);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Add a player to the list of members with item interaction enabled.
     * @param playerUUID The UUID of the player being added.
     * @return True if operation succeeded, false if not.
     */
    public boolean addBypassingItemBlock(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if(player == null) return false;
        boolean additionSuccess = this.membersBypassingItemBlock.add(playerUUID);
        if(additionSuccess) player.showBossBar(itemInteractionEnabledBar);
        return additionSuccess;
    }

    // ------------------------------------------------------------------------

    /**
     * Remove a player from the list of members with item interaction enabled.
     * @param playerUUID The UUID of the player being removed.
     * @return True if operation succeeded, false if not.
     */
    public boolean removeBypassingItemBlock(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if(player == null) return false;
        boolean removalSuccess = this.membersBypassingItemBlock.remove(playerUUID);
        if(removalSuccess) player.hideBossBar(itemInteractionEnabledBar);
        return removalSuccess;
    }

    /*
    ------------------------------------------------------------------------
    GETTERS AND SETTERS
    ------------------------------------------------------------------------
     */

    /**
     * Returns the name of this group.
     * @return The name of this group.
     */
    public String getName() {
        return name;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the instance of the command object paired with this group.
     * @return the instance of the command object paired with this group.
     */
    public ModModeCommand getCommand() {
        return this.command;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the name of the LuckPerms track this group slides along.
     * @return the name of the LuckPerms track this group slides along.
     */
    public String getTrackName() {
        return trackName;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns whether members of this group are able to fly or not.
     * @return true if able, false if not.
     */
    public boolean isAllowFlight() {
        return allowFlight;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns whether members of this group are able to collide with other entities or not.
     * @return true if able, false if not.
     */
    public boolean isAllowCollisions() {
        return allowCollisions;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns whether members of this group quit/join the game silently or not.
     * @return true if they do, false if not.
     */
    public boolean isSuppressJoinMessages() {
        return suppressJoinMessages;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the list of members' UUIDs of this group.
     * @return the list of members' UUIDs of this group.
     */
    public List<UUID> getMembers() {
        return members;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns whether members of this group are able to toggle item dropping/pick up.
     * @return true if able, false if not.
     */
    public boolean isInteractWithItems() {
        return interactWithItems;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns whether the member with the specified UUID currently has item interaction enabled.
     * @param playerUUID the UUID of the player.
     * @return true if they are, false if not.
     */
    public boolean isBypassingItemBlock(UUID playerUUID) {
        return this.membersBypassingItemBlock.contains(playerUUID);
    }

    // ------------------------------------------------------------------------

    /**
     * Used by the Configuration class to update all of this group's configurable data in the event of a reload.
     * @param activateBefore List of commands to run before entering this group.
     * @param activateAfter List of commands to run after entering this group.
     * @param deactivateBefore List of commands to run before leaving this group.
     * @param deactivateAfter List of commands to run after leaving this group.
     * @param trackName The name of the LuckPerms track this group slides along.
     * @param prefix The prefix of this group.
     * @param allowFlight If members of this group are able to fly, regardless of game mode.
     * @param allowCollisions If members of this group collide with other entities.
     * @param suppressJoinMessages If members of this group will leave/join silently.
     * @param interactWithItems If members of this group can toggle the ability to pick up and drop items.
     */
    public void updateGroup(List<String> activateBefore, List<String> activateAfter, List<String> deactivateBefore,
                            List<String> deactivateAfter, String trackName, String prefix, boolean allowFlight,
                            boolean allowCollisions, boolean suppressJoinMessages, boolean interactWithItems) {
        this.activateBefore = activateBefore;
        this.activateAfter = activateAfter;
        this.deactivateBefore = deactivateBefore;
        this.deactivateAfter = deactivateAfter;
        this.trackName = trackName;
        this.prefix = prefix;
        this. allowFlight = allowFlight;
        this.allowCollisions = allowCollisions;
        this.suppressJoinMessages = suppressJoinMessages;
        this.interactWithItems = interactWithItems;
        if(interactWithItems && membersBypassingItemBlock == null) {
            membersBypassingItemBlock = new HashSet<>();
            itemInteractionEnabledBar = BossBar.bossBar(Component.text("⚠ You have item interaction enabled! ⚠",
                    NamedTextColor.RED), 1, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        } else if (!interactWithItems && membersBypassingItemBlock != null) {
            for(UUID memberUUID : membersBypassingItemBlock) {
                removeBypassingItemBlock(memberUUID);
            }
            membersBypassingItemBlock = null;
            itemInteractionEnabledBar = null;
        }
    }
}
