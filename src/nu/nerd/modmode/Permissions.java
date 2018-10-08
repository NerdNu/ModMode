package nu.nerd.modmode;

import net.milkbowl.vault.Vault;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashSet;
import java.util.Set;

public class Permissions {

    private static final String MODMODE = "modmode.";
    public static final String VANISH = MODMODE + "vanish";
    public static final String TOGGLE = MODMODE + "toggle";
    public static final String ADMIN = MODMODE + "admin";
    public static final String OP = MODMODE + "op";

    private final Permission API;

    Permissions() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Vault");
        if (plugin instanceof Vault) {
            RegisteredServiceProvider<Permission> svcProvider = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
            API = svcProvider.getProvider();
        } else {
            API = null;
            ModMode.log("LuckPerms could not be found. Is it disabled or missing?");
            Bukkit.getPluginManager().disablePlugin(ModMode.PLUGIN);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the player has Admin permissions.
     *
     * That is, the player has permissions in excess of those of the ModMode
     * permission group. This is a different concept from Permissions.OP,
     * which merely signifies that the player can administer this plugin.
     *
     * @return true for Admins, false for Moderators and default players.
     */
    boolean isAdmin(Player player) {
        return API.playerHas(player, ADMIN);
    }

    // ------------------------------------------------------------------------
    /**
     * Applies the given node to the given player.
     *
     * @param player the player.
     * @param node the node to apply.
     */
    void addNode(Player player, Node node) {
        if (node.getWorld() == null) {
            // global
            API.playerAddGroup(player, node.getName());
        } else {
            // per world
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
            API.playerAddGroup(node.getWorld().getName(), offlinePlayer, node.getName());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Removes the given node from the given player.
     *
     * @param player the player.
     * @param node the node to remove.
     */
    void removeNode(Player player, Node node) {
        if (node.getWorld() == null) {
            // global
            API.playerRemoveGroup(player, node.getName());
        } else {
            // per world
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
            API.playerRemoveGroup(node.getWorld().getName(), offlinePlayer, node.getName());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Returns all of a player's nodes as a set.
     *
     * @param player the player.
     * @param worlds the specific worlds, if any, to search; null for all worlds.
     * @return a set of nodes.
     */
    HashSet<Node> getNodes(Player player, Set<World> worlds) {
        HashSet<Node> nodes = new HashSet<>();
        if (worlds == null || worlds.size() == 0) {
            // global
            for (String group : API.getPlayerGroups(player)) {
                nodes.add(new Node(group, null));
            }
        } else {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
            for (World world : worlds) {
                for (String group : API.getPlayerGroups(world.getName(), offlinePlayer)) {
                    nodes.add(new Node(group, world));
                }
            }
        }
        return nodes;
    }

}
