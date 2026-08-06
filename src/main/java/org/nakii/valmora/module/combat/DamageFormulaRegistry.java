package org.nakii.valmora.module.combat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code damage_formula.yml} and pre-compiles every formula into an {@link Expression} AST
 * at load time (Phase 2.2 — see docs/REFACTOR/PROGRESS.md). {@link DamageCalculator} evaluates
 * the cached, already-parsed trees on the combat hot path — raw formula strings are never
 * re-parsed per hit (satisfies the "no hot-path string interpretation" rule).
 *
 * <p>Available variables inside a formula (via {@code $dmg.*$}, see
 * {@link org.nakii.valmora.module.script.variable.providers.DamageVariableProvider}):
 * {@code base_damage}, {@code strength}, {@code crit_chance}, {@code crit_damage},
 * {@code defense}. Defaults reproduce the exact pre-refactor hardcoded math, so an admin who
 * never creates {@code damage_formula.yml} sees no behavior change.
 */
public class DamageFormulaRegistry {

    /** Multiplies base damage for the strength bonus: previously hardcoded `1 + strength/100`. */
    public static final String DAMAGE_MULTIPLIER = "damage_multiplier";
    /** Extra multiplier applied only on a critical hit: previously hardcoded `1 + critDamage/100`. */
    public static final String CRIT_MULTIPLIER = "crit_multiplier";
    /** Multiplies post-strength/crit damage for defense mitigation: previously hardcoded `100/(defense+100)`. */
    public static final String DEFENSE_MULTIPLIER = "defense_multiplier";

    private static final Map<String, String> DEFAULTS = Map.of(
            DAMAGE_MULTIPLIER, "1 + $dmg.strength$ / 100",
            CRIT_MULTIPLIER, "1 + $dmg.crit_damage$ / 100",
            DEFENSE_MULTIPLIER, "100 / ($dmg.defense$ + 100)"
    );

    private final Valmora plugin;
    private final ExpressionParser parser;
    private final Map<String, Expression> compiled = new ConcurrentHashMap<>();

    public DamageFormulaRegistry(Valmora plugin, ExpressionParser parser) {
        this.plugin = plugin;
        this.parser = parser;
    }

    public void load() {
        compiled.clear();

        Map<String, String> raw = new java.util.HashMap<>(DEFAULTS);
        File file = new File(plugin.getDataFolder(), "damage_formula.yml");
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String key : DEFAULTS.keySet()) {
                String override = config.getString(key);
                if (override != null && !override.isBlank()) {
                    raw.put(key, override);
                }
            }
        }

        for (Map.Entry<String, String> entry : raw.entrySet()) {
            compiled.put(entry.getKey(), parser.parse(entry.getValue()));
        }
        plugin.getLogger().info("[DamageFormulaRegistry] Compiled " + compiled.size() + " damage formulas.");
    }

    /** Evaluates a pre-compiled formula against the given context, returning {@code fallback} if missing/non-numeric. */
    public double evaluate(String formulaId, org.nakii.valmora.api.execution.ExecutionContext context, double fallback) {
        Expression expr = compiled.get(formulaId);
        if (expr == null) return fallback;
        Object result = expr.evaluate(context);
        return result instanceof Number n ? n.doubleValue() : fallback;
    }

    public void clear() {
        compiled.clear();
    }
}
