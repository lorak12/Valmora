package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.combat.DamageModifierContext;

import java.util.Map;

/**
 * Compiles and applies an enchant's YAML {@code combat.modify-attack}/{@code modify-defend} block:
 * a gate ({@code conditions:}, evaluated against the current formula/hit context) plus a set of
 * named modifier formulas, each evaluated once and folded into {@link DamageModifierContext} using
 * the same composition rule the existing (Java) {@code EnchantmentLogic} implementations already
 * use for that field — multiplicative for {@code damage-multiplier} (the formula is the full
 * factor, e.g. {@code "1.0 + (0.25 * $level$)"}, mirroring {@code DamageMultiplierLogic}), additive
 * for everything else (the formula is a delta), mirroring {@code StatBonusLogic}/{@code
 * DefenseReductionLogic}'s style.
 *
 * <p>Not a {@link org.nakii.valmora.api.pipeline.HookBus} stage — pass/fail action dispatch (see
 * {@link EnchantTriggerStage}) and numeric modifier accumulation are different mechanisms, matching
 * the YAML schema's own {@code combat:} vs {@code triggers:} split.
 */
public class EnchantCombatHook {

    /** One compiled {@code modify-attack:} or {@code modify-defend:} block. */
    public record CompiledCombatModifiers(Condition conditions, Map<String, Expression> modifiers) {
    }

    public static void applyAttack(DamageModifierContext ctx, ExecutionContext scriptCtx, CompiledCombatModifiers block) {
        if (block == null) return;
        if (block.conditions() != null && !block.conditions().evaluate(scriptCtx)) return;

        for (Map.Entry<String, Expression> entry : block.modifiers().entrySet()) {
            double value = asDouble(entry.getValue().evaluate(scriptCtx));
            switch (entry.getKey().toLowerCase()) {
                case "damage-multiplier" -> ctx.setDamageMultiplier(ctx.getDamageMultiplier() * value);
                case "crit-chance" -> ctx.setCritChance(ctx.getCritChance() + value);
                case "crit-damage" -> ctx.setCritDamage(ctx.getCritDamage() + value);
                case "defense-shred-percent" -> ctx.addDefenseShredPercent(value);
                case "knockback-multiplier" -> ctx.setKnockbackMultiplier(ctx.getKnockbackMultiplier() * value);
                default -> { /* unknown modifier key — ignored, matches the load-time warning pattern elsewhere */ }
            }
        }
    }

    public static void applyDefend(DamageModifierContext ctx, ExecutionContext scriptCtx, CompiledCombatModifiers block) {
        if (block == null) return;
        if (block.conditions() != null && !block.conditions().evaluate(scriptCtx)) return;

        for (Map.Entry<String, Expression> entry : block.modifiers().entrySet()) {
            double value = asDouble(entry.getValue().evaluate(scriptCtx));
            switch (entry.getKey().toLowerCase()) {
                case "damage-reduction-percent" -> ctx.addDamageReductionPercent(value);
                case "damage-multiplier" -> ctx.setDamageMultiplier(ctx.getDamageMultiplier() * value);
                case "knockback-multiplier" -> ctx.setKnockbackMultiplier(ctx.getKnockbackMultiplier() * value);
                default -> { /* unknown modifier key — ignored */ }
            }
        }
    }

    private static double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
