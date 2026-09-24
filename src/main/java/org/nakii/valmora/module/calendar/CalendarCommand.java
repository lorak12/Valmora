package org.nakii.valmora.module.calendar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.time.TimeSnapshot;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.stream.Collectors;

/** Admin/debug command for the Calendar module — previously no command surface existed
 *  beyond automatic time-driven triggering (see docs/IMPLEMENTATION_BACKLOG.md, cross-cutting). */
public class CalendarCommand implements TabExecutor {

    private final Valmora plugin;

    public CalendarCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    private CalendarEventModule module() {
        return plugin.getCalendarEventModule();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "calendar")) {
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
            case "force-start" -> handleForceStart(sender, args);
            case "force-end" -> handleForceEnd(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleList(CommandSender sender) {
        CalendarEventModule cm = module();
        sender.sendMessage(Formatter.format("<gold><bold>CALENDAR EVENTS:"));
        if (cm.getDefinitions().isEmpty()) {
            sender.sendMessage(Formatter.format(" <gray>No calendar events registered."));
            return;
        }
        for (CalendarEventDefinition def : cm.getDefinitions()) {
            boolean active = cm.getActiveEventIds().contains(def.getId());
            String window = (def.getSeason() != null ? def.getSeason() + " " : "<any season> ")
                    + (def.getPhase() != null ? def.getPhase() : "<any phase>")
                    + ", day " + def.getDayStart() + "-" + def.getDayEnd();
            sender.sendMessage(Formatter.format(" <gray>- <white>" + def.getId()
                    + (active ? " <green>[ACTIVE]" : "") + " <dark_gray>(" + window + ")"));
        }
    }

    private void handlePreview(CommandSender sender, String[] args) {
        CalendarEventDefinition def = resolve(sender, args);
        if (def == null) return;
        boolean active = def.isActive(plugin.getTimeManager().getSnapshot());
        sender.sendMessage(Formatter.format("<gold>Preview '" + def.getId() + "': <white>"
                + (def.getSeason() != null ? def.getSeason() : "<any season>") + " "
                + (def.getPhase() != null ? def.getPhase() : "<any phase>")
                + ", day " + def.getDayStart() + "-" + def.getDayEnd()
                + " <gray>— currently " + (active ? "<green>active" : "<red>inactive")));
    }

    private void handleForceStart(CommandSender sender, String[] args) {
        CalendarEventDefinition def = resolve(sender, args);
        if (def == null) return;
        CalendarEventModule cm = module();
        cm.getActiveEventIds().add(def.getId());
        def.getOnStart().execute(new SimpleExecutionContext(null, (org.bukkit.Location) null, new YamlConfiguration()));
        sender.sendMessage(Formatter.format("<green>Forced on-start for calendar event '" + def.getId() + "'."));
    }

    private void handleForceEnd(CommandSender sender, String[] args) {
        CalendarEventDefinition def = resolve(sender, args);
        if (def == null) return;
        CalendarEventModule cm = module();
        cm.getActiveEventIds().remove(def.getId());
        def.getOnEnd().execute(new SimpleExecutionContext(null, (org.bukkit.Location) null, new YamlConfiguration()));
        sender.sendMessage(Formatter.format("<green>Forced on-end for calendar event '" + def.getId() + "'."));
    }

    private CalendarEventDefinition resolve(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /calendar " + args[0].toLowerCase() + " <eventId>"));
            return null;
        }
        CalendarEventDefinition def = module().getDefinition(args[1]);
        if (def == null) {
            sender.sendMessage(Formatter.format("<red>Unknown calendar event: " + args[1]));
        }
        return def;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>CALENDAR COMMANDS:"));
        sender.sendMessage(Formatter.format(" <gray>/calendar list"));
        sender.sendMessage(Formatter.format(" <gray>/calendar preview <eventId>"));
        sender.sendMessage(Formatter.format(" <gray>/calendar force-start <eventId>"));
        sender.sendMessage(Formatter.format(" <gray>/calendar force-end <eventId>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("list", "preview", "force-start", "force-end"));
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return module().getDefinitions().stream()
                    .map(CalendarEventDefinition::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
