package nu.nerd.modmode;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group in ModMode.
 * This class provides the structure for a group's data.
 * @version 1.0
 */
public class ModModeGroup {

    private String name;
    private NamedTextColor nameColor;
    private boolean isCollidable;
    private TabAPI tabAPI;
    private NameTagManager nameTagManager;
    private List<TabPlayer> members = new ArrayList<>();

    /**
     * Creates a new {@code ModModeGroup} instance.
     *
     * @param name The name of the group. This is internal only and will not be displayed in-game.
     * @param nameColor The colour of this group. This is what the player's name colour will become
     *                 when part of this group.
     * @param isCollidable If the player can collide with other entities.
     */
    public ModModeGroup(String name, NamedTextColor nameColor, boolean isCollidable, TabAPI tabAPIPass) {
        this.name = name;
        this.nameColor = nameColor;
        this.isCollidable = isCollidable;
        this.tabAPI = tabAPIPass;
        this.nameTagManager = tabAPIPass.getNameTagManager();
    }

    /**
     * Returns the name of this group.
     * @return The name of this group.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the colour of the nametags members of this group will have.
     * @param nameColor The {@code NamedTextColor} representing the colour of name.
     */
    public void setNameColor(NamedTextColor nameColor) {
        this.nameColor = nameColor;
    }

    /**
     * Sets if players in this group can collide with other players.
     * @param collidable If players can collide with other players.
     */
    public void setCollidable(boolean collidable) {
        isCollidable = collidable;
    }

    /**
     * Checks if the specified player is a member of this group.
     * @param player The player being checked.
     * @return True if the player is a member of this group and false if not.
     */
    public boolean isMember(TabPlayer player) {
        return this.members.contains(player);
    }

    /**
     * Adds a player to this group.
     * @param player The player to be added.
     */
    public void addMember(TabPlayer player) {
        this.members.add(player);
    }

    /**
     * Removes a player from this group.
     * @param player The player to be removed.
     */
    public void removeMember(TabPlayer player) {
        this.members.remove(player);
    }

    /**
     * Updates the specified player's in-game name and collision status.
     * @param player The player to be updated.
     */
    public void updateMember(TabPlayer player) {
        String prefix = LegacyComponentSerializer.legacyAmpersand().serialize(Component.text("", nameColor));
        nameTagManager.setPrefix(player, prefix);
        nameTagManager.setCollisionRule(player, isCollidable);
    }

    /**
     * Updates the  in-game name and collision status of all members of this group.
     */
    public void updateMembers() {
        String prefix = LegacyComponentSerializer.legacyAmpersand().serialize(Component.text("", nameColor));
        for(TabPlayer player : members) {
            nameTagManager.setPrefix(player, prefix);
            nameTagManager.setCollisionRule(player, isCollidable);
        }
    }

}
