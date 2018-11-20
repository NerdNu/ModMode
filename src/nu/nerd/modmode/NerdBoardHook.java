package nu.nerd.modmode;

import nu.nerd.nerdboard.NerdBoard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

// ------------------------------------------------------------------------
/**
 * A class which encapsulates all of this plugin's hooked functionality
 * dependent on the NerdBoard plugin.
 */
final class NerdBoardHook {

    /**
     * The NerdBoard instance.
     */
    private static NerdBoard _nerdBoard;

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param nerdBoard the NerdBoard plugin.
     */
    NerdBoardHook(NerdBoard nerdBoard) {
        _nerdBoard = nerdBoard;
        _scoreboard = nerdBoard.getScoreboard();

        _modModeTeam = configureTeam("Mod Mode", ChatColor.GREEN);
        _vanishedTeam = configureTeam("Vanished", ChatColor.BLUE);
        _defaultTeam = configureTeam("Default", null);
    }

    // ------------------------------------------------------------------------
    /**
     * Configures a {@link Team} with the given name, color, and collision
     * status.
     *
     * @param name the name of the team.
     * @param color (nullable) the team's color (i.e. player name color).
     * @return a {@link Team} with the given properties.
     */
    private static Team configureTeam(String name, ChatColor color) {
        Team team = getOrCreateTeam(name);
        if (color != null) {
            team.setColor(color);
        }
        return team;
    }

    // ------------------------------------------------------------------------
    /**
     * Returns the NerdBoard plugin if found, otherwise null.
     *
     * @return the NerdBoard plugin if found, otherwise null.
     */
    static NerdBoard getNerdBoard() {
        Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("NerdBoard");
        if (plugin instanceof NerdBoard) {
            return (NerdBoard) plugin;
        }
        return null;
    }

    // ------------------------------------------------------------------------
    /**
     * Look up a Team by name in the Scoreboard, or create a new one with the
     * specified name if not found.
     *
     * @param name the Team name.
     * @return the Team with that name.
     */
    private static Team getOrCreateTeam(String name) {
        Team team = _scoreboard.getTeam(name);
        if (team == null) {
            team = _nerdBoard.addTeam(name);
        }
        return team;
    }

    // ------------------------------------------------------------------------
    /**
     * Assign the player to the Team that corresponds to its vanish and modmode
     * states, and then update their scoreboard if necessary.
     *
     * The Team controls the name tag prefix (colour) and collision detection
     * between players.
     *
     * @param player the player.
     */
    static void reconcilePlayerWithVanishState(Player player) {
        boolean inModMode = ModMode.PLUGIN.isModMode(player);
        boolean isVanished = ModMode.PLUGIN.isVanished(player);
        Team team = inModMode ? _modModeTeam : (isVanished ? _vanishedTeam
                                                           : _defaultTeam);
        _nerdBoard.addPlayerToTeam(team, player);
        if (player.getScoreboard() != _scoreboard) {
            player.setScoreboard(_scoreboard);
        }
    }

    /**
     * Scoreboard API stuff for colored name tags
     */
    private static Scoreboard _scoreboard;

    private static Team _modModeTeam;

    private static Team _vanishedTeam;

    /**
     * Players who are not in ModMode or vanished are added to this team so that
     * we can control their collidability. LivingEntity.setCollidable() is
     * broken: https://hub.spigotmc.org/jira/browse/SPIGOT-2069
     */
    private static Team _defaultTeam;

} // NerdBoardHook
