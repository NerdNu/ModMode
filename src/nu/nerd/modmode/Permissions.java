package nu.nerd.modmode;

import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

public class Permissions {

    private final LuckPermsApi API;

    Permissions() {
        RegisteredServiceProvider<LuckPermsApi> svcProvider = Bukkit.getServer().getServicesManager().getRegistration(LuckPermsApi.class);
        if (svcProvider != null) {
            API = svcProvider.getProvider();
        } else {
            API = null;
            ModMode.log("LuckPerms could not be found. Is it disabled or missing?");
            Bukkit.getPluginManager().disablePlugin(ModMode.PLUGIN);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Returns a User instance corresponding to the given player.
     *
     * @param player the player.
     * @return the User instance.
     */
    private User getUser(Player player) {
        UUID uuid = player.getUniqueId();
        if (API.isUserLoaded(uuid)) {
            return API.getUser(uuid);
        } else {
            return API.getUserManager().loadUser(uuid).join();
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Returns a Node instance for the given group. Consults cache before
     * doing a lookup.
     *
     * @param group the group.
     * @return the node.
     */
    private Node getGroupNode(String group) {
        if (_nodeCache.containsKey(group)) {
            return _nodeCache.get(group);
        } else {
            Node node = API.getNodeFactory()
                           .makeGroupNode(group)
                           .build();
            _nodeCache.put(group, node);
            return node;
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
        return player.hasPermission(ADMIN);
    }

    // ------------------------------------------------------------------------
    /**
     * Adds the player to the given group.
     *
     *  @param player the player.
     * @param group the group to add.
     */
    void addGroup(Player player, String group) {
        User user = getUser(player);
        Node node = getGroupNode(group);
        user.setPermission(node);
    }

    // ------------------------------------------------------------------------
    /**
     * Removes the player from the given group.
     *
     *  @param player the player.
     * @param group the group to remove.
     */
    void removeGroup(Player player, String group) {
        User user = getUser(player);
        Node node = getGroupNode(group);
        user.unsetPermission(node);
    }

    // ------------------------------------------------------------------------
    /**
     * Returns all of a player's groups as a set.
     *
     * @param player the player.
     * @return a set of groups.
     */
    HashSet<String> getGroups(Player player) {
        User user = getUser(player);
        return user.getAllNodes()
                   .stream()
                   .filter(Node::isGroupNode)
                   .map(Node::getGroupName)
                   .collect(Collectors.toCollection(HashSet::new));
    }

    // ------------------------------------------------------------------------
    /**
     * A cache of previously built Nodes.
     */
    private final HashMap<String, Node> _nodeCache = new HashMap<>();

    private static final String MODMODE = "modmode.";
    public static final String VANISH = MODMODE + "vanish";
    public static final String TOGGLE = MODMODE + "toggle";
    public static final String ADMIN = MODMODE + "admin";
    public static final String OP = MODMODE + "op";

}