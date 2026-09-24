package org.nakii.valmora.module.script.compile;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;

import java.util.List;

/**
 * An action list from YAML ({@code on-right-click:}, {@code enter-actions:}, ...) together with
 * its compiled form. Loaders call {@link #compile(List, String)} inside their {@link LoadScope}, so
 * the list is compiled once and any problem in it is reported at load time with its file, entry
 * and key; call sites then just {@link #run} it. Built without a scope (e.g. from Java), it
 * compiles lazily on first use instead.
 */
public final class CompiledScript {

    public static final CompiledScript EMPTY = new CompiledScript(List.of(), ctx -> {}, ConfigSource.UNKNOWN);

    private final List<String> raw;
    private final ConfigSource source;
    private volatile CompiledEvent compiled;
    /** {@link ScriptEpoch} the compiled form belongs to — recompiled lazily once a partial reload bumps it. */
    private volatile int epoch = ScriptEpoch.current();

    private CompiledScript(List<String> raw, CompiledEvent compiled, ConfigSource source) {
        this.raw = raw == null ? List.of() : List.copyOf(raw);
        this.compiled = compiled;
        this.source = source == null ? ConfigSource.UNKNOWN : source;
    }

    /**
     * Compiles {@code lines} now, at {@code path} inside the current load scope (e.g.
     * {@code "enter-actions"}). Without a script module (plain unit tests) it stays lazy.
     */
    public static CompiledScript compile(List<String> lines, String path) {
        if (lines == null || lines.isEmpty()) return EMPTY;
        LoadScope scope = LoadScope.current().orElse(null);
        ConfigSource source = scope == null ? ConfigSource.UNKNOWN : (path == null ? scope : scope.sub(path)).source();
        var module = scriptModule();
        if (module == null || module.getEventParser() == null) return new CompiledScript(lines, null, source);
        CompiledEvent event = ScriptCompile.at(scope, path, () -> module.getEventParser().parseList(lines));
        return new CompiledScript(lines, event, source);
    }

    /** Not compiled until first {@link #event()} — for definitions built outside a loader. */
    public static CompiledScript lazy(List<String> lines) {
        if (lines == null || lines.isEmpty()) return EMPTY;
        return new CompiledScript(lines, null, ConfigSource.UNKNOWN);
    }

    public boolean isEmpty() {
        return raw.isEmpty();
    }

    public List<String> raw() {
        return raw;
    }

    public ConfigSource source() {
        return source;
    }

    public CompiledEvent event() {
        return event(null);
    }

    /** @param module script module to compile with if this isn't compiled yet (null = the API's) */
    public CompiledEvent event(org.nakii.valmora.module.script.ScriptModule module) {
        CompiledEvent e = compiled;
        if (e == null || epoch != ScriptEpoch.current()) {
            if (module == null) module = scriptModule();
            if (module == null || module.getEventParser() == null) return ctx -> {};
            e = module.getEventParser().parseList(raw);
            compiled = e;
            epoch = ScriptEpoch.current();
        }
        return e;
    }

    /**
     * Runs the script; a failing {@code condition} line just stops it, an exception is logged with
     * this script's source instead of escaping into the caller.
     * @return true if it ran to the end
     */
    public boolean run(ExecutionContext context) {
        return run(context, null);
    }

    /** {@link #run(ExecutionContext)} compiling with {@code module} if needed (the caller's own script module). */
    public boolean run(ExecutionContext context, org.nakii.valmora.module.script.ScriptModule module) {
        if (isEmpty()) return true;
        return ScriptRunner.run(event(module), context, source);
    }

    private static org.nakii.valmora.module.script.ScriptModule scriptModule() {
        try {
            ValmoraAPI api = ValmoraAPI.getInstance();
            return api == null ? null : api.getScriptModule();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
