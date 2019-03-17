package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;

public class PlayerState {

    // ------------------------------------------------------------------------
    /**
     * Return the File used to store the player's normal or ModMode state.
     *
     * @param player the player.
     * @param isModMode true if the data is for the ModMode state.
     * @return the File used to store the player's normal or ModMode state.
     */
    private static File getStateFile(Player player, boolean isModMode) {
        File playersDir = new File(ModMode.PLUGIN.getDataFolder(), "players");
        playersDir.mkdirs();

        String fileName = player.getUniqueId().toString() + ((isModMode) ? "_modmode" : "_normal") + ".yml";
        return new File(playersDir, fileName);
    }

    // ------------------------------------------------------------------------
    /**
     * Save the player's data to a YAML configuration file.
     *
     * @param player the player.
     * @param isModMode true if the saved data is for the ModMode inventory.
     */
    static void savePlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);
        if (ModMode.CONFIG.DEBUG) {
            ModMode.log("savePlayerData(): " + stateFile);
        }

        // Keep 2 backups of saved player data.
        try {
            File backup1 = new File(stateFile.getPath() + ".1");
            File backup2 = new File(stateFile.getPath() + ".2");
            backup1.renameTo(backup2);
            stateFile.renameTo(backup1);
        } catch (Exception ex) {
            String msg = " raised saving state file backups for " + player.getName()
                         + " (" + player.getUniqueId().toString() + ").";
            ModMode.log(ex.getClass().getName() + msg);
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("health", player.getHealth());
        config.set("food", player.getFoodLevel());
        config.set("experience", player.getLevel() + player.getExp());
        config.set("world", player.getLocation().getWorld().getName());
        config.set("x", player.getLocation().getX());
        config.set("y", player.getLocation().getY());
        config.set("z", player.getLocation().getZ());
        config.set("pitch", player.getLocation().getPitch());
        config.set("yaw", player.getLocation().getYaw());
        config.set("helmet", player.getInventory().getHelmet());
        config.set("chestplate", player.getInventory().getChestplate());
        config.set("leggings", player.getInventory().getLeggings());
        config.set("boots", player.getInventory().getBoots());
        config.set("off-hand", player.getInventory().getItemInOffHand());
        for (PotionEffect potion : player.getActivePotionEffects()) {
            config.set("potions." + potion.getType().getName(), potion);
        }
        ItemStack[] inventory = player.getInventory().getContents();
        for (int slot = 0; slot < inventory.length; ++slot) {
            config.set("inventory." + slot, inventory[slot]);
        }

        ItemStack[] enderChest = player.getEnderChest().getContents();
        for (int slot = 0; slot < enderChest.length; ++slot) {
            config.set("enderchest." + slot, enderChest[slot]);
        }

        try {
            config.save(stateFile);
        } catch (Exception exception) {
            ModMode.log("Failed to save player data for " + player.getName() + "(" + player.getUniqueId().toString() + ")");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Load the player's data from a YAML configuration file.
     *
     * @param player the player.
     * @param isModMode true if the loaded data is for the ModMode inventory.
     */
    static void loadPlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);
        if (ModMode.CONFIG.DEBUG) {
            ModMode.log("loadPlayerData(): " + stateFile);
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(stateFile);
            player.setHealth(config.getDouble("health"));
            player.setFoodLevel(config.getInt("food"));
            float level = (float) config.getDouble("experience");
            player.setLevel((int) Math.floor(level));
            player.setExp(level - player.getLevel());

            if (!isModMode) {
                String world = config.getString("world");
                double x = config.getDouble("x");
                double y = config.getDouble("y");
                double z = config.getDouble("z");
                float pitch = (float) config.getDouble("pitch");
                float yaw = (float) config.getDouble("yaw");
                player.teleport(new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch));
            }

            player.getEnderChest().clear();
            player.getInventory().clear();
            player.getInventory().setHelmet(config.getItemStack("helmet"));
            player.getInventory().setChestplate(config.getItemStack("chestplate"));
            player.getInventory().setLeggings(config.getItemStack("leggings"));
            player.getInventory().setBoots(config.getItemStack("boots"));
            player.getInventory().setItemInOffHand(config.getItemStack("off-hand"));

            for (PotionEffect potion : player.getActivePotionEffects()) {
                player.removePotionEffect(potion.getType());
            }

            ConfigurationSection potions = config.getConfigurationSection("potions");
            if (potions != null) {
                for (String key : potions.getKeys(false)) {
                    PotionEffect potion = (PotionEffect) potions.get(key);
                    player.addPotionEffect(potion);
                }
            }

            ConfigurationSection inventory = config.getConfigurationSection("inventory");
            if (inventory != null) {
                for (String key : inventory.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack item = inventory.getItemStack(key);
                        player.getInventory().setItem(slot, item);
                    } catch (Exception ex) {
                        ModMode.log("[ModMode] Exception while loading " + player.getName() + "'s inventory: " + ex);
                    }
                }
            }

            ConfigurationSection enderChest = config.getConfigurationSection("enderchest");
            if (enderChest != null) {
                for (String key : enderChest.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack item = enderChest.getItemStack(key);
                        player.getEnderChest().setItem(slot, item);
                    } catch (Exception ex) {
                        ModMode.log("[ModMode] Exception while loading " + player.getName() + "'s ender chest: " + ex);
                    }
                }
            }
        } catch (Exception ex) {
            ModMode.log("Failed to load player data for " + player.getName() + "(" + player.getUniqueId().toString() + ")");
        }
    }

}
