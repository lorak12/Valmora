package org.nakii.valmora.module.combat;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Lets a {@code combat:pre_damage} pipeline stage contribute a multiplicative damage buff/debuff —
 * the resolution to the "conflict with existing enchants" ordering question raised in
 * docs/COMBAT_PIPELINE_ANALYSIS.md §5 (see the ordering-rule javadoc on
 * {@link DamageCalculator#calculateDamage(org.bukkit.entity.LivingEntity, org.bukkit.entity.LivingEntity, DamageType, double, ExecutionContext)}
 * for exactly where this applies relative to strength/crit/enchants/defense).
 *
 * <p>Multiple calls within the same hit stack multiplicatively (each call multiplies the running
 * total, starting from {@code 1.0}), so two stages each calling {@code multiply_damage 1.5} on the
 * same hit compound to {@code 2.25x}, not {@code 1.5x}.
 *
 * <p>DSL: {@code multiply_damage <factor>} — {@code factor} is a literal number or a single
 * {@code $variable$} token (same convention as {@code stat_modify}'s value argument).
 */
public class MultiplyDamageEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "multiply_damage";
    }

    @Override
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 1;
    }

    @Override
    public String usage() {
        return "multiply_damage <factor>";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};
        String rawFactor = args[0];

        return context -> {
            double factor = resolveDouble(rawFactor, context);
            double current = context.get("dmg:pipeline_multiplier", 1.0);
            context.set("dmg:pipeline_multiplier", current * factor);
        };
    }

    private double resolveDouble(String raw, ExecutionContext context) {
        String value = raw;
        if (raw.startsWith("$") && raw.endsWith("$")) {
            Object resolved = context.getVariableResolver().resolve(raw, context);
            value = resolved != null ? resolved.toString() : "1";
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
}
