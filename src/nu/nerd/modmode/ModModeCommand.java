package nu.nerd.modmode;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class ModModeCommand extends BukkitCommand {

    private boolean interactWithItems;
    private ModMode plugin;
    private ModModeGroup parentGroup;

    public ModModeCommand(String name, String description, String permission, List<String> aliases,
                          boolean interactWithItems, ModMode plugin) {
        super(name);
        this.description = description;
        this.usageMessage = "You seem to have made an error. This command is run like: /" + name + " [on/off]";
        this.setPermission(permission);
        this.setAliases(aliases);
        this.interactWithItems = interactWithItems;
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean execute(@NotNull CommandSender commandSender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (!(commandSender instanceof Player player)) return false;

        UUID uuid = player.getUniqueId();
        ModModeGroup currentGroup = plugin.getGroupPlayerMemberOf(uuid);

        // Permission check
        if (!player.hasPermission(getPermission())) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        boolean isInGroup = currentGroup != null;
        boolean isInParentGroup = false;
        if (isInGroup) isInParentGroup = currentGroup.equals(parentGroup);

        // --- No arguments: toggle group membership ---
        if (args.length == 0) {
            if (currentGroup != null) {
                if (currentGroup.equals(parentGroup)) {
                    parentGroup.demote(player, uuid);
                } else {
                    String currentGroupName = currentGroup.getName();
                    player.sendMessage(Component.text("You're already in " + currentGroupName + "! Run /"
                            + currentGroupName + " to leave.", NamedTextColor.RED));
                }
            } else {
                parentGroup.promote(player, uuid);
            }
            return true;
        }

        // --- Handle 1 or 2 arguments ---
        String firstArg = args[0].toLowerCase();

        // iteminteract branch
        if (firstArg.equals("iteminteract")) {
            if(currentGroup != null && currentGroup.equals(parentGroup)) {
                if (parentGroup.isInteractWithItems()) {
                    if (args.length == 1) {
                        // Toggle item interaction
                        interactWithItems = !currentGroup.isBypassingItemBlock(uuid);
                        player.sendMessage(Component.text("Item interaction is now " +
                                (interactWithItems ? "enabled" : "disabled"), NamedTextColor.YELLOW));
                        if(interactWithItems) {
                            currentGroup.addBypassingItemBlock(uuid);
                        } else {
                            currentGroup.removeBypassingItemBlock(uuid);
                        }
                        return true;
                    } else if (args.length == 2) {
                        switch (args[1].toLowerCase()) {
                            case "on":
                                interactWithItems = true;
                                currentGroup.addBypassingItemBlock(uuid);
                                break;
                            case "off":
                                interactWithItems = false;
                                currentGroup.removeBypassingItemBlock(uuid);
                                break;
                            default:
                                player.sendMessage(Component.text("Usage: /" + commandLabel +
                                        " iteminteract [on/off]", NamedTextColor.RED));
                                return true;
                        }
                        player.sendMessage(Component.text("Item interaction is now " +
                                (interactWithItems ? "enabled" : "disabled"), NamedTextColor.YELLOW));
                        return true;
                    }
                } else {
                    player.sendMessage(Component.text("This group doesn't allow item interaction toggle.",
                            NamedTextColor.RED));
                }
            } else {
                player.sendMessage(Component.text("You're not in this group.", NamedTextColor.RED));
            }
        }

        // group on/off/help branch
        if (args.length == 1) {
            switch (firstArg) {
                case "on":
                    if (!isInGroup) {
                        parentGroup.promote(player, uuid);
                    } else {
                        String currentGroupName = currentGroup.getName();
                        player.sendMessage(Component.text("You're already in " + currentGroupName + "! Run /"
                                + currentGroupName + " to leave.", NamedTextColor.RED));
                    }
                    break;
                case "off":

                    if (isInGroup) {
                        if (isInParentGroup) {
                            parentGroup.demote(player, uuid);
                            return true;
                        }
                    }
                    player.sendMessage(Component.text("You are not in this mode.", NamedTextColor.RED));
                    break;
                case "help":
                    help(player);
                    break;
                default:
                    player.sendMessage(Component.text("Usage: /" + commandLabel + " [on/off] or /"
                            + commandLabel + " iteminteract [on/off]", NamedTextColor.RED));
            }
            return true;
        }

        // If none matched
        player.sendMessage(Component.text("Usage: /" + commandLabel + " [on/off] or /" + commandLabel +
                " iteminteract [on/off]", NamedTextColor.RED));
        return true;
    }

    // ------------------------------------------------------------------------

    /**
     * The method that's called when '/[command] help' is run. Outputs the help text to the player and dynamically
     * generates it based on multiple conditions.
     * @param player The player running the command.
     */
    private void help(Player player) {

        String header = this.getName().substring(0, 1).toUpperCase() + this.getName().substring(1) + " help";

        Component message = Component.text(header, NamedTextColor.GREEN)
                .appendNewline()
                // Line under header
                .append(Component.text(StringUtils.repeat('-', header.length()), NamedTextColor.BLUE))
                // Toggle command
                .appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                        .append(Component.text("/" + this.getName(), NamedTextColor.GRAY)
                                .append(Component.text(" - Toggle " + parentGroup.getName() + ".", NamedTextColor.GREEN))))
                // On command
                .appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                        .append(Component.text("/" + this.getName() + " on ", NamedTextColor.GRAY)
                                .append(Component.text("- Enable " + parentGroup.getName() + ".", NamedTextColor.GREEN))))
                // Off command
                .appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                        .append(Component.text("/" + this.getName() + " off ", NamedTextColor.GRAY)
                                .append(Component.text("- Enable " + parentGroup.getName() + ".", NamedTextColor.GREEN))))
                // Vanish command
                .appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                        .append(Component.text("/vanish ", NamedTextColor.GRAY)
                                .append(Component.text("- Enable " + parentGroup.getName() + ".", NamedTextColor.GREEN))))
                // Unvanish command
                .appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                        .append(Component.text("/unvanish ", NamedTextColor.GRAY)
                                .append(Component.text("- Enable " + parentGroup.getName() + ".", NamedTextColor.GREEN))))
                .appendNewline().appendNewline().append(Component.text("Aliases:", NamedTextColor.BLUE));

        // Show all aliases of the mode command defined in config.yml.
        if(!this.getAliases().isEmpty()) {
            for(String alias : this.getAliases()) {
                message = message.appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                        .append(Component.text("/" + alias + " [on/off]", NamedTextColor.GRAY)
                                .append(Component.text(" - Same functionality as main command.", NamedTextColor.GREEN))));
            }
        } else {
            message = message.appendNewline().append(Component.text("None!", NamedTextColor.GRAY));
        }

        if(parentGroup.isInteractWithItems() || player.hasPermission("modmode.op")) {
            message = message.appendNewline().appendNewline().append(Component.text("Additional Commands:", NamedTextColor.BLUE));
        }

        // If the group has item interaction enabled, display the extra command available.
        if(parentGroup.isInteractWithItems()) {
            message = message.appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                    .append(Component.text("/" + this.getName() + " iteminteract [on/off]", NamedTextColor.GRAY)
                            .append(Component.text(" - Toggle/enable/disable the ability to pick up and drop items.",
                                    NamedTextColor.GREEN))));
        }

        if(player.hasPermission("modmode.op")) {
            message = message.appendNewline().append(Component.text("- ", NamedTextColor.GREEN)
                    .append(Component.text("/reloadmodmode", NamedTextColor.GRAY)
                            .append(Component.text(" - Reloads the plugin and updates groups with any new information.",
                                    NamedTextColor.GREEN))));
        }

        player.sendMessage(message);

    }

    // ------------------------------------------------------------------------

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender commandSender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("on", "off", "help"));
            if (parentGroup.isInteractWithItems()) {
                options.add("iteminteract");
            }
            return options.stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("iteminteract")) {
            return Stream.of("on", "off")
                    .filter(opt -> opt.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    /**
     * Pairs this command instance to the instance of the group it supports.
     * @param group The group this command supports.
     */
    public void setParentGroup(ModModeGroup group) {
        this.parentGroup = group;
    }

}
