package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Registry;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

public class ApplyEffectMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "APPLY_EFFECT";
    }

    @Override
    public void execute(ExecutionContext context) {
        String targetType = context.getString("target", "@target");
        LivingEntity actualTarget = targetType.equalsIgnoreCase("@player") 
                ? (context.getCaster() instanceof LivingEntity ? (LivingEntity) context.getCaster() : null)
                : context.getTarget().orElse(null);
        
        if (actualTarget == null) return;

        String effectStr = context.getString("effect", "").toLowerCase();
        
        // Paper 1.21 uses Registry for PotionEffectType
        PotionEffectType effectType = Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft(effectStr));
        if (effectType == null) return;

        double durationSeconds = context.getDouble("duration", 5.0);
        int durationTicks;
        
        // If duration is -1, it's an infinite passive effect (Infinite in Bukkit is usually PotionEffect.INFINITE_DURATION or a massive number)
        if (durationSeconds == -1) {
            durationTicks = PotionEffect.INFINITE_DURATION;
        } else {
            durationTicks = (int) (durationSeconds * 20); // Convert seconds to ticks
        }

        // Amplifier in YAML is usually 1-based, but Bukkit is 0-based.
        // So an amplifier of 1 in config = Level 1 (0 in Bukkit).
        int amplifier = context.getInt("amplifier", 1) - 1;
        if (amplifier < 0) amplifier = 0;

        boolean hideParticles = context.getBoolean("hide-particles", false);

        PotionEffect effect = new PotionEffect(effectType, durationTicks, amplifier, false, !hideParticles, true);
        actualTarget.addPotionEffect(effect);
    }
}
