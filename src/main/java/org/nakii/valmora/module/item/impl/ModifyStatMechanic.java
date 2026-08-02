package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.TemporaryStatService;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Temporarily (or permanently, with {@code duration: -1}) alters a player stat. The modifier is
 * tracked by {@link TemporaryStatService} so it survives the next stat recalculation, then a
 * recalculation is triggered so the change takes effect immediately and is undone on expiry.
 */
public class ModifyStatMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "MODIFY_STAT";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;

        String stat = context.getString("stat", "");
        if (stat.isBlank()) return;

        double amount = context.resolveDouble("amount", 0.0);
        double duration = context.resolveDouble("duration", -1.0);

        ValmoraProfile profile = Valmora.getInstance().getPlayerManager()
                .getSession(player.getUniqueId()).getActiveProfile();
        if (profile == null) return;

        if (duration < 0) {
            // Passive/permanent modifier. These fire from within recalculateStats (PASSIVE
            // trigger), so apply directly to the effective stats and do NOT trigger another
            // recalculation — that would recurse and accumulate.
            profile.getStatManager().addModifier(stat, amount);
            return;
        }

        // Remove any existing modifier for the same stat so re-casting refreshes the buff
        // rather than stacking it (prevents unbounded accumulation on short cooldowns).
        TemporaryStatService.removeForStat(player.getUniqueId(), stat);

        // Timed modifier: track it so it survives recalculations until it expires, then
        // recalculate now (to apply) and again when it expires (to remove).
        TemporaryStatService.add(player.getUniqueId(), stat, amount, duration);
        Valmora plugin = Valmora.getInstance();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> profile.getStatManager().recalculateStats(player));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> profile.getStatManager().recalculateStats(player), (long) (duration * 20) + 1);
    }
}
