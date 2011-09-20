package cc.co.traviswatkins;

import com.nijiko.permissions.PermissionHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;


public class Permissions {
    public static PermissionHandler permissionHandler;
    public static boolean useNewPermissions = false;

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
        // setup permissions handler on first call
        if (permissionHandler == null && !Permissions.useNewPermissions) {
            Plugin permissionsPlugin = Bukkit.getServer().getPluginManager().getPlugin("Permissions");

            if (permissionsPlugin != null) {
                permissionHandler = ((com.nijikokun.bukkit.Permissions.Permissions) permissionsPlugin).getHandler();
            }
            else {
                useNewPermissions = true;
            }
        }

        if (useNewPermissions) {
            return player.hasPermission(node);
        }
        else {
            return permissionHandler.has(player, node);
        }
    }
}
