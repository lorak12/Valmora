package org.nakii.valmora.module.modifier;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generic admin/debug command for the modifier framework (docs/
 * Valmora_Modifier_Framework_Design.docx) — replaces the old group-specific {@code /reforge}
 * command with one that works for every group (reforges, gemstones, traits, ...) since it operates
 * purely on {@link ModifierGroupRegistry}/{@link ModifierRegistry} data.
 */
public class ModifierCommand implements TabExecutor {

    private final Valmora plugin;

    public ModifierCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    private ModifierModule module() {
        return plugin.getModifierModule();
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

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "groups" -> handleGroups(sender);
            case "list" -> handleList(sender, args);
            case "apply" -> handleApply(sender, args);
            case "remove" -> handleRemove(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<red>Usage: /modifier groups|list <group>|apply <group> <id> [tier]|remove <group> [id]"));
    }

    private void handleGroups(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>MODIFIER GROUPS:"));
        for (ModifierGroupDefinition group : module().getGroupRegistry().values()) {
            sender.sendMessage(Formatter.format(" <gray>- <white>" + group.getId()
                    + " <dark_gray>(" + group.getApplicationMode() + ", max " + group.getMax()
                    + ", storage " + group.getStorageMode() + ", tier-source " + group.getTierSource() + ")"));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /modifier list <group>"));
            return;
        }
        String groupId = args[1];
        if (module().getGroupRegistry().get(groupId).isEmpty()) {
            sender.sendMessage(Formatter.format("<red>Unknown group: " + groupId));
            return;
        }
        sender.sendMessage(Formatter.format("<gold><bold>MODIFIERS IN '" + groupId.toUpperCase(Locale.ROOT) + "':"));
        var defs = module().getModifierRegistry().valuesInGroup(groupId);
        if (defs.isEmpty()) {
            sender.sendMessage(Formatter.format(" <gray>No modifiers registered in this group."));
            return;
        }
        for (ModifierDefinition def : defs) {
            String tiers = def.isTiered() ? (" <dark_gray>(tiers 1-" + def.getMaxTier() + ")") : "";
            sender.sendMessage(Formatter.format(" <gray>- <white>" + def.getId() + tiers));
        }
    }

    private void handleApply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Formatter.format("<red>Only players can use this."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Formatter.format("<red>Usage: /modifier apply <group> <id> [tier]"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(Formatter.format("<red>Hold the item to modify."));
            return;
        }
        int tier = args.length >= 4 ? parseIntOr(args[3], 1) : 1;

        var outcome = module().getEngine().apply(held, args[1], args[2], tier);
        if (!outcome.isSuccess()) {
            sender.sendMessage(Formatter.format("<red>Could not apply: " + outcome.result()));
            return;
        }
        plugin.getItemManager().getItemFactory().updateLore(outcome.item());
        player.getInventory().setItemInMainHand(outcome.item());
        sender.sendMessage(Formatter.format("<green>Applied '" + args[2] + "' (group " + args[1] + ", tier " + tier + ")."));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Formatter.format("<red>Only players can use this."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /modifier remove <group> [id]"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(Formatter.format("<red>Hold the item to modify."));
            return;
        }
        String modifierId = args.length >= 3 ? args[2] : null;

        var outcome = module().getEngine().remove(held, args[1], modifierId);
        if (!outcome.isSuccess()) {
            sender.sendMessage(Formatter.format("<red>Could not remove: " + outcome.result()));
            return;
        }
        plugin.getItemManager().getItemFactory().updateLore(outcome.item());
        player.getInventory().setItemInMainHand(outcome.item());
        sender.sendMessage(Formatter.format("<green>Removed from group '" + args[1] + "'."));
    }

    private int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("groups", "list", "apply", "remove"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("apply") || args[0].equalsIgnoreCase("remove"))) {
            for (ModifierGroupDefinition g : module().getGroupRegistry().values()) out.add(g.getId());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("apply") || args[0].equalsIgnoreCase("remove"))) {
            for (ModifierDefinition d : module().getModifierRegistry().valuesInGroup(args[1])) out.add(d.getId());
        }
        return out;
    }
}
