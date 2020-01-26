package nu.nerd.modmode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

// ----------------------------------------------------------------------------
/**
 * Caches a set of players that are stored as a list of their UUIDs in a YAML
 * configuration.
 */
public class PlayerUuidSet {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param configKey this cache's YAML key, for (de)serialization.
     */
    public PlayerUuidSet(String configKey) {
        _configKey = configKey;
    }

    // ------------------------------------------------------------------------
    /**
     * Adds the given player to the cache.
     *
     * @param player the player.
     */
    public void add(Player player) {
        _uuids.add(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Removes the given player from the cache.
     *
     * @param player the player.
     */
    public void remove(Player player) {
        _uuids.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Returns true if the given player is in the cache.
     *
     * @param player the player.
     * @return true if the given player is in the cache.
     */
    public boolean contains(Player player) {
        return _uuids.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Serializes the current cache to the configuration.
     *
     * @param config the configuration.
     */
    public void save(FileConfiguration config) {
        config.set(_configKey, new ArrayList<>(_uuids.stream().map(UUID::toString).collect(Collectors.toSet())));
    }

    // ------------------------------------------------------------------------
    /**
     * Deserializes the cache contained in the configuration.
     *
     * @param config the configuration.
     */
    public void load(FileConfiguration config) {
        _uuids = config.getStringList(this._configKey).stream().map(UUID::fromString).collect(Collectors.toCollection(HashSet::new));
    }

    // ------------------------------------------------------------------------
    /**
     * The cache of players.
     */
    private HashSet<UUID> _uuids = new HashSet<>();

    /**
     * This cache's YAML key, for (de)serialization.
     */
    private final String _configKey;
}
