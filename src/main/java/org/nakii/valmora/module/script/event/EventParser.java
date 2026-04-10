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
        List<String> argsList = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.equalsIgnoreCase("notify")) {
                notifyPlayer = true;
            } else if (part.startsWith("delay:")) {
                try {
                    delay = Integer.parseInt(part.substring(6));
                } catch (NumberFormatException ignored) {}
            } else {
                argsList.add(part);
            }
        }

        String[] args = argsList.toArray(new String[0]);
        EventOptions options = new EventOptions(delay, notifyPlayer);

        final int finalDelay = delay;
        return module.getEventFactoryRegistry().get(eventName)
                .<CompiledEvent>map(factory -> {
                    CompiledEvent event = factory.compile(args, options);
                    
                    // Delay handling
                    if (finalDelay > 0) {
                        return context -> {
                            Bukkit.getScheduler().runTaskLater(
                                module.getValmora(),
                                () -> event.execute(context),
                                finalDelay
                            );
                        };
                    }
                    return event;
                })
                .orElse(context -> {
                    module.getValmora().getLogger().warning("Unknown script event: " + eventName);
                });
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
        return context -> {
            for (CompiledEvent event : events) {
                event.execute(context);
            }
        };
    }
}
