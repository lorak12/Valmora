package org.nakii.valmora.module.time;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.nakii.valmora.util.Formatter;

public class TimeCommand implements CommandExecutor {

    private final TimeManager timeManager;

    public TimeCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sendInfo(sender);
            return true;
        }

        if (!sender.hasPermission("valmora.admin")) {
            sender.sendMessage(Formatter.format("<red>You don't have permission to use this command."));
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            timeManager.resetOffset();
            TimeSnapshot snap = timeManager.getSnapshot();
            sender.sendMessage(Formatter.format(
                    "<green>Time reset. Now: <white>" + snap.phaseName() + " " + snap.seasonName()
                    + ", Day " + snap.dayInPhase() + ", Year " + snap.year()
                    + " (" + snap.formattedTime() + ")"
            ));
            return true;
        }

        sender.sendMessage(Formatter.format("<red>Usage: /time [info|reset]"));
        return true;
    }

    private void sendInfo(CommandSender sender) {
        TimeSnapshot snap = timeManager.getSnapshot();
        Component msg = Formatter.format(
                "<gold><bold>✦ Valmora Time</bold></gold>\n"
                + "<gray>Season: <white>" + snap.phaseName() + " " + snap.seasonName() + "\n"
                + "<gray>Day: <white>" + snap.dayInPhase() + " <gray>of 30\n"
                + "<gray>Year: <white>" + snap.year() + "\n"
                + "<gray>Time: " + snap.timeOfDayMiniColor() + snap.timeOfDayEmote() + " <white>" + snap.formattedTime()
        );
        sender.sendMessage(msg);
    }
}
