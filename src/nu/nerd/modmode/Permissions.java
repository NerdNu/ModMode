package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Track;

// ----------------------------------------------------------------------------
/**
 * A wrapper around the LuckPerms API to provide only the abstractions we need.
 */
public class Permissions {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    Permissions() {
        RegisteredServiceProvider<LuckPermsApi> svcProvider = Bukkit.getServer().getServicesManager().getRegistration(LuckPermsApi.class);
        if (svcProvider != null) {
            API = svcProvider.getProvider();
            MODMODE_TRACK = getTrack(ModMode.CONFIG.MODMODE_TRACK_NAME);
            FOREIGN_SERVER_ADMINS_TRACK = getTrack(ModMode.CONFIG.FOREIGN_SERVER_ADMIN_MODMODE_TRACK_NAME);
        } else {
            API = null;
            ModMode.log("LuckPerms could not be found. Is it disabled or missing?");
            Bukkit.getPluginManager().disablePlugin(ModMode.PLUGIN);
        }
        if (MODMODE_TRACK == null) {
            ModMode.log("Track modmode-track could not be found.");
            Bukkit.getPluginManager().disablePlugin(ModMode.PLUGIN);
        }
        if (FOREIGN_SERVER_ADMINS_TRACK == null) {
            ModMode.log("Track modmode-track could not be found.");
            Bukkit.getPluginManager().disablePlugin(ModMode.PLUGIN);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the player has Admin permissions.
     *
     * That is, the player has permissions in excess of those of the ModMode
     * permission group. This is a different concept from Permissions.OP, which
     * merely signifies that the player can administer this plugin.
     *
     * @return true for Admins, false for Moderators and default players.
     */
    boolean isAdmin(Player player) {
        return player.hasPermission(ADMIN);
    }

    // ------------------------------------------------------------------------
    /**
     * Gets the Track with the given name, or null if it does not exist.
     *
     * @param name the name of the Track.
     * @return the Track, or null if it does not exist.
     */
    Track getTrack(String name) {
        if (API.isTrackLoaded(name)) {
            return API.getTrack(name);
        } else {
            return API.getTrackManager().loadTrack(name).join().orElse(null);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the modmode track if the player is a moderator, or the foreign
     * server admins modmode track if the player is a foreign server admin.
     *
     * @param player the player.
     * @return the modmode track if the player is a moderator, or the foreign
     *         server admins modmode track if the player is a foreign server
     *         admin.
     */
    private Track getAppropriateTrack(Player player) {
        if (player.hasPermission("group.foreignserveradmins")) {
            return FOREIGN_SERVER_ADMINS_TRACK;
        } else {
            return MODMODE_TRACK;
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Promotes the player along the track corresponding to their primary group.
     *
     * @param player the player to promote.
     */
    void promote(Player player) {
        Track track = getAppropriateTrack(player);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " promote " + track.getName());
    }

    // ------------------------------------------------------------------------
    /**
     * Demotes the player along the track corresponding to their primary group.
     *
     * @param player the player to demote.
     */
    void demote(Player player) {
        Track track = getAppropriateTrack(player);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " demote " + track.getName());
    }

    // ------------------------------------------------------------------------
    /**
     * Prefix of permission nodes.
     */
    private static final String MODMODE = "modmode.";

    /**
     * Permission to alter vanish state.
     */
    public static final String VANISH = MODMODE + "vanish";

    /**
     * Permission to toggle ModMode state.
     */
    public static final String TOGGLE = MODMODE + "toggle";

    /**
     * Tags the user as an admin, whose permissions will not change along a
     * track (because they are already in the ModMode group).
     * 
     * @TODO: This seems a bit redundant (review code).
     * @TODO: Check current permissions in light of this discovery.
     */
    public static final String ADMIN = MODMODE + "admin";

    /**
     * Permission to use administer the plugin as a console user/operator.
     */
    public static final String OP = MODMODE + "op";

    /**
     * LuckPerms API instance.
     */
    private final LuckPermsApi API;

    /**
     * Moderator -> ModMode Track instance.
     */
    private static Track MODMODE_TRACK;

    /**
     * Foreign Server Admin -> ModMode Track instance.
     */
    private static Track FOREIGN_SERVER_ADMINS_TRACK;

}