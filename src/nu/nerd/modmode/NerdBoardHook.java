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
    private final NerdBoard _nerdBoard;

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param nerdBoard the NerdBoard plugin.
     */
    NerdBoardHook(NerdBoard nerdBoard) {
        _nerdBoard = nerdBoard;
        _scoreboard = nerdBoard.getScoreboard();

        _modModeTeam = configureTeam("Mod Mode", ChatColor.GREEN, ModMode.CONFIG.ALLOW_COLLISIONS);
        _vanishedTeam = configureTeam("Vanished", ChatColor.BLUE, ModMode.CONFIG.ALLOW_COLLISIONS);
        _defaultTeam = configureTeam("Default", null, false);
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
     * Configures a {@link Team} with the given name, color, and collision
     * status.
     *
     * @param name the name of the team.
     * @param color (nullable) the team's color (i.e. player name color).
     * @param collisions if entity collisions should be enabled for
     *                        players on this team.
     * @return a {@link Team} with the given properties.
     */
    private Team configureTeam(String name, ChatColor color, boolean collisions) {
        Team team = getOrCreateTeam(name);
        if (color != null) {
            team.setColor(color);
        }
        team.setOption(Team.Option.COLLISION_RULE, boolToStatus(collisions));
        return team;
    }

    // ------------------------------------------------------------------------
    /**
     * Look up a Team by name in the Scoreboard, or create a new one with the
     * specified name if not found.
     *
     * @param name the Team name.
     * @return the Team with that name.
     */
    private Team getOrCreateTeam(String name) {
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
    @SuppressWarnings("deprecation")
    void reconcilePlayerWithVanishState(Player player) {
        boolean inModMode = ModMode.PLUGIN.isModMode(player);
        boolean isVanished = ModMode.PLUGIN.isVanished(player);
        Team team;
        if (inModMode) {
            team = _modModeTeam;
        } else {
            team = isVanished ? _vanishedTeam : _defaultTeam;
        }
        _nerdBoard.addPlayerToTeam(team, player);
        if (player.getScoreboard() != _scoreboard) {
            player.setScoreboard(_scoreboard);
        }
    }

    /**
     * Translates a boolean to an {@link org.bukkit.scoreboard.Team.OptionStatus}
     * with the mapping:
     *      true -> OptionStatus.ALWAYS
     *      false -> OptionStatus.NEVER
     *
     * @param bool the boolean.
     * @return the translated OptionStatus.
     */
    private static Team.OptionStatus boolToStatus(boolean bool) {
        return bool ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;
    }

    private Scoreboard _scoreboard;
    private Team _modModeTeam;
    private Team _vanishedTeam;

    /**
     * Players who are not in ModMode or vanished are added to this team so that
     * we can control their collidability. LivingEntity.setCollidable() is
     * broken: https://hub.spigotmc.org/jira/browse/SPIGOT-2069
     */
    private Team _defaultTeam;

} // NerdBoardHook
