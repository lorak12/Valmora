package org.nakii.valmora.module.quest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

public class QuestCommand implements CommandExecutor {

    private final Valmora plugin;

    public QuestCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Formatter.format("<red>Only players can use this command."));
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "journal";
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
}
