package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes the just-resolved hit result via the {@code hit} namespace:
 * <ul>
 *     <li>{@code $hit.damage$} — final damage dealt after all mitigation</li>
 *     <li>{@code $hit.is_crit$} — whether the hit rolled a critical</li>
 *     <li>{@code $hit.damage_type$} — the {@link org.nakii.valmora.module.combat.DamageType} id</li>
 * </ul>
 * Attached (as {@code "hit:damage"}/{@code "hit:is_crit"}/{@code "hit:damage_type"}) onto
 * {@code DamageCalculator}'s existing per-hit formula context by {@link EnchantDispatcher} right
 * after the {@code DamageResult} is computed — no new context type is created for this.
 */
public class HitVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "hit";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return switch (path[0].toLowerCase()) {
            case "damage" -> context.get("hit:damage", 0.0);
            case "is_crit" -> context.get("hit:is_crit", false);
            case "damage_type" -> context.get("hit:damage_type", "");
            default -> null;
        };
    }
}
