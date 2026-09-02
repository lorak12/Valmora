package org.nakii.valmora.module.skill;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code skills/xp_curves.yml} and resolves each entry into a pre-computed {@link XpCurve}
 * threshold table (Phase 3.2 of the refactor — see docs/REFACTOR/PROGRESS.md), replacing the
 * single hardcoded {@code DEFAULT_XP_THRESHOLDS} array that used to live directly on
 * {@code SkillRegistry} and was applied to every skill regardless of its {@code xp_curve} field.
 *
 * <p>The {@code "default"} curve is always present — built with the exact original 59-level
 * threshold table — so a server that never creates {@code skills/xp_curves.yml} sees zero
 * behavior change (verified in {@code XpCurveRegistryTest}).
 *
 * <p>A curve entry is defined either as:
 * <pre>
 * xp_curves:
 *   default:
 *     thresholds: [10, 20, 50, ...]   # explicit table, matched verbatim
 *   fast:
 *     formula: "50 * $curve.level$^1.8"
 *     max-level: 60                    # formula is evaluated once per level, 1..max-level, at load time
 * </pre>
 * Formulas are parsed into an {@link Expression} AST once and evaluated {@code max-level} times
 * — never re-parsed, and never touched again after the resulting table is cached. Skills opt into
 * a curve via their {@code xp_curve} field (defaults to {@code "default"}).
 */
public class XpCurveRegistry {

    public static final String DEFAULT_CURVE_ID = "default";

    // The exact pre-refactor lookup table (previously SkillRegistry.DEFAULT_XP_THRESHOLDS).
    private static final int[] DEFAULT_THRESHOLDS = {
            10, 20, 50, 100, 200, 500, 1000, 1500, 2000, 3000, 5000, 7500, 10000,
            15000, 20000, 30000, 40000, 50000, 60000, 75000, 100000, 125000, 150000,
            175000, 200000, 250000, 300000, 350000, 400000, 450000, 500000, 600000,
            700000, 800000, 900000, 1000000, 1200000, 1400000, 1600000, 1800000,
            2000000, 2300000, 2600000, 3000000, 3400000, 3800000, 4200000, 4600000,
            5000000, 5500000, 6000000, 6500000, 7000000, 7500000, 8000000, 8500000,
            9000000, 9500000, 10000000
    };

    private final Map<String, XpCurve> curves = new ConcurrentHashMap<>();

    public XpCurveRegistry() {
        curves.put(DEFAULT_CURVE_ID, new XpCurve(DEFAULT_THRESHOLDS));
    }

    /** (Re)loads curves from {@code skills/xp_curves.yml}. The built-in "default" curve is always restored first. */
    public void load(Valmora plugin, ExpressionParser parser) {
        curves.clear();
        curves.put(DEFAULT_CURVE_ID, new XpCurve(DEFAULT_THRESHOLDS));

        File file = new File(plugin.getDataFolder(), "skills/xp_curves.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("xp_curves");
        if (section == null) return;

        int loaded = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection curveSection = section.getConfigurationSection(id);
            if (curveSection == null) continue;

            int[] thresholds = curveSection.contains("thresholds")
                    ? curveSection.getIntegerList("thresholds").stream().mapToInt(Integer::intValue).toArray()
                    : fromFormula(curveSection, parser);
            if (thresholds == null || thresholds.length == 0) {
                plugin.getLogger().warning("[XpCurveRegistry] Curve '" + id + "' has neither a valid 'thresholds' list nor a 'formula' — skipped.");
                continue;
            }

            curves.put(id.toLowerCase(Locale.ROOT), new XpCurve(thresholds));
            loaded++;
        }
        plugin.getLogger().info("[XpCurveRegistry] Loaded " + loaded + " custom XP curve(s) from skills/xp_curves.yml.");
    }

    private int[] fromFormula(ConfigurationSection section, ExpressionParser parser) {
        String formula = section.getString("formula");
        if (formula == null || formula.isBlank()) return null;
        // HC-072: skills.curves.default-max-level — a central tunable for the fallback used when
        // an individual curve's own YAML omits max-level.
        int defaultMaxLevel = Valmora.getInstance() != null
                ? Valmora.getInstance().getConfig().getInt("skills.curves.default-max-level", 60) : 60;
        int maxLevel = Math.max(1, section.getInt("max-level", defaultMaxLevel));

        Expression expression = parser.parse(formula); // pre-compiled once, not per level
        int[] thresholds = new int[maxLevel];
        for (int level = 1; level <= maxLevel; level++) {
            SimpleExecutionContext ctx = new SimpleExecutionContext(null, null, null);
            ctx.set("curve:level", (double) level);
            Object result = expression.evaluate(ctx);
            thresholds[level - 1] = result instanceof Number n ? (int) Math.round(n.doubleValue()) : 0;
        }
        return thresholds;
    }

    /** @return the named curve, or the built-in "default" curve if {@code curveId} is null/unregistered. */
    public XpCurve get(String curveId) {
        XpCurve curve = curves.get(curveId == null ? DEFAULT_CURVE_ID : curveId.toLowerCase(Locale.ROOT));
        return curve != null ? curve : curves.get(DEFAULT_CURVE_ID);
    }

    /** Resets to the built-in-only state (same as a freshly constructed registry). */
    public void clear() {
        curves.clear();
        curves.put(DEFAULT_CURVE_ID, new XpCurve(DEFAULT_THRESHOLDS));
    }
}
