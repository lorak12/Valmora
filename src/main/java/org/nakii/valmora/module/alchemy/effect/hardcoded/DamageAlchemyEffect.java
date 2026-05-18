package org.nakii.valmora.module.alchemy.effect.hardcoded;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Instantly deals 5 * level true damage on apply. Intended for splash use.
 */
public class DamageAlchemyEffect implements HardcodedAlchemyEffect {

    @Override
    public String getEffectId() { return "damage"; }

    @Override
    public void onApply(LivingEntity entity, int level, int durationSeconds) {
        double trueDamage = 5.0 * level;
        if (entity instanceof Player player) {
            ValmoraAPI api = ValmoraAPI.getInstance();
            ValmoraPlayer vp = api.getPlayerManager().getSession(player.getUniqueId());
            if (vp == null) return;
            ValmoraProfile profile = vp.getActiveProfile();
            if (profile == null) return;
            profile.getPlayerState().reduceHealth(trueDamage);
            api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
        } else {
            entity.setHealth(Math.max(0, entity.getHealth() - trueDamage));
        }
    }

    @Override
    public void onExpire(LivingEntity entity, int level) {}
}
