package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Registry;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.MechanicDefaults;
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
        if (effectStr.isEmpty()) return;

        double durationSeconds = context.resolveDouble("duration", MechanicDefaults.getDouble("apply-effect", "duration-default", 5.0));
        // Amplifier in YAML is 1-based (matches AlchemyManager's 1-based "level").
        int level = context.resolveInt("amplifier", MechanicDefaults.getInt("apply-effect", "amplifier-default", 1));
        if (level < 1) level = 1;

        List<LivingEntity> targets = TargetResolver.resolve(context.getString("target", "@target"), context);

        // Prefer Valmora's own alchemy effect system (module/alchemy — stat-driven buffs/debuffs
        // like "slowness"/"strength" that scale via config, not vanilla PotionEffectType) so an
        // ability configured with e.g. effect: "slowness" actually applies the plugin's own effect
        // instead of silently reaching for the vanilla potion effect of the same name. Only fall
        // back to a raw vanilla PotionEffectType for ids the alchemy registry doesn't define
        // (e.g. "blindness", which has no plugin-side equivalent).
        AlchemyManager alchemyManager = ValmoraAPI.getInstance().getAlchemyManager();
        if (alchemyManager != null && alchemyManager.getEffect(effectStr).isPresent()) {
            int durationSecondsInt = (int) Math.round(durationSeconds);
            for (LivingEntity target : targets) {
                alchemyManager.applyEffect(target, effectStr, level, durationSecondsInt);
            }
            return;
        }

        // Paper 1.21 uses Registry for PotionEffectType
        PotionEffectType effectType = Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft(effectStr));
        if (effectType == null) return;

        int durationTicks;
        // If duration is -1, it's an infinite passive effect.
        if (durationSeconds == -1) {
            durationTicks = PotionEffect.INFINITE_DURATION;
        } else {
            durationTicks = (int) (durationSeconds * 20); // Convert seconds to ticks
        }

        // Bukkit's PotionEffect amplifier is 0-based.
        int amplifier = Math.max(0, level - 1);

        boolean hideParticles = context.getBoolean("hide-particles", MechanicDefaults.getBoolean("apply-effect", "hide-particles", false));

        for (LivingEntity target : targets) {
            PotionEffect effect = new PotionEffect(effectType, durationTicks, amplifier, false, !hideParticles, true);
            target.addPotionEffect(effect);
            if (durationTicks == PotionEffect.INFINITE_DURATION) {
                org.nakii.valmora.module.item.PassiveEffects.record(target, effectType);
            }
        }
    }
}
