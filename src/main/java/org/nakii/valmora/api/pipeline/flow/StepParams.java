package org.nakii.valmora.api.pipeline.flow;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A built-in step's {@code params:} section, handed to {@link BuiltinStepFactory#compile}.
 * Problems are reported at the param's own location ({@code flows.catch[2].params.chance}), and
 * everything is resolved once at load time — a numeric param written as an expression is compiled
 * here, never re-parsed per run.
 *
 * <p>Precedence for params that predate flows (docs/PIPELINE_FLOWS_PLAN.md §4.3): a value written
 * in the pipeline file wins; otherwise the caller-supplied fallback (usually the old
 * {@code config.yml} key, read live) applies.
 */
public final class StepParams {

    private final ConfigReader reader;
    private final LoadScope scope;
    private final ExpressionParser expressions;

    public StepParams(ConfigurationSection section, LoadScope scope, ExpressionParser expressions) {
        this.reader = ConfigReader.of(section, scope);
        this.scope = scope;
        this.expressions = expressions;
    }

    /** No params at all (a step used from code or a test). */
    public static StepParams empty() {
        return new StepParams(null, null, null);
    }

    public ConfigReader reader() {
        return reader;
    }

    public boolean has(String key) {
        return reader.has(key);
    }

    /** A literal number or an expression ({@code "$fishing.table.sea_creature_chance$ * 2"}); {@code fallback} when absent. */
    public NumberParam number(String key, NumberParam fallback) {
        ConfigurationSection section = reader.section();
        if (section == null || !section.contains(key)) return fallback;
        Object raw = section.get(key);
        if (raw instanceof Number n) return NumberParam.constant(n.doubleValue());
        String text = String.valueOf(raw).trim();
        try {
            return NumberParam.constant(Double.parseDouble(text));
        } catch (NumberFormatException notALiteral) {
            // an expression — compiled below
        }
        if (expressions == null) {
            reader.warn(key, "expected a number, got '" + raw + "' — using the default");
            return fallback;
        }
        Expression expression = ScriptCompile.at(scope, key, () -> expressions.parse(text));
        if (expression == null) return fallback;
        return (ctx, state) -> {
            FlowState.expose(ctx, state);
            Object result = expression.evaluate(ctx);
            return FlowState.asDouble(result, fallback.get(ctx, state));
        };
    }

    public NumberParam number(String key, double def) {
        return number(key, NumberParam.constant(def));
    }

    public String string(String key, String def) {
        return reader.string(key, def);
    }

    /** A string param, or {@code fallback} (read at run time — e.g. a live {@code config.yml} key) when absent. */
    public Supplier<String> string(String key, Supplier<String> fallback) {
        String value = reader.string(key, null);
        return value != null ? () -> value : fallback;
    }

    public boolean bool(String key, boolean def) {
        return reader.bool(key, def);
    }

    /** A boolean param, or {@code fallback} (read at run time) when absent. */
    public BooleanSupplier bool(String key, BooleanSupplier fallback) {
        if (!reader.has(key)) return fallback;
        boolean value = reader.bool(key, fallback.getAsBoolean());
        return () -> value;
    }

    public List<String> stringList(String key) {
        return reader.stringList(key);
    }

    /** Live {@code config.yml} boolean lookup — the usual fallback for {@link #bool(String, BooleanSupplier)}. */
    public static BooleanSupplier configBool(String path, boolean def) {
        return () -> {
            Valmora plugin = Valmora.getInstance();
            return plugin != null && plugin.getConfig() != null ? plugin.getConfig().getBoolean(path, def) : def;
        };
    }

    /** Live {@code config.yml} string lookup — the usual fallback for {@link #string(String, Supplier)}. */
    public static Supplier<String> configString(String path, String def) {
        return () -> {
            Valmora plugin = Valmora.getInstance();
            return plugin != null && plugin.getConfig() != null ? plugin.getConfig().getString(path, def) : def;
        };
    }
}
