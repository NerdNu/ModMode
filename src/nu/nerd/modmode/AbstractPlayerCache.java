package nu.nerd.modmode;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.UUID;

// ------------------------------------------------------------------------
/**
 * Represents an abstract cache of players.
 */
class AbstractPlayerCache {

    /**
     * The cache of players.
     */
    private final HashSet<UUID> CACHE = new HashSet<>();

    /**
     * This cache's YAML key, for (de)serialization.
     */
    private final String _configKey;

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param configKey this cache's YAML key, for (de)serialization.
     */
    AbstractPlayerCache(String configKey) {
        _configKey = configKey;
    }

    // ------------------------------------------------------------------------
    /**
     * Adds the given player to the cache.
     *
     * @param player the player.
     */
    synchronized void add(Player player) {
        CACHE.add(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Removes the given player from the cache.
     *
     * @param player the player.
     */
    synchronized void remove(Player player) {
        CACHE.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Returns true if the given player is in the cache.
     *
     * @param player the player.
     * @return true if the given player is in the cache.
     */
    synchronized boolean contains(Player player) {
        return CACHE.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Serializes the current cache to the configuration.
     *
     * @param config the configuration.
     */
    synchronized void save(FileConfiguration config) {
        config.set(_configKey, CACHE.toArray());
    }

    // ------------------------------------------------------------------------
    /**
     * Deserializes the cache contained in the configuration.
     *
     * @param config the configuration.
     */
    synchronized void load(FileConfiguration config) {
        CACHE.clear();
        config.getStringList(_configKey).stream()
                                        .map(UUID::fromString)
                                        .forEach(CACHE::add);
    }

}
