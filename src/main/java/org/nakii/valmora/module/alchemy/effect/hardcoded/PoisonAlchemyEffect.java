package org.nakii.valmora.module.alchemy.effect.hardcoded;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Deals 10 * level true damage per tick (once per alchemy tick-interval, default 1s), to any
 * {@link LivingEntity} holding the effect — not just players (generalized 2026-08-07; previously
 * only ticked for players since the tick loop only iterated online players).
 */
public class PoisonAlchemyEffect implements HardcodedAlchemyEffect {

    @Override
    public String getEffectId() { return "poison"; }

    @Override
    public void onApply(LivingEntity entity, int level, int durationSeconds) {}

    @Override
    public void onExpire(LivingEntity entity, int level) {}

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.isDead()) return;
        double damage = 10.0 * level;
        if (entity instanceof Player player) {
            ValmoraAPI api = ValmoraAPI.getInstance();
            ValmoraPlayer vp = api.getPlayerManager().getSession(player.getUniqueId());
            if (vp == null) return;
            ValmoraProfile profile = vp.getActiveProfile();
            if (profile == null) return;
            profile.getPlayerState().reduceHealth(damage);
            api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
        } else {
            // Same true-damage pattern as DamageAlchemyEffect's non-player branch — no profile/stat
            // system exists for vanilla mobs, so this bypasses the damage event entirely.
            entity.setHealth(Math.max(0, entity.getHealth() - damage));
        }
    }
}
