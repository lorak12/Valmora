package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.TargetResolver;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.util.List;

public class HealMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "HEAL";
    }

    @Override
    public void execute(ExecutionContext context) {
        double healAmount = context.resolveDouble("heal", 0.0);
        if (healAmount <= 0) return;
        String selector = context.getString("target", "@player");

        int ticks = Math.max(1, context.getInt("ticks", 1));
        double intervalSeconds = context.getDouble("interval", 1.0);

        applyOnce(context, selector, healAmount);
        if (ticks <= 1) return;

        Valmora plugin = Valmora.getInstance();
        long intervalTicks = Math.max(1, (long) (intervalSeconds * 20));
        final int[] remaining = {ticks - 1};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            applyOnce(context, selector, healAmount);
            if (--remaining[0] <= 0) task.cancel();
        }, intervalTicks, intervalTicks);
    }

    private void applyOnce(ExecutionContext context, String selector, double healAmount) {
        List<LivingEntity> targets = TargetResolver.resolve(selector, context);
        for (LivingEntity target : targets) {
            if (!(target instanceof Player healTarget)) continue; // Only players have a Valmora profile.

            ValmoraProfile profile = Valmora.getInstance().getPlayerManager()
                    .getSession(healTarget.getUniqueId()).getActiveProfile();
            if (profile == null) continue;

            profile.getPlayerState().heal(healAmount, profile.getStatManager());
            Valmora.getInstance().getPlayerManager()
                    .syncVisualHealth(healTarget, profile.getPlayerState(), profile.getStatManager());
        }
    }
}
