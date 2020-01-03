package nu.nerd.modmode;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

// ----------------------------------------------------------------------------
/**
 * Utilities.
 */
public class Util {
    // ------------------------------------------------------------------------
    /**
     * Format a Location as a String for human consumption.
     * 
     * @param loc the location.
     * @return a String.
     */
    static String formatLocation(Location loc) {
        return "(" + loc.getWorld().getName() + ", " + loc.getX() + ", " + loc.getY() + ", " + loc.getZ() + ")";
    }

    // ------------------------------------------------------------------------
    /**
     * Format the Location of an Entity as a String for human consumption.
     * 
     * @param entity the Entity.
     * @return a String.
     */
    public static String formatLocation(Entity entity) {
        return formatLocation(entity.getLocation());
    }

} // class Util