package nu.nerd.modmode;

import org.bukkit.entity.Player;

public class Permissions {
    private static final String MODMODE = "modmode.";

    public static final String VANISH = MODMODE + "vanish";
    public static final String FULLVANISH = MODMODE + "fullvanish";
    public static final String UNVANISH = MODMODE + "unvanish";
    public static final String SHOWVANISHED = MODMODE + "showvanished";
    public static final String SHOWMODS = MODMODE + "showmods";

    public static final String TOGGLE = MODMODE + "toggle";

    public static boolean hasPermission(Player player, String node) {
        return player.hasPermission(node);
    }
}
