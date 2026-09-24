package org.nakii.valmora.module.script.event;

import org.bukkit.Bukkit;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.util.DebugManager;

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

        String[] parts = tokenize(raw);
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

        CompiledEvent compiled;
        if (factoryOpt.isPresent()) {
            compiled = factoryOpt.get().compile(args, options);
        } else {
            // Not registered (yet). Many events are registered by modules that enable AFTER the
            // modules whose YAML uses them (e.g. `warp_to` from the warp module, used by GUIs;
            // `notify`, quest and point events), so failing here turned those scripts into
            // permanent no-ops. Resolve on first execution instead. Names still unknown once
            // every module is up are reported by reportUnresolved().
            unresolvedNames.add(eventName.toLowerCase());
            compiled = new LazyEvent(eventName, args, options, raw);
        }

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

        // Single generic choke point for "debug for everything else": nearly every subsystem in
        // this engine (GUI event blocks, skill rewards, item abilities, quest/mob triggers, ...)
        // ultimately dispatches through a CompiledEvent parsed here — so logging every execution
        // under the "script" debug channel gives broad coverage without hand-instrumenting each
        // domain individually. Guarded by isEnabled() first so the string concat below is skipped
        // entirely when the channel is off.
        final CompiledEvent finalEventForDebug = event;
        final CompiledEvent debuggedEvent = context -> {
            if (DebugManager.isEnabled("script")) {
                DebugManager.log("script", "event: \"" + raw + "\"");
            }
            finalEventForDebug.execute(context);
        };

        if (finalDelay > 0) {
            // Tracked, so a reload or the player quitting cancels it instead of it running later
            // against discarded state (see DelayedEventTracker).
            return context -> module.getDelayedEvents().schedule(context, () -> debuggedEvent.execute(context), finalDelay);
        }
        return debuggedEvent;
    }

    /** Event names seen at compile time with no registered factory. */
    private final java.util.Set<String> unresolvedNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> warnedUnresolved = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Logs every event name used in scripts that no module registered. Call once all modules are
     * enabled. Earlier, a name may simply belong to a module that hasn't enabled yet.
     */
    public void reportUnresolved() {
        for (String name : unresolvedNames) {
            if (module.getEventFactoryRegistry().get(name).isEmpty() && warnedUnresolved.add(name)) {
                module.getValmora().getLogger().warning("[DSL] Unknown event '" + name
                        + "' is used in scripts but no module provides it — those lines do nothing.");
            }
        }
    }

    /** Compiles against its factory on first execution (see {@link #parse}). */
    private final class LazyEvent implements CompiledEvent {
        private final String eventName;
        private final String[] args;
        private final EventOptions options;
        private final String raw;
        private volatile CompiledEvent resolved;

        LazyEvent(String eventName, String[] args, EventOptions options, String raw) {
            this.eventName = eventName;
            this.args = args;
            this.options = options;
            this.raw = raw;
        }

        @Override
        public void execute(org.nakii.valmora.api.execution.ExecutionContext context) {
            CompiledEvent target = resolved;
            if (target == null) {
                var factory = module.getEventFactoryRegistry().get(eventName);
                if (factory.isEmpty()) {
                    if (warnedUnresolved.add(eventName.toLowerCase())) {
                        module.getValmora().getLogger().warning("[DSL] Unknown event '" + eventName + "' in script: \"" + raw + "\"");
                    }
                    return;
                }
                target = factory.get().compile(args, options);
                resolved = target;
            }
            target.execute(context);
        }
    }

    /**
     * Splits a raw event string on whitespace, except inside double-quoted segments (added
     * 2026-08-07 — was a naive {@code raw.split(" ")}, unable to express an argument containing
     * spaces, e.g. a display name). Quotes are stripped from the resulting token; everything
     * else (including a stray unclosed quote) degrades gracefully rather than throwing.
     */
    private static String[] tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens.toArray(new String[0]);
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
