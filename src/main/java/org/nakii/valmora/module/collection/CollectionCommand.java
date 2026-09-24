package org.nakii.valmora.module.collection;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.gui.GuiModule;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Bare {@code /collections} opens the collections GUI (original behavior, no subcommand). Admin
 * subcommands ({@code view}/{@code force}/{@code reset}) added per
 * docs/IMPLEMENTATION_BACKLOG.md's cross-cutting "collection admin view/reset/force" item.
 */
public class CollectionCommand implements TabExecutor {

    private final Valmora plugin;
    private final PlayerManager playerManager;

    public CollectionCommand(Valmora plugin) {
        this.plugin = plugin;
        this.playerManager = plugin.getPlayerManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "view" -> { handleView(sender, args); return true; }
                case "force" -> { handleForce(sender, args); return true; }
                case "reset" -> { handleReset(sender, args); return true; }
            }
        }

        // Original no-arg behavior: open the GUI for the invoking player.
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        GuiModule guiModule = plugin.getGuiModule();
        if (guiModule == null) {
            player.sendMessage("GUI module is not available.");
            return true;
        }
        guiModule.openGui(player, "collections_categories");
        return true;
    }

    private void handleView(CommandSender sender, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "collection")) {
            sender.sendMessage(Formatter.format("<red>No permission."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Formatter.format("<red>Usage: /collections view <player> <collectionId>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player not found (must be online)."));
            return;
        }
        CollectionDefinition def = resolveDefinition(sender, args[2]);
        if (def == null) return;

        var profile = playerManager.getSession(target.getUniqueId()).getActiveProfile();
        if (profile == null) {
            sender.sendMessage(Formatter.format("<red>Player profile not loaded."));
            return;
        }
        CollectionManager cm = profile.getCollectionManager();
        long count = cm.getCount(def.getId());
        int stage = cm.getCurrentStage(def.getId(), def);
        int granted = cm.getGrantedStage(def.getId());
        sender.sendMessage(Formatter.format("<gold><bold>" + def.getId().toUpperCase() + " <dark_gray>(" + target.getName() + ")"));
        sender.sendMessage(Formatter.format(" <gray>Count: <white>" + count));
        sender.sendMessage(Formatter.format(" <gray>Current stage: <white>" + stage + " <dark_gray>(rewards granted through stage " + granted + ")"));
    }

    private void handleForce(CommandSender sender, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "collection")) {
            sender.sendMessage(Formatter.format("<red>No permission."));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Formatter.format("<red>Usage: /collections force <player> <collectionId> <count>"));
            return;
        }
        OfflinePlayer target = resolveTarget(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player <white>" + args[1] + "<red> was not found (must have played on this server before)."));
            return;
        }
        CollectionDefinition def = resolveDefinition(sender, args[2]);
        if (def == null) return;

        long count;
        try {
            count = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Formatter.format("<red>Invalid count."));
            return;
        }

        String name = target.getName() != null ? target.getName() : args[1];
        // Note: this sets the raw count directly, bypassing CollectionListener.trackEvent — reward
        // stages are NOT re-fired here (that only happens on a real gameplay event next time the
        // player's count crosses a stage threshold), so this is purely for correcting/inspecting a
        // count, not a shortcut for granting stage rewards.
        playerManager.withOfflineProfile(target.getUniqueId(),
                profile -> profile.getCollectionManager().addCount(def.getId(), count - profile.getCollectionManager().getCount(def.getId())),
                ok -> {
                    if (ok) {
                        sender.sendMessage(Formatter.format("<green>Set " + name + "'s " + def.getId() + " collection count to " + count + "."));
                    } else {
                        sender.sendMessage(Formatter.format("<red>" + name + "'s profile could not be loaded."));
                    }
                });
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "collection")) {
            sender.sendMessage(Formatter.format("<red>No permission."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Formatter.format("<red>Usage: /collections reset <player> <collectionId>"));
            return;
        }
        OfflinePlayer target = resolveTarget(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player <white>" + args[1] + "<red> was not found (must have played on this server before)."));
            return;
        }
        CollectionDefinition def = resolveDefinition(sender, args[2]);
        if (def == null) return;

        String name = target.getName() != null ? target.getName() : args[1];
        playerManager.withOfflineProfile(target.getUniqueId(),
                profile -> {
                    CollectionManager cm = profile.getCollectionManager();
                    cm.addCount(def.getId(), -cm.getCount(def.getId()));
                    cm.setGrantedStage(def.getId(), 0);
                },
                ok -> {
                    if (ok) {
                        sender.sendMessage(Formatter.format("<green>Reset " + name + "'s " + def.getId() + " collection (count and granted rewards)."));
                    } else {
                        sender.sendMessage(Formatter.format("<red>" + name + "'s profile could not be loaded."));
                    }
                });
    }

    private CollectionDefinition resolveDefinition(CommandSender sender, String id) {
        Optional<CollectionDefinition> def = plugin.getCollectionModule().getRegistry().getCollection(id);
        if (def.isEmpty()) {
            sender.sendMessage(Formatter.format("<red>Unknown collection: " + id));
            return null;
        }
        return def.get();
    }

    /** Online players resolve directly; offline targets are matched (case-insensitively) against the server's known-player cache — never a blocking network lookup. Same pattern as {@code EcoCommand}/{@code SkillCommand}. */
    private OfflinePlayer resolveTarget(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.hasPlayedBefore() && name.equalsIgnoreCase(op.getName())) return op;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("view", "force", "reset"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("view") || args[0].equalsIgnoreCase("force") || args[0].equalsIgnoreCase("reset"))) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("view") || args[0].equalsIgnoreCase("force") || args[0].equalsIgnoreCase("reset"))) {
            return filter(args[2], plugin.getCollectionModule().getRegistry().getCollections().stream()
                    .map(CollectionDefinition::getId).collect(Collectors.toList()));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
