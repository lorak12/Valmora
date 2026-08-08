package org.nakii.valmora.module.reforge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.item.Rarity;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin/debug command for the Reforge module — previously reachable only through the Anvil/Forge
 *  GUIs, with no way to inspect, force-apply, or reset a reforge from the console/command line
 *  (see docs/IMPLEMENTATION_BACKLOG.md, Reforge module). */
public class ReforgeCommand implements TabExecutor {

    private final Valmora plugin;

    public ReforgeCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    private ReforgeModule module() {
        return plugin.getReforgeModule();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("valmora.admin")) {
            sender.sendMessage(Formatter.format("<red>You don't have permission to use this command."));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "preview" -> handlePreview(sender, args);
            case "force" -> handleForce(sender, args);
            case "reset" -> handleReset(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleList(CommandSender sender) {
        ReforgeModule rm = module();
        sender.sendMessage(Formatter.format("<gold><bold>REFORGES:"));
        if (rm.getDefinitions().isEmpty()) {
            sender.sendMessage(Formatter.format(" <gray>No reforges registered."));
            return;
        }
        for (ReforgeDefinition def : rm.getDefinitions()) {
            String types = def.getApplicableTypes().isEmpty() ? "All"
                    : def.getApplicableTypes().stream().map(ItemType::name).collect(Collectors.joining(", "));
            sender.sendMessage(Formatter.format(" <gray>- <white>" + def.getId()
                    + " <dark_gray>(" + def.getName() + ", applies to: " + types + ", weight " + def.getWeight() + ")"));
        }
    }

    private void handlePreview(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /reforge preview <reforgeId> [rarity]"));
            return;
        }
        ReforgeDefinition def = module().getDefinition(args[1]);
        if (def == null) {
            sender.sendMessage(Formatter.format("<red>Unknown reforge: " + args[1]));
            return;
        }
        Rarity rarity = Rarity.COMMON;
        if (args.length >= 3) {
            try {
                rarity = Rarity.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Formatter.format("<red>Invalid rarity: " + args[2]));
                return;
            }
        }
        sender.sendMessage(Formatter.format("<gold>Preview '" + def.getId() + "' <gray>(" + def.getName() + ") <white>@ " + rarity.getName() + ":"));
        Map<String, Double> bonuses = def.getStatBonusesForRarity(rarity);
        if (bonuses.isEmpty()) {
            sender.sendMessage(Formatter.format(" <gray>No stat bonuses at this rarity."));
        } else {
            for (Map.Entry<String, Double> entry : bonuses.entrySet()) {
                sender.sendMessage(Formatter.format(" <gray>- <white>" + entry.getKey() + ": <green>+" + entry.getValue()));
            }
        }
    }

    private void handleForce(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Formatter.format("<red>Only a player can force-apply a reforge to their held item."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /reforge force <reforgeId>"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(Formatter.format("<red>Hold an item in your main hand first."));
            return;
        }
        ItemStack result = module().forceApplyReforge(held, args[1]);
        if (result == null) {
            sender.sendMessage(Formatter.format("<red>Unknown reforge id, or it doesn't apply to this item's type."));
            return;
        }
        player.getInventory().setItemInMainHand(result);
        sender.sendMessage(Formatter.format("<green>Applied reforge '" + args[1] + "' to your held item."));
    }

    private void handleReset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Formatter.format("<red>Only a player can reset the reforge on their held item."));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(Formatter.format("<red>Hold an item in your main hand first."));
            return;
        }
        ItemStack result = module().resetReforge(held);
        player.getInventory().setItemInMainHand(result);
        sender.sendMessage(Formatter.format("<green>Reforge reset on your held item."));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>REFORGE COMMANDS:"));
        sender.sendMessage(Formatter.format(" <gray>/reforge list"));
        sender.sendMessage(Formatter.format(" <gray>/reforge preview <reforgeId> [rarity]"));
        sender.sendMessage(Formatter.format(" <gray>/reforge force <reforgeId>  <dark_gray>(applies to held item)"));
        sender.sendMessage(Formatter.format(" <gray>/reforge reset  <dark_gray>(strips reforge from held item)"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("list", "preview", "force", "reset"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("force"))) {
            return module().getDefinitions().stream()
                    .map(ReforgeDefinition::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("preview")) {
            return filter(args[2], java.util.Arrays.stream(Rarity.values()).map(Enum::name).collect(Collectors.toList()));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
