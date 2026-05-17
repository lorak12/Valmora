package org.nakii.valmora.module.script.event;

import org.bukkit.Bukkit;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.ScriptModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw event strings into CompiledEvent objects using factories and options.
 * Supports delay and notify options in DSL.
 */
public class EventParser {

    private final ScriptModule module;

    public EventParser(ScriptModule module) {
        this.module = module;
    }

    /**
     * Parses a single event string.
     * @param raw event string (e.g., "give STONE:10 notify delay:20")
     * @return compiled and ready-to-execute event
     */
    public CompiledEvent parse(String raw) {
        if (raw == null || raw.isEmpty()) return context -> {};

        String[] parts = raw.split(" ");
        if (parts.length == 0) return context -> {};

        String eventName = parts[0];

        // Option parsing
        int delay = 0;
        boolean notifyPlayer = false;
        String conditionsToken = null;
        List<String> argsList = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.equalsIgnoreCase("notify")) {
                notifyPlayer = true;
            } else if (part.startsWith("delay:")) {
                try {
                    delay = Integer.parseInt(part.substring(6));
                } catch (NumberFormatException ignored) {}
            } else if (part.startsWith("conditions:") || part.startsWith("condition:")) {
                int sep = part.indexOf(':');
                conditionsToken = part.substring(sep + 1);
            } else {
                argsList.add(part);
            }
        }

        String[] args = argsList.toArray(new String[0]);
        EventOptions options = new EventOptions(delay, notifyPlayer);
        final String finalConditionsToken = conditionsToken;

        final int finalDelay = delay;
        var factoryOpt = module.getEventFactoryRegistry().get(eventName);

        // Validate at compile time — fail fast with a clear message
        if (factoryOpt.isEmpty()) {
            module.getValmora().getLogger().warning("[DSL] Unknown event '" + eventName + "' in script: \"" + raw + "\"");
            return context -> {};
        }

        CompiledEvent compiled = factoryOpt.get().compile(args, options);

        // Wrap with condition guard if conditions: token was present
        CompiledEvent event;
        if (finalConditionsToken != null && !finalConditionsToken.isEmpty()) {
            var conditionGroup = module.getConditionParser().parseInlineList(finalConditionsToken);
            event = context -> {
                if (conditionGroup.evaluate(context)) compiled.execute(context);
            };
        } else {
            event = compiled;
        }

        if (finalDelay > 0) {
            return context -> Bukkit.getScheduler().runTaskLater(
                module.getValmora(),
                () -> event.execute(context),
                finalDelay
            );
        }
        return event;
    }

    /**
     * Parses a list of event strings.
     * @param list strings from YAML
     * @return a single CompiledEvent that executes all in sequence
     */
    public CompiledEvent parseList(List<String> list) {
        if (list == null || list.isEmpty()) return context -> {};
        List<CompiledEvent> events = new ArrayList<>();
        for (String s : list) {
            events.add(parse(s));
        }
        // ConditionAbortException is intentionally NOT caught here.
        // It propagates to the caller (GuiModule / GuiListener) which then runs fail-actions.
        return context -> {
            for (CompiledEvent event : events) {
                event.execute(context);
            }
        };
    }
}
