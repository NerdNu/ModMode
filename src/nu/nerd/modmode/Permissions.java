package nu.nerd.modmode;

import org.bukkit.entity.Player;


public class Permissions {
    private final static String _MODMODE          = "modmode";

    public final static String VANISH             = _MODMODE + ".vanish";
    public final static String VANISH_LIST        = VANISH   + ".list";
    public final static String VANISH_SHOWMODS    = VANISH   + ".showmods";
    public final static String VANISH_SHOWALL     = VANISH   + ".showall";
    public final static String UNVANISH           = _MODMODE + ".unvanish";
    public final static String UNVANISH_UNLIMITED = UNVANISH + ".unlimited";

    public final static String MODMODE_TOGGLE     = _MODMODE + ".toggle";


    private Permissions() {}

    public static boolean hasPermission (Player player, String node) {
        return player.hasPermission(node);
    }
}
