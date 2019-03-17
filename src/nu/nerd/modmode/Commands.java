package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Commands implements TabExecutor {

    public Commands() {
        Bukkit.getPluginCommand("modmode").setExecutor(this);
        Bukkit.getPluginCommand("unvanish").setExecutor(this);
        Bukkit.getPluginCommand("vanishlist").setExecutor(this);
        Bukkit.getPluginCommand("vanish").setExecutor(this);
        Bukkit.getPluginCommand("vanish").setPermission(Permissions.VANISH);
    }

    // ------------------------------------------------------------------------
    /**
     * @see CommandExecutor#onCommand(CommandSender, Command, String, String[]).
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        if (command.getName().equalsIgnoreCase("vanishlist")) {
            showVanishList(sender);
            return true;
        }

        if (!isInGame(sender)) {
            return true;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("modmode")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("kit")) {
                    HashMap<Integer, ItemStack> kit = ModMode.CONFIG.getModKit();
                    for (Integer i : kit.keySet()) {
                        PlayerInventory inventory = player.getInventory();
                        ItemStack item = inventory.getItem(i);
                        ItemStack kitItem = kit.get(i);
                        if (item != null && item.getType() != Material.AIR) {
                            inventory.addItem(kitItem).isEmpty();
                        } else {
                            inventory.setItem(i, kitItem);
                        }
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("savekit")) {
                    if (player.hasPermission(Permissions.OP)) {
                        ModMode.CONFIG.saveModKit(player.getInventory());
                        player.sendMessage(ChatColor.RED + "Mod kit saved.");
                    }
                    return true;
                }
            }
            cmdModMode(sender, args);
            return true;
        }

        boolean isVanished = ModMode.PLUGIN.isVanished(player);
        if (command.getName().equalsIgnoreCase("vanish")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
                player.sendMessage(String.format("%sYou are %s.", ChatColor.DARK_AQUA, isVanished ? "vanished" : "visible"));
            } else if (isVanished) {
                player.sendMessage(ChatColor.DARK_AQUA + "You are already vanished.");
            } else {
                ModMode.PLUGIN.setVanished(player, true);
            }
        } else if (command.getName().equalsIgnoreCase("unvanish")) {
            if (isVanished) {
                ModMode.PLUGIN.setVanished(player, false);
            } else {
                player.sendMessage(ChatColor.DARK_AQUA + "You are already visible.");
            }
        }
        return true;

    } // onCommand

    // ------------------------------------------------------------------------
    /**
     * Handle /modmode [save|reload] both for players in-game and the console.
     *
     * @param sender the command sender.
     * @param args command arguments.
     */
    private void cmdModMode(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (isInGame(sender)) {
                ModMode.PLUGIN.toggleModMode((Player) sender);
            }
        } else if (args.length == 1 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("reload"))) {
            if (!sender.hasPermission(Permissions.OP)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use /modmode op commands.");
                return;
            }
            if (args[0].equalsIgnoreCase("save")) {
                ModMode.CONFIG.save();
                sender.sendMessage(ChatColor.GOLD + "ModMode configuration saved.");
            } else if (args[0].equalsIgnoreCase("reload")) {
                ModMode.CONFIG.reload();
                sender.sendMessage(ChatColor.GOLD + "ModMode configuration reloaded.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /modmode [save | reload | kit | savekit]");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the CommandSender is a Player (in-game).
     *
     * If the command sender is not in game, tell them they need to be in-game
     * to use the current command.
     *
     * @param sender the command sender.
     * @return true if the CommandSender is a Player (in-game).
     */
    private boolean isInGame(CommandSender sender) {
        boolean inGame = (sender instanceof Player);
        if (!inGame) {
            sender.sendMessage("You need to be in-game to use this command.");
        }
        return inGame;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }

    // ------------------------------------------------------------------------
    /**
     * Sends a list of currently-vanished players to the given CommandSender.
     *
     * @param sender the CommandSender.
     */
    private void showVanishList(CommandSender sender) {
        if (ModMode.VANISHED.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "All players are visible!");
        } else {
            String vanishList = ModMode.VANISHED.stream().map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.joining(", "));
            sender.sendMessage(ChatColor.RED + "Vanished players: " + vanishList);
        }
    }

}
