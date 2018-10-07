package nu.nerd.modmode;

import com.sun.istack.internal.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

// ------------------------------------------------------------------------
/**
 * A node represents an abstract permission coupled with a Bukkit World. In
 * essence a node is a permission with context. If a node is not coupled
 * with a World, it is considered a global node and will apply (and remove)
 * in (from) all worlds.
 */
public class Node {

    /**
     * The name of this node, e.g. the name of the group.
     */
    private final String _name;

    /**
     * The world in which this node should apply. Null for global.
     */
    private final World _world;

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param name the name of the node, e.g. the name of the group.
     * @param world the world in which this node should apply (null for global).
     */
    Node(String name, @Nullable World world) {
        _name = name;
        _world = world;
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the name of this node.
     *
     * @return the name of this node.
     */
    String getName() {
        return _name;
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the world in which this node should apply, or null if global.
     *
     * @return the world in which this node should apply, or null if global.
     */
    World getWorld() {
        return _world;
    }

    // ------------------------------------------------------------------------
    /**
     * Applies this node to the given player.
     *
     * @param player the player.
     */
    void apply(Player player) {
        ModMode.getPermissions().addNode(player, this);
    }

    // ------------------------------------------------------------------------
    /**
     * Removes this node from the given player.
     *
     * @param player the player.
     */
    void remove(Player player) {
        ModMode.getPermissions().removeNode(player, this);
    }

    // ------------------------------------------------------------------------
    /**
     * Returns a string representing this node for YAML serialization.
     *
     * @return a string representing this node for YAML serialization.
     */
    String serialize() {
        return _name + ":" + _world.getName();
    }

    // ------------------------------------------------------------------------
    /**
     * Converts a serialized node into a Node object based on the serialized
     * information.
     *
     * @param node the serialized node.
     * @return a Node object based on the serialized information.
     */
    static Node deserialize(String node) {
        String[] parts = node.split(":");
        try {
            World world = Bukkit.getWorld(parts[1]);
            return new Node(parts[0], world);
        } catch (Exception e) {
            // failed to deserialize
            e.printStackTrace();
            return null;
        }
    }

}
