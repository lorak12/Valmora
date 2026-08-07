package org.nakii.valmora.module.ui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

/**
 * {@code /ui toggle} — per-player scoreboard visibility control (added 2026-08-07). Session-only
 * (resets on rejoin); see {@link ScoreboardUI#toggle}.
 */
public class UICommand implements CommandExecutor {

    private final Valmora plugin;

    public UICommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("toggle")) {
            player.sendMessage(Formatter.format("<red>Usage: /ui toggle"));
            return true;
        }

        boolean nowHidden = plugin.getUIManager().getScoreboard().toggle(player);
        player.sendMessage(Formatter.format(nowHidden
                ? "<gray>Scoreboard hidden. Run <white>/ui toggle<gray> again to show it."
                : "<green>Scoreboard shown."));
        return true;
    }
}
