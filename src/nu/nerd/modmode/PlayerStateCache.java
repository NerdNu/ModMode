package nu.nerd.modmode;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

class PlayerStateCache {

    private PlayerStateCache() { }

    // ------------------------------------------------------------------------
    /**
     * Return the persistent (cross login) vanish state.
     *
     * This means different things depending on whether the player could
     * normally vanish without being in ModMode.
     *
     * @see #VANISHED
     *
     * @param player the player.
     * @return true if vanished.
     */
    static boolean getPersistentVanishState(Player player) {
        return VANISHED.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Save the current vanish state of the player as his persistent vanish
     * state.
     *
     * This means different things depending on whether the player could
     * normally vanish without being in ModMode.
     *
     * @see #VANISHED
     */
    static void setPersistentVanishState(Player player) {
        if (ModMode.PLUGIN.isVanished(player)) {
            VANISHED.add(player.getUniqueId());
        } else {
            VANISHED.remove(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the player is currently in ModMode.
     *
     * @param player the Player.
     * @return true if the player is currently in ModMode.
     */
    static boolean inModMode(Player player) {
        return MODMODE.contains(player.getUniqueId());
    }

    static void toggleModModeState(Player player) {
        setModModeState(player, !inModMode(player));
    }

    static void setModModeState(Player player, boolean state) {
        if (state) {
            MODMODE.add(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "You are now in ModMode!");
        } else {
            MODMODE.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "You are no longer in ModMode!");
        }
    }

    static boolean joinedVanished(Player player) {
        return JOINED_VANISHED.contains(player.getUniqueId());
    }

    static void setJoinedVanished(Player player, boolean state) {
        if (state) {
            JOINED_VANISHED.add(player.getUniqueId());
        } else {
            JOINED_VANISHED.remove(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Serialize the Vanished and ModMode cache to config.
     *
     * @param config the {@link FileConfiguration}.
     */
    static void serialize(FileConfiguration config) {
        config.set("vanished", VANISHED.stream()
                                       .map(UUID::toString)
                                       .collect(Collectors.toList()));
        config.set("modmode", MODMODE.stream()
                                     .map(UUID::toString)
                                     .collect(Collectors.toList()));
    }

    static void load(FileConfiguration config) {
        // TODO make copy and remove instead of clearing
        VANISHED.clear();
        MODMODE.clear();
        config.getStringList("vanished").stream()
                                        .map(UUID::fromString)
                                        .forEach(VANISHED::add);
        config.getStringList("modmode").stream()
                                       .map(UUID::fromString)
                                       .forEach(MODMODE::add);
    }

    /**
     * For Moderators in ModMode, this is persistent storage for their vanish
     * state when they log out. Moderators out of ModMode are assumed to always
     * be visible.
     *
     * For Admins who can vanish without transitioning into ModMode, this
     * variable stores their vanish state between logins only when not in
     * ModMode. If they log out in ModMode, they are re-vanished automatically
     * when they log in. When they leave ModMode, their vanish state is set
     * according to membership in this set.
     *
     * This is NOT the set of currently vanished players, which is instead
     * maintained by the VanishNoPacket plugin.
     */
    private static final HashSet<UUID> VANISHED = new HashSet<>();

    /**
     * A set of all players currently in ModMode.
     */
    private static final HashSet<UUID> MODMODE = new HashSet<>();

    private static final HashSet<UUID> JOINED_VANISHED = new HashSet<>();

}
