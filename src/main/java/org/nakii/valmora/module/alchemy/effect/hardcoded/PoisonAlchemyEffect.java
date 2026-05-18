package org.nakii.valmora.module.alchemy.effect.hardcoded;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Deals 10 * level damage per tick (once per alchemy tick-interval, default 1s).
 */
public class PoisonAlchemyEffect implements HardcodedAlchemyEffect {

    @Override
    public String getEffectId() { return "poison"; }

    @Override
    public void onApply(LivingEntity entity, int level, int durationSeconds) {}

    @Override
    public void onExpire(LivingEntity entity, int level) {}

    @Override
    public void onTick(Player player, int level) {
        if (player.isDead()) return;
        double damage = 10.0 * level;
        ValmoraAPI api = ValmoraAPI.getInstance();
        ValmoraPlayer vp = api.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return;
        profile.getPlayerState().reduceHealth(damage);
        api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
    }
}
