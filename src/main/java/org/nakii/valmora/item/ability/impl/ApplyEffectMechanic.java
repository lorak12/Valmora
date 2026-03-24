package org.nakii.valmora.item.ability.impl;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Registry;
import org.nakii.valmora.item.ability.AbilityMechanic;

public class ApplyEffectMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "APPLY_EFFECT";
    }

    @Override
    public void execute(Player caster, LivingEntity target, ConfigurationSection params) {
        String targetType = params.getString("target", "@target");
        LivingEntity actualTarget = targetType.equalsIgnoreCase("@player") ? caster : target;
        
        if (actualTarget == null) return;

        String effectStr = params.getString("effect", "").toLowerCase();
        
        // Paper 1.21 uses Registry for PotionEffectType
        PotionEffectType effectType = Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft(effectStr));
        if (effectType == null) return;

        double durationSeconds = params.getDouble("duration", 5.0);
        int durationTicks;
        
        // If duration is -1, it's an infinite passive effect (Infinite in Bukkit is usually PotionEffect.INFINITE_DURATION or a massive number)
        if (durationSeconds == -1) {
            durationTicks = PotionEffect.INFINITE_DURATION;
        } else {
            durationTicks = (int) (durationSeconds * 20); // Convert seconds to ticks
        }

        // Amplifier in YAML is usually 1-based, but Bukkit is 0-based.
        // So an amplifier of 1 in config = Level 1 (0 in Bukkit).
        int amplifier = params.getInt("amplifier", 1) - 1;
        if (amplifier < 0) amplifier = 0;

        boolean hideParticles = params.getBoolean("hide-particles", false);

        PotionEffect effect = new PotionEffect(effectType, durationTicks, amplifier, false, !hideParticles, true);
        actualTarget.addPotionEffect(effect);
    }
}