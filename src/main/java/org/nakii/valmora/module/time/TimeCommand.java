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

        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "time")) {
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

        if (args[0].equalsIgnoreCase("set")) {
            // /time set <year> <season> <phase> <day>
            if (args.length < 5) {
                sender.sendMessage(Formatter.format("<red>Usage: /time set <year> <season> <phase> <day>"));
                return true;
            }
            int year;
            int day;
            try {
                year = Integer.parseInt(args[1]);
                day = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Formatter.format("<red>Year and day must be numbers."));
                return true;
            }
            Season season;
            Phase phase;
            try {
                season = Season.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Formatter.format("<red>Invalid season. Must be one of: SPRING, SUMMER, AUTUMN, WINTER."));
                return true;
            }
            try {
                phase = Phase.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Formatter.format("<red>Invalid phase. Must be one of: EARLY, MID, LATE."));
                return true;
            }

            timeManager.setDate(year, season, phase, day);
            TimeSnapshot snap = timeManager.getSnapshot();
            sender.sendMessage(Formatter.format(
                    "<green>Time set. Now: <white>" + snap.phaseName() + " " + snap.seasonName()
                    + ", Day " + snap.dayInPhase() + ", Year " + snap.year()
                    + " (" + snap.formattedTime() + ")"
            ));
            return true;
        }

        sender.sendMessage(Formatter.format("<red>Usage: /time [info|reset|set <year> <season> <phase> <day>]"));
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
