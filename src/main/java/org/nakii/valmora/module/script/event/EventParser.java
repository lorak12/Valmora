package org.nakii.valmora.module.script.event;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.DiagnosticSink;
import org.nakii.valmora.infrastructure.config.diag.Diagnostics;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.util.DebugManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses raw event strings into CompiledEvent objects using factories and options:
 * <pre>
 * &lt;event&gt; &lt;args...&gt; [notify] [delay:&lt;20 | 20t | 1s | 2.5s | 1m&gt;] [condition:&lt;cond&gt;,&lt;cond&gt;]
 * </pre>
 * Arguments containing spaces go in double quotes ({@code "a \"quoted\" word"}).
 *
 * <p>Problems (unknown event, wrong number of arguments, bad {@code delay:}) are reported through
 * {@link Diagnostics}, i.e. into the file/entry being loaded. Events whose module enables later
 * than the content using them compile lazily; they're resolved — and checked — once every module
 * is up ({@link #runDeferred}).
 */
public class EventParser {

    private final ScriptModule module;

    public EventParser(ScriptModule module) {
        this.module = module;
    }

    /** One token of an event line: its text (quotes removed) and exactly how it was written. */
    record Token(String text, String raw, boolean quoted) {}

    /**
     * Parses a single event string.
     * @param raw event string (e.g., "give STONE:10 notify delay:20")
     * @return compiled and ready-to-execute event
     */
    public CompiledEvent parse(String raw) {
        if (raw == null || raw.isBlank()) return context -> {};

        List<Token> parts = tokenize(raw);
        if (parts.isEmpty()) return context -> {};

        String eventName = parts.get(0).text();

        // Option parsing — only unquoted tokens are options, so a quoted "notify" is a normal argument.
        int delay = 0;
        boolean notifyPlayer = false;
        String conditionsToken = null;
        List<String> argsList = new ArrayList<>();
        List<String> rawArgs = new ArrayList<>();

        for (int i = 1; i < parts.size(); i++) {
            Token part = parts.get(i);
            String text = part.text();
            if (!part.quoted() && text.equalsIgnoreCase("notify")) {
                notifyPlayer = true;
            } else if (!part.quoted() && text.startsWith("delay:")) {
                Integer ticks = parseDelayTicks(text.substring(6));
                if (ticks == null) {
                    Diagnostics.error(eventName + ": invalid delay '" + text.substring(6)
                            + "' — expected ticks (20, 20t) or time (1s, 2.5s, 1m); the event runs without a delay");
                } else {
                    delay = ticks;
                }
            } else if (!part.quoted() && (text.startsWith("conditions:") || text.startsWith("condition:"))) {
                conditionsToken = text.substring(text.indexOf(':') + 1);
                if (conditionsToken.isBlank()) {
                    Diagnostics.warn(eventName + ": empty '" + text + "' — the event always runs");
                }
            } else {
                argsList.add(text);
                rawArgs.add(part.raw());
            }
        }

        String[] args = argsList.toArray(new String[0]);
        for (String a : args) org.nakii.valmora.module.script.expression.ExpressionParser.noticeVariablesIn(a);
        ConfigSource source = LoadScope.current().map(LoadScope::source).orElse(ConfigSource.UNKNOWN);
        EventOptions options = new EventOptions(delay, notifyPlayer, String.join(" ", rawArgs), source);
        final String finalConditionsToken = conditionsToken;

        final int finalDelay = delay;
        var factoryOpt = module.getEventFactoryRegistry().get(eventName);

        CompiledEvent compiled;
        if (factoryOpt.isPresent()) {
            compiled = compileWith(factoryOpt.get(), args, options);
        } else {
            // Not registered (yet). Many events are registered by modules that enable AFTER the
            // modules whose YAML uses them (e.g. `warp_to` from the warp module, used by GUIs;
            // `notify`, quest and point events), so failing here turned those scripts into
            // permanent no-ops. Resolve on first execution — or at the end of the load, whichever
            // comes first (see runDeferred). Names still unknown then are reported.
            recordUnresolved(eventName, source);
            LazyEvent lazy = new LazyEvent(eventName, args, options, raw);
            trackLazy(lazy);
            compiled = lazy;
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
                DebugManager.log("script", "event: \"" + raw + "\"" + (source.isKnown() ? " (" + source.describe() + ")" : ""));
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

    /** Checks the argument count, records references, compiles — reporting instead of throwing. */
    private CompiledEvent compileWith(EventFactory factory, String[] args, EventOptions options) {
        int min = factory.minArgs();
        int max = factory.maxArgs();
        if (args.length < min || args.length > max) {
            String expected = min == max ? String.valueOf(min)
                    : max == Integer.MAX_VALUE ? "at least " + min : min + "-" + max;
            Diagnostics.error(factory.getName() + ": expected " + expected + " argument" + (expected.equals("1") ? "" : "s")
                    + ", got " + args.length, "usage: " + factory.usage());
        }
        LoadScope scope = LoadScope.current().orElse(null);
        if (scope != null) {
            try {
                factory.references(args, scope::ref);
            } catch (RuntimeException ignored) {
                // a factory's reference extraction must never break compilation
            }
        }
        try {
            CompiledEvent event = factory.compile(args, options);
            return event != null ? event : context -> {};
        } catch (RuntimeException e) {
            Diagnostics.error(factory.getName() + ": could not compile — " + e.getMessage());
            return context -> {};
        }
    }

    /**
     * {@code 20} / {@code 20t} ticks, {@code 1s} / {@code 2.5s} seconds, {@code 1m} minutes. Null
     * when malformed or negative.
     */
    static Integer parseDelayTicks(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase();
        double multiplier = 1.0;
        if (s.endsWith("t")) {
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("s")) {
            multiplier = 20.0;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            multiplier = 20.0 * 60.0;
            s = s.substring(0, s.length() - 1);
        }
        try {
            double value = Double.parseDouble(s) * multiplier;
            if (value < 0 || Double.isNaN(value) || value > Integer.MAX_VALUE) return null;
            return (int) Math.round(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ unresolved / lazy events

    /** Event names seen at compile time with no registered factory → where they were used. */
    private final Map<String, Set<ConfigSource>> unresolvedNames = new ConcurrentHashMap<>();
    private final Set<String> warnedUnresolved = ConcurrentHashMap.newKeySet();
    /** Lazy events compiled since the last {@link #runDeferred} — resolved (and checked) there. */
    private final List<LazyEvent> pendingLazy = new ArrayList<>();
    private static final int MAX_PENDING = 20_000;

    private void recordUnresolved(String eventName, ConfigSource source) {
        Set<ConfigSource> sources = unresolvedNames.computeIfAbsent(eventName.toLowerCase(), k -> ConcurrentHashMap.newKeySet());
        if (sources.size() < 50) sources.add(source);
    }

    private void trackLazy(LazyEvent lazy) {
        synchronized (pendingLazy) {
            if (pendingLazy.size() < MAX_PENDING) pendingLazy.add(lazy);
        }
    }

    /** Logs every unresolved event name to the console (legacy entry point — prefer {@link #runDeferred}). */
    public void reportUnresolved() {
        List<ConfigDiagnostic> found = new ArrayList<>();
        reportUnresolved(found::add);
        for (ConfigDiagnostic d : found) module.getValmora().getLogger().warning("[DSL] " + d.format());
    }

    /**
     * Once every module is enabled: compiles each lazy event whose factory now exists (inside the
     * scope it was written in, so argument problems are reported against its file and entry), then
     * reports every event name no module provides.
     */
    public void runDeferred(DiagnosticSink sink) {
        List<LazyEvent> lazies;
        synchronized (pendingLazy) {
            lazies = new ArrayList<>(pendingLazy);
            pendingLazy.clear();
        }
        for (LazyEvent lazy : lazies) {
            try {
                lazy.resolveNow(sink);
            } catch (RuntimeException e) {
                // resolved again on first execution instead
            }
        }
        reportUnresolved(sink);
    }

    /**
     * Reports every event name used in scripts that no module registered, once per name, with where
     * it is used and the closest known name. Call once all modules are enabled — earlier, a name may
     * simply belong to a module that hasn't enabled yet.
     */
    public void reportUnresolved(DiagnosticSink sink) {
        Set<String> known = new TreeSet<>();
        for (EventFactory f : module.getEventFactoryRegistry().values()) known.add(f.getName().toLowerCase());
        for (var entry : unresolvedNames.entrySet()) {
            String name = entry.getKey();
            if (module.getEventFactoryRegistry().get(name).isPresent() || !warnedUnresolved.add(name)) continue;
            List<ConfigSource> sources = new ArrayList<>(entry.getValue());
            ConfigSource first = sources.stream().filter(ConfigSource::isKnown).findFirst().orElse(ConfigSource.UNKNOWN);
            StringBuilder message = new StringBuilder("unknown script event '").append(name)
                    .append("' — no module provides it, so those lines do nothing");
            List<ConfigSource> others = sources.stream().filter(s -> s != first && s.isKnown()).toList();
            if (!others.isEmpty()) {
                message.append("; also used in ");
                others.stream().limit(3).forEach(s -> message.append(s.describe()).append(", "));
                message.setLength(message.length() - 2);
                if (others.size() > 3) message.append(" (+").append(others.size() - 3).append(" more)");
            }
            sink.accept(first.diagnostic(Severity.WARN, message.toString(), Suggestions.hint(name, known)));
        }
    }

    /** Compiles against its factory on first execution, or at the end of the load (see {@link #parse}). */
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

        /** Resolves now inside the scope the event was written in, reporting into {@code sink}. */
        void resolveNow(DiagnosticSink sink) {
            if (resolved != null) return;
            var factory = module.getEventFactoryRegistry().get(eventName);
            if (factory.isEmpty()) return;
            ConfigSource src = options.source();
            if (src == null || !src.isKnown()) {
                resolved = compileWith(factory.get(), args, options);
                return;
            }
            try (LoadScope entry = LoadScope.enter(src.category(), src.file(), src.entryId(), sink);
                 LoadScope ignored = entry.sub(src.path()).push()) {
                resolved = compileWith(factory.get(), args, options);
            }
        }

        @Override
        public void execute(org.nakii.valmora.api.execution.ExecutionContext context) {
            CompiledEvent target = resolved;
            if (target == null) {
                var factory = module.getEventFactoryRegistry().get(eventName);
                if (factory.isEmpty()) {
                    if (warnedUnresolved.add(eventName.toLowerCase())) {
                        module.getValmora().getLogger().warning("[DSL] Unknown event '" + eventName + "' in script: \"" + raw + "\""
                                + (options.source().isKnown() ? " (" + options.source().describe() + ")" : ""));
                    }
                    return;
                }
                target = compileWith(factory.get(), args, options);
                resolved = target;
            }
            target.execute(context);
        }
    }

    /**
     * Splits a raw event string on whitespace, except inside double-quoted segments. Quotes are
     * stripped from {@link Token#text()} but kept in {@link Token#raw()}; {@code \"} inside quotes
     * is a literal quote. A stray unclosed quote degrades gracefully rather than throwing.
     */
    static List<Token> tokenize(String raw) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        StringBuilder rawText = new StringBuilder();
        boolean inQuotes = false;
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inQuotes && c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '"') {
                text.append('"');
                rawText.append("\\\"");
                i++;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                quoted = true;
                rawText.append(c);
            } else if (c == ' ' && !inQuotes) {
                if (rawText.length() > 0) {
                    tokens.add(new Token(text.toString(), rawText.toString(), quoted));
                    text.setLength(0);
                    rawText.setLength(0);
                    quoted = false;
                }
            } else {
                text.append(c);
                rawText.append(c);
            }
        }
        if (rawText.length() > 0) tokens.add(new Token(text.toString(), rawText.toString(), quoted));
        return tokens;
    }

    /**
     * Parses a list of event strings.
     * @param list strings from YAML
     * @return a single CompiledEvent that executes all in sequence
     */
    public CompiledEvent parseList(List<String> list) {
        if (list == null || list.isEmpty()) return context -> {};
        List<CompiledEvent> events = new ArrayList<>();
        LoadScope scope = LoadScope.current().orElse(null);
        for (int i = 0; i < list.size(); i++) {
            String line = list.get(i);
            if (scope != null && list.size() > 1) {
                // Point problems at the exact line: e.g. on-open.actions[2].
                try (LoadScope ignored = scope.sub(String.valueOf(i)).push()) {
                    events.add(parse(line));
                }
            } else {
                events.add(parse(line));
            }
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
