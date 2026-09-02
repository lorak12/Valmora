package org.nakii.valmora.module.alchemy.effect.hardcoded;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Instant health restore on apply.
 * Level 1: 20 HP, Level 2: 50 HP, each subsequent level +50 HP.
 */
public class HealingAlchemyEffect implements HardcodedAlchemyEffect {

    private static final double[] DEFAULT_HEAL_VALUES = {20, 50, 100, 150, 200, 250, 300, 350};

    @Override
    public String getEffectId() { return "healing"; }

    @Override
    public void onApply(LivingEntity entity, int level, int durationSeconds) {
        double amount = getHealAmount(level);
        if (entity instanceof Player player) {
            healPlayer(player, amount);
        } else {
            entity.setHealth(Math.min(entity.getAttribute(
                    org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                    entity.getHealth() + amount));
        }
    }

    @Override
    public void onExpire(LivingEntity entity, int level) {}

    /** HC-190: {@code alchemy.effects.healing.values} — per-level curve, falls back to the
     *  original hardcoded values when unset. */
    private double getHealAmount(int level) {
        var plugin = org.nakii.valmora.Valmora.getInstance();
        java.util.List<Double> configured = plugin != null
                ? plugin.getConfig().getDoubleList("alchemy.effects.healing.values") : java.util.List.of();
        double[] values = configured.isEmpty() ? DEFAULT_HEAL_VALUES
                : configured.stream().mapToDouble(Double::doubleValue).toArray();
        int idx = Math.max(0, Math.min(level - 1, values.length - 1));
        return values[idx];
    }

    private void healPlayer(Player player, double amount) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        ValmoraPlayer vp = api.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return;
        profile.getPlayerState().heal(amount, profile.getStatManager());
        api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
    }
}
