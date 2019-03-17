package nu.nerd.modmode;

import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Track;
import me.lucko.luckperms.api.User;
import me.lucko.luckperms.api.context.ContextSet;
import me.lucko.luckperms.api.event.EventBus;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Permissions {

    private final LuckPermsApi API;

    private final EventBus EVENT_BUS;

    private static Track MODMODE_TRACK;
    private static Track FOREIGN_SERVER_ADMINS_TRACK;

    Permissions(LuckPermsApi api) {
        API = api;
        EVENT_BUS = api.getEventBus();
        MODMODE_TRACK = getTrack("modmode-track");
        FOREIGN_SERVER_ADMINS_TRACK = getTrack("foreign-server-admins-modmode-track");
        if (MODMODE_TRACK == null) {
            ModMode.log("Track modmode-track could not be found.");
        }
        if (FOREIGN_SERVER_ADMINS_TRACK == null) {
            ModMode.log("Track modmode-track could not be found.");
        }
    }

    public static boolean canModMode(Player player) {
        return player.hasPermission(TOGGLE);
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
    public static boolean isAdmin(Player player) {
        return player.hasPermission(ADMIN);
    }

    // ------------------------------------------------------------------------
    /**
     * Gets the Track with the given name, or null if it does not exist.
     * According to the LuckPerms documentation, tracks are always kept loaded.
     *
     * @param name the name of the Track.
     * @return the Track, or null if it does not exist.
     */
    private Track getTrack(String name) {
        return API.getTrack(name);
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the modmode track if the player is a moderator, or the foreign
     * server admins modmode track if the player is a foreign server admin.
     *
     * @param player the player.
     * @return the modmode track if the player is a moderator, or the foreign
     * server admins modmode track if the player is a foreign server admin.
     */
    private Track getAppropriateTrack(Player player) {
        if (player.hasPermission("group.foreignserveradmins")) {
            return FOREIGN_SERVER_ADMINS_TRACK;
        } else {
            return MODMODE_TRACK;
        }
    }

    private CompletableFuture<User> getUser(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        if (API.isUserLoaded(uuid)) {
            return CompletableFuture.completedFuture(API.getUser(uuid));
        } else {
            return API.getUserManager().loadUser(uuid);
        }
    }

    CompletableFuture<Void> updatePermissions(Player player, boolean promote) {
        CompletableFuture<User> futureUser = getUser(player);
        if (isAdmin(player)) {
            return CompletableFuture.completedFuture(null);
        }
        return futureUser.thenComposeAsync(user -> CompletableFuture.supplyAsync(() -> {
                if (user != null && !isAdmin(player)) {
                    Track track = getAppropriateTrack(player);
                    if (promote) {
                        track.promote(user, ContextSet.empty());
                    } else {
                        track.demote(user, ContextSet.empty());
                    }
                    return user;
                }
                return null;
         })).thenAcceptAsync(u -> {
             if (u != null) {
                 API.getUserManager().saveUser(u);
             }
        });
    }

    private static final String MODMODE = "modmode.";
    public static final String VANISH = MODMODE + "vanish";
    public static final String TOGGLE = MODMODE + "toggle";
    public static final String ADMIN = MODMODE + "admin";
    public static final String OP = MODMODE + "op";

}