package org.nakii.valmora.module.quest;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.stream.Collectors;

public class QuestCommand implements TabExecutor {

    private final Valmora plugin;

    public QuestCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "journal";

        // Admin subcommands — support/debugging force-complete/reset/inspect (previously the
        // command only supported opening the journal).
        switch (sub) {
            case "complete", "reset", "inspect" -> {
                return handleAdmin(sender, sub, args);
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Formatter.format("<red>Only players can use this command."));
            return true;
        }

        return switch (sub) {
            case "journal" -> {
                QuestModule qm = plugin.getQuestModule();
                if (qm != null && qm.getJournalManager() != null)
                    qm.getJournalManager().openJournal(player);
                yield true;
            }
            default -> {
                player.sendMessage(Formatter.format("<yellow>Usage: /quest [journal]"));
                yield true;
            }
        };
    }

    private boolean handleAdmin(CommandSender sender, String sub, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "quest")) {
            sender.sendMessage(Formatter.format("<red>No permission."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /quest " + sub + " <player> [questId]"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player not found."));
            return true;
        }

        QuestManager qm = plugin.getQuestManager();
        if (qm == null) {
            sender.sendMessage(Formatter.format("<red>Quest module not loaded."));
            return true;
        }

        switch (sub) {
            case "complete" -> {
                if (args.length < 3) { sender.sendMessage(Formatter.format("<red>Usage: /quest complete <player> <questId>")); return true; }
                qm.completeQuest(target, args[2].toLowerCase());
                sender.sendMessage(Formatter.format("<green>Force-completed quest '" + args[2] + "' for " + target.getName() + "."));
            }
            case "reset" -> {
                if (args.length < 3) { sender.sendMessage(Formatter.format("<red>Usage: /quest reset <player> <questId>")); return true; }
                ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(target.getUniqueId());
                ValmoraProfile profile = vp != null ? vp.getActiveProfile() : null;
                if (profile == null) { sender.sendMessage(Formatter.format("<red>Target has no active profile loaded.")); return true; }
                qm.resetQuestProgress(profile, args[2].toLowerCase());
                sender.sendMessage(Formatter.format("<green>Reset quest '" + args[2] + "' for " + target.getName() + "."));
            }
            case "inspect" -> {
                ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(target.getUniqueId());
                ValmoraProfile profile = vp != null ? vp.getActiveProfile() : null;
                if (profile == null) { sender.sendMessage(Formatter.format("<red>Target has no active profile loaded.")); return true; }

                sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                sender.sendMessage(Formatter.format(" <gold><bold>QUESTS — " + target.getName()));
                if (args.length >= 3) {
                    String questId = args[2].toLowerCase();
                    QuestDefinition quest = qm.getRegistry().get(questId).orElse(null);
                    if (quest == null) { sender.sendMessage(Formatter.format("<red>Unknown quest: " + questId)); return true; }
                    String status = qm.getStatus(profile, questId);
                    sender.sendMessage(Formatter.format(" <white>" + quest.getName() + " <dark_gray>(" + questId + ") <gray>— " + status));
                    for (int i = 0; i < quest.getObjectives().size(); i++) {
                        QuestObjective obj = quest.getObjectives().get(i);
                        int progress = qm.getProgress(profile, questId, i);
                        sender.sendMessage(Formatter.format("   <gray>- " + obj.getType() + " <dark_gray>(" + obj.getTarget()
                                + ") <white>" + progress + "<dark_gray>/" + obj.getRequired()));
                    }
                } else {
                    for (QuestDefinition quest : qm.getRegistry().values()) {
                        String status = qm.getStatus(profile, quest.getId());
                        if (status.equals(QuestManager.STATUS_NOT_STARTED)) continue;
                        sender.sendMessage(Formatter.format(" <gray>- <white>" + quest.getName()
                                + " <dark_gray>(" + quest.getId() + ") <gray>— " + status));
                    }
                }
                sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("journal", "complete", "reset", "inspect"));
        }
        if (args.length == 2 && List.of("complete", "reset", "inspect").contains(args[0].toLowerCase())) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }
        if (args.length == 3 && List.of("complete", "reset", "inspect").contains(args[0].toLowerCase())) {
            QuestManager qm = plugin.getQuestManager();
            if (qm == null) return List.of();
            return filter(args[2], qm.getRegistry().getKeys().stream().collect(Collectors.toList()));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
