package org.nakii.valmora.module.alchemy.effect.hardcoded;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;

/**
 * Delegates to a vanilla PotionEffect for the full duration.
 * Used for jump_boost, night_vision, invisibility, and fire_resistance.
 */
public class VanillaAlchemyEffect implements HardcodedAlchemyEffect {

    private final String effectId;
    private final PotionEffectType vanillaType;
    private final boolean amplifierScales;

    /**
     * @param amplifierScales if true, amplifier = level - 1; otherwise always 0
     */
    public VanillaAlchemyEffect(String effectId, PotionEffectType vanillaType, boolean amplifierScales) {
        this.effectId = effectId;
        this.vanillaType = vanillaType;
        this.amplifierScales = amplifierScales;
    }

    @Override
    public String getEffectId() { return effectId; }

    @Override
    public void onApply(LivingEntity entity, int level, int durationSeconds) {
        int amplifier = amplifierScales ? level - 1 : 0;
        entity.addPotionEffect(new PotionEffect(vanillaType, durationSeconds * 20, amplifier, false, false));
    }

    @Override
    public void onExpire(LivingEntity entity, int level) {
        entity.removePotionEffect(vanillaType);
    }
}
