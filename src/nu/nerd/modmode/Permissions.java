package nu.nerd.modmode;

import net.luckperms.api.context.ImmutableContextSet;
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

    /**
     * Constructor.
     */
    Permissions(ModMode plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<LuckPerms> svcProvider = Bukkit.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (svcProvider != null) {
            _luckpermsAPI = svcProvider.getProvider();
        } else {
            _luckpermsAPI = null;
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
        if (_luckpermsAPI.getTrackManager().isLoaded(name)) {
            return _luckpermsAPI.getTrackManager().getTrack(name);
        } else {
            return _luckpermsAPI.getTrackManager().loadTrack(name).join().orElse(null);
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
        System.out.println("Promoting player " + player.getName() + " in group " + group.getName());
        Track track = getAppropriateTrack(group);
        User luckpermsPlayer = _luckpermsAPI.getUserManager().getUser(player.getUniqueId());
        if(track != null && luckpermsPlayer != null) {
            System.out.println("Got track and user");
            Result result = track.promote(luckpermsPlayer, ImmutableContextSet.empty());
            if(result.wasSuccessful()) {
                System.out.println("Promotion successful");
                _luckpermsAPI.getUserManager().saveUser(luckpermsPlayer);
                return true;
            }
        }
        System.out.println("Failed to promote player " + player.getName() + " in group " + group.getName());
        return false;
    }

    // ------------------------------------------------------------------------
    /**
     * Demotes the player along the track corresponding to their group.
     *
     * @param player the player to demote.
     */
    public boolean demote(Player player, ModModeGroup group) {
        System.out.println("Demoting player " + player.getName() + " in group " + group.getName());
        Track track = getAppropriateTrack(group);
        User luckpermsPlayer = _luckpermsAPI.getUserManager().getUser(player.getUniqueId());
        if(track != null && luckpermsPlayer != null) {
            System.out.println("Got track and user");
            Result result = track.demote(luckpermsPlayer, ImmutableContextSet.empty());
            if(result.wasSuccessful()) {
                System.out.println("Demotion successful");
                _luckpermsAPI.getUserManager().saveUser(luckpermsPlayer);
                return true;
            }
        }
        System.out.println("Failed to demote player " + player.getName() + " in group " + group.getName());
        return false;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns an instance of the LuckPerms API.
     * @return an instance of the LuckPerms API.
     */
    public LuckPerms getLuckPermsAPI() {
        return _luckpermsAPI;
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
    private LuckPerms _luckpermsAPI;

}