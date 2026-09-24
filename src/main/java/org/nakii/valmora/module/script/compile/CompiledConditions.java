package org.nakii.valmora.module.script.compile;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;

import java.util.List;

/**
 * A condition list from YAML (all must pass) with its compiled form — the condition counterpart of
 * {@link CompiledScript}. An empty list always passes.
 */
public final class CompiledConditions {

    public static final CompiledConditions ALWAYS = new CompiledConditions(List.of(), ctx -> true, ConfigSource.UNKNOWN);

    private final List<String> raw;
    private final ConfigSource source;
    private volatile Condition compiled;
    /** {@link ScriptEpoch} the compiled form belongs to — recompiled lazily once a partial reload bumps it. */
    private volatile int epoch = ScriptEpoch.current();

    private CompiledConditions(List<String> raw, Condition compiled, ConfigSource source) {
        this.raw = raw == null ? List.of() : List.copyOf(raw);
        this.compiled = compiled;
        this.source = source == null ? ConfigSource.UNKNOWN : source;
    }

    /** Compiles {@code lines} now, at {@code path} inside the current load scope. */
    public static CompiledConditions compile(List<String> lines, String path) {
        if (lines == null || lines.isEmpty()) return ALWAYS;
        LoadScope scope = LoadScope.current().orElse(null);
        ConfigSource source = scope == null ? ConfigSource.UNKNOWN : (path == null ? scope : scope.sub(path)).source();
        var module = scriptModule();
        if (module == null || module.getConditionParser() == null) return new CompiledConditions(lines, null, source);
        Condition condition = ScriptCompile.at(scope, path, () -> module.getConditionParser().parseList(lines));
        return new CompiledConditions(lines, condition, source);
    }

    public static CompiledConditions lazy(List<String> lines) {
        if (lines == null || lines.isEmpty()) return ALWAYS;
        return new CompiledConditions(lines, null, ConfigSource.UNKNOWN);
    }

    public boolean isEmpty() {
        return raw.isEmpty();
    }

    public List<String> raw() {
        return raw;
    }

    public Condition condition() {
        return condition(null);
    }

    /** @param module script module to compile with if this isn't compiled yet (null = the API's) */
    public Condition condition(org.nakii.valmora.module.script.ScriptModule module) {
        Condition c = compiled;
        if (c == null || epoch != ScriptEpoch.current()) {
            if (module == null) module = scriptModule();
            if (module == null || module.getConditionParser() == null) return ctx -> true;
            c = module.getConditionParser().parseList(raw);
            compiled = c;
            epoch = ScriptEpoch.current();
        }
        return c;
    }

    /** Evaluates the conditions; an exception counts as "not met" and is logged with the source. */
    public boolean test(ExecutionContext context) {
        return test(context, null);
    }

    public boolean test(ExecutionContext context, org.nakii.valmora.module.script.ScriptModule module) {
        if (isEmpty()) return true;
        try {
            return condition(module).evaluate(context);
        } catch (RuntimeException e) {
            ScriptRunner.report(source, e);
            return false;
        }
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
