package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.combat.DamageCalculator;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.TargetResolver;

import java.util.List;

public class DamageMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "damage";
    }

    @Override
    public void execute(ExecutionContext context) {
        // Accept the new schema ("damage"/"damage-type") and the legacy keys ("amount"/"type").
        double amount = context.getParams().contains("damage")
                ? context.resolveDouble("damage", 1.0)
                : context.resolveDouble("amount", 1.0);

        String damageTypeStr = context.getParams().contains("damage-type")
                ? context.getString("damage-type", "MAGIC")
                : context.getString("type", "MAGIC");
        DamageType damageType = mapType(damageTypeStr);
        String selector = context.getString("target", "@target");

        int ticks = Math.max(1, context.getInt("ticks", 1));
        double intervalSeconds = context.getDouble("interval", 1.0);

        // First burst happens immediately; remaining bursts (for damage-over-time) are scheduled.
        applyOnce(context, selector, damageType, amount);
        if (ticks <= 1) return;

        org.nakii.valmora.Valmora plugin = org.nakii.valmora.Valmora.getInstance();
        long intervalTicks = Math.max(1, (long) (intervalSeconds * 20));
        final int[] remaining = {ticks - 1};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (context.getCaster() == null || context.getCaster().isDead() || remaining[0] <= 0) {
                task.cancel();
                return;
            }
            applyOnce(context, selector, damageType, amount);
            if (--remaining[0] <= 0) task.cancel();
        }, intervalTicks, intervalTicks);
    }

    private void applyOnce(ExecutionContext context, String selector, DamageType damageType, double amount) {
        List<LivingEntity> targets = TargetResolver.resolve(selector, context);
        for (LivingEntity target : targets) {
            if (target.isDead()) continue;
            DamageResult result = DamageCalculator.calculateDamage(context.getCaster(), target, damageType, amount);
            result.apply();
            ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(result);
        }
    }

    private DamageType mapType(String raw) {
        // Schema uses PHYSICAL; the combat engine's melee equivalent is MELEE.
        String upper = raw.toUpperCase();
        if (upper.equals("PHYSICAL")) return DamageType.MELEE;
        try {
            return DamageType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return DamageType.MAGIC;
        }
    }
}
