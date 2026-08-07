package org.nakii.valmora.module.progression;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A generic, tree-agnostic command surface for the progression module — previously the only way
 * to interact with a progression tree at all was through a GUI's own `command:` binding (e.g.
 * `/geomancy`), which doesn't generalize to other trees or offer any non-GUI/admin access.
 */
public class ProgressionCommand implements TabExecutor {

    private final Valmora plugin;

    public ProgressionCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        ProgressionManager manager = plugin.getProgressionModule().getProgressionManager();
        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender, manager);
            case "info" -> handleInfo(sender, args, manager);
            case "reset" -> handleReset(sender, args, manager);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleList(CommandSender sender, ProgressionManager manager) {
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>PROGRESSION TREES"));
        for (ProgressionTreeDefinition tree : manager.getRegistry().values()) {
            sender.sendMessage(Formatter.format(" <gray>- <white>" + tree.getId()
                    + " <dark_gray>(" + tree.getNodes().size() + " nodes)"));
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void handleInfo(CommandSender sender, String[] args, ProgressionManager manager) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
        if (args.length < 2) {
            player.sendMessage(Formatter.format("<red>Usage: /progression info <treeId>"));
            return;
        }
        String treeId = args[1].toLowerCase();
        var tree = manager.getRegistry().getTree(treeId).orElse(null);
        if (tree == null) {
            player.sendMessage(Formatter.format("<red>Unknown progression tree: " + treeId));
            return;
        }

        int tierUnlocked = manager.getUnlockedTier(player.getUniqueId(), treeId);
        player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        player.sendMessage(Formatter.format(" <gold><bold>" + treeId.toUpperCase() + " <gray>— Tier <yellow>" + tierUnlocked));
        for (ProgressionNode node : tree.getNodes().values()) {
            int level = manager.getNodeLevel(player.getUniqueId(), treeId, node.getId());
            boolean unlocked = manager.isNodeUnlocked(player.getUniqueId(), treeId, node.getId());
            String state = !unlocked ? "<dark_gray>locked" : "<white>Lvl " + level + "<dark_gray>/" + node.getMaxLevel();
            player.sendMessage(Formatter.format(" <gray>- " + node.getDisplayName() + " <dark_gray>(" + state + "<dark_gray>)"));
        }
        player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void handleReset(CommandSender sender, String[] args, ProgressionManager manager) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
        if (args.length < 2) {
            player.sendMessage(Formatter.format("<red>Usage: /progression reset <treeId>"));
            return;
        }
        String treeId = args[1].toLowerCase();
        if (manager.getRegistry().getTree(treeId).isEmpty()) {
            player.sendMessage(Formatter.format("<red>Unknown progression tree: " + treeId));
            return;
        }
        manager.resetTree(player, treeId);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>PROGRESSION COMMANDS:"));
        sender.sendMessage(Formatter.format(" <gray>/progression list"));
        sender.sendMessage(Formatter.format(" <gray>/progression info <treeId>"));
        sender.sendMessage(Formatter.format(" <gray>/progression reset <treeId>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ProgressionManager manager = plugin.getProgressionModule().getProgressionManager();
        if (args.length == 1) {
            return filter(args[0], List.of("list", "info", "reset"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("reset"))) {
            return filter(args[1], manager.getRegistry().values().stream()
                    .map(ProgressionTreeDefinition::getId).collect(Collectors.toList()));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
