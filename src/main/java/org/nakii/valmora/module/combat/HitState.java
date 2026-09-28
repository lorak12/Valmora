package org.nakii.valmora.module.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.flow.FlowState;

/**
 * State of one {@code combat.entity_hit} or {@code combat.environment_damage} flow run: the event,
 * who hit whom, the damage type and — once {@code calculate_damage} ran — the {@link DamageResult}.
 *
 * <p>Script-visible as {@code $dmg.final_damage$}, {@code $dmg.is_critical$},
 * {@code $dmg.damage_type$}, {@code $dmg.is_immune$} (once calculated) and {@code $dmg.raw_damage$}
 * (vanilla's damage before Valmora replaced it). Writable with {@code pipeline_set}:
 * {@code dmg.damage_type} (before calculation) and {@code dmg.final_damage} (after it).
 */
public final class HitState implements FlowState {

    final EntityDamageEvent event;
    final LivingEntity victim;
    LivingEntity attacker;
    DamageType type;
    double rawDamage;
    DamageResult result;
    /**
     * Whether anything script-side (a stage, hook, script step or condition) has seen this hit —
     * only then can a {@code multiply_damage} multiplier be on the context, so only then does
     * {@code calculate_damage} pay for looking it up.
     */
    boolean scripted;

    public HitState(EntityDamageEvent event, LivingEntity victim) {
        this.event = event;
        this.victim = victim;
    }

    public DamageResult result() {
        return result;
    }

    /** Replaces the result's damage, keeping everything else (immunity, knockback, crit). */
    void rescale(double finalDamage) {
        DamageResult old = result;
        result = new DamageResult(finalDamage, old.getDamageType(), old.isCritical(), old.getAttacker(), old.getVictim());
        result.setImmune(old.isImmune());
        result.setKnockbackMultiplier(old.getKnockbackMultiplier());
    }

    @Override
    public void publish(ExecutionContext ctx) {
        scripted = true;
        ctx.set("dmg:raw_damage", rawDamage);
        if (result != null) {
            ctx.set("dmg:final_damage", result.getFinalDamage());
            ctx.set("dmg:is_critical", result.isCritical());
            ctx.set("dmg:damage_type", result.getDamageType().getId());
            ctx.set("dmg:is_immune", result.isImmune());
        } else if (type != null) {
            ctx.set("dmg:damage_type", type.getId());
        }
    }

    @Override
    public boolean write(String key, Object value) {
        switch (key) {
            case "dmg.final_damage" -> {
                if (result == null) return false;
                rescale(Math.max(0, FlowState.asDouble(value, result.getFinalDamage())));
            }
            case "dmg.damage_type" -> {
                if (result != null) return false; // too late — the damage was calculated with the old type
                String id = FlowState.asString(value);
                DamageType found = id == null ? null : DamageType.find(id).orElse(null);
                if (found == null) return false;
                type = found;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
