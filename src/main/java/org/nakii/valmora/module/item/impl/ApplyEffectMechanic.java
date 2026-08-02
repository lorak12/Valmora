package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Registry;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.TargetResolver;

import java.util.List;

public class ApplyEffectMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "APPLY_EFFECT";
    }

    @Override
    public void execute(ExecutionContext context) {
        String effectStr = context.getString("effect", "").toLowerCase();

        // Paper 1.21 uses Registry for PotionEffectType
        PotionEffectType effectType = Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft(effectStr));
        if (effectType == null) return;

        double durationSeconds = context.resolveDouble("duration", 5.0);
        int durationTicks;
        // If duration is -1, it's an infinite passive effect.
        if (durationSeconds == -1) {
            durationTicks = PotionEffect.INFINITE_DURATION;
        } else {
            durationTicks = (int) (durationSeconds * 20); // Convert seconds to ticks
        }

        // Amplifier in YAML is 1-based, Bukkit is 0-based.
        int amplifier = context.resolveInt("amplifier", 1) - 1;
        if (amplifier < 0) amplifier = 0;

        boolean hideParticles = context.getBoolean("hide-particles", false);

        List<LivingEntity> targets = TargetResolver.resolve(context.getString("target", "@target"), context);
        for (LivingEntity target : targets) {
            PotionEffect effect = new PotionEffect(effectType, durationTicks, amplifier, false, !hideParticles, true);
            target.addPotionEffect(effect);
        }
    }
}
