package nu.nerd.modmode;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TABHook {

    private static TabAPI tabAPI;
    private static Map<String, ModModeGroup> groupMap = new HashMap<>();

    private static boolean _allowCollisions;

    // -------------------------------------------------------------------

    public TABHook(TabAPI tabAPIPass) {
        tabAPI = tabAPIPass;

        groupMap.put("adminmode", new ModModeGroup("adminmode", NamedTextColor.GOLD, false, tabAPI));
        groupMap.put("modmode", new ModModeGroup("modmode", NamedTextColor.GREEN, false, tabAPI));
        groupMap.put("vanished", new ModModeGroup("vanished", NamedTextColor.BLUE, false, tabAPI));
    }

    // -------------------------------------------------------------------

    public static TabPlayer playerToTabPlayer(Player player) {
        return tabAPI.getPlayer(player.getName());
    }

    public static ModModeGroup getOrCreateGroup(String name) {
        ModModeGroup group = groupMap.get(name);
        if(group == null) {
            group = new ModModeGroup(name, NamedTextColor.WHITE, true, tabAPI);
            groupMap.put(name, group);
        }
        return group;
    }

    private static ModModeGroup configureGroup(String name, NamedTextColor color, boolean isCollidable) {
        ModModeGroup group = getOrCreateGroup(name);
        if(color != null) {
            group.setNameColor(color);
        }
        group.setCollidable(isCollidable);
        return group;
    }

    static void reconcilePlayerWithVanishState(Player player) {
        TabPlayer tabPlayer = tabAPI.getPlayer(player.getName());
        boolean inModMode = ModMode.PLUGIN.isModMode(player);
        boolean inAdminMode = ModMode.PLUGIN.isAdminMode(player);
        boolean isVanished = ModMode.PLUGIN.isVanished(player);
        ModModeGroup group = null;

        if(inAdminMode) {
            group = groupMap.get("adminmode");
        } else if(inModMode) {
            group = groupMap.get("modmode");
        } else if(isVanished) {
            group = groupMap.get("vanished");
        }

        for(ModModeGroup grp : groupMap.values()) {
            if (grp.isMember(tabPlayer)) {
                grp.removeMember(tabPlayer);
            }
            if (group != null) {
                group.addMember(tabPlayer);
                group.updateMember(tabPlayer);
            }
        }

    }

}
