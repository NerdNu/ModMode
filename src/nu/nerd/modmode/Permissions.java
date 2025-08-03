package nu.nerd.modmode;

import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Result;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.track.Track;

// ----------------------------------------------------------------------------
/**
 * A wrapper around the LuckPerms API to provide only the abstractions we need.
 */
public class Permissions {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    Permissions(ModMode plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<LuckPerms> svcProvider = Bukkit.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (svcProvider != null) {
            _api = svcProvider.getProvider();
            contextSet = MutableContextSet.create();
        } else {
            _api = null;
            plugin.logError("LuckPerms could not be found. Is it disabled or missing?");
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Gets the Track with the given name, or null if it does not exist.
     *
     * @param name the name of the Track.
     * @return the Track, or null if it does not exist.
     */
    Track getTrack(String name) {
        if (_api.getTrackManager().isLoaded(name)) {
            return _api.getTrackManager().getTrack(name);
        } else {
            return _api.getTrackManager().loadTrack(name).join().orElse(null);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the instance of the specified group's track from the LuckPerms API.
     *
     * @return the instance of the specified group's track from the LuckPerms API.
     */
    private Track getAppropriateTrack(ModModeGroup group) {
        String trackName = group.getTrackName();
        Track track = getTrack(trackName);
        if (track == null) {
            plugin.logError("Track \"" + trackName + "\" could not be loaded!");
        }
        return track;
    }

    // ------------------------------------------------------------------------
    /**
     * Promotes the player along the track corresponding to their group.
     *
     * @param player the player to promote.
     */
    public boolean promote(Player player, ModModeGroup group) {
        Track track = getAppropriateTrack(group);
        User luckpermsPlayer = _api.getUserManager().getUser(player.getUniqueId());
        if(track != null && luckpermsPlayer != null) {
            Result result = track.promote(luckpermsPlayer, contextSet);
            return result.wasSuccessful();
        }
        plugin.stateFail(player, player.getUniqueId());
        return false;
    }

    // ------------------------------------------------------------------------
    /**
     * Demotes the player along the track corresponding to their group.
     *
     * @param player the player to demote.
     */
    public boolean demote(Player player, ModModeGroup group) {
        Track track = getAppropriateTrack(group);
        User luckpermsPlayer = _api.getUserManager().getUser(player.getUniqueId());
        if(track != null && luckpermsPlayer != null) {
            Result result = track.demote(luckpermsPlayer, contextSet);
            return result.wasSuccessful();
        }
        plugin.stateFail(player, player.getUniqueId());
        return false;
    }

    // ------------------------------------------------------------------------

    /**
     * The instance of the plugin.
     */
    private ModMode plugin;

    /**
     * Prefix of permission nodes.
     */
    private static final String MODMODE = "modmode.";

    /**
     * Permission to alter vanish state.
     */
    public static final String VANISH = MODMODE + "vanish";

    /**
     * LuckPerms API instance.
     */
    private LuckPerms _api;

    /**
     * The LuckPerms context for promotions/demotions
     */
    private MutableContextSet contextSet;

}