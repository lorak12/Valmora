package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.MechanicDefaults;

/**
 * Begins a "charge your jump by sneaking" cycle (e.g. Spring Boots' "To the Moon!"). Bind this to
 * a {@code SNEAK}-triggered ability — {@code AbilityTriggerListener.onSneak} already fires on
 * sneak-<i>start</i> only, so this only ever records the charge start; the launch itself happens
 * on release, handled by a dedicated {@code ChargeJumpListener} reading
 * {@link ChargeJumpTracker#releaseCharge}. Params (all optional): {@code max-charge-ms} (default
 * 2000), {@code min-y-force} (default 0.4), {@code max-y-force} (default 2.2).
 */
public class ChargeJumpMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "CHARGE_JUMP";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;

        long maxChargeMs = (long) context.resolveDouble("max-charge-ms", MechanicDefaults.getDouble("charge-jump", "max-charge-ms", 2000.0));
        double minYForce = context.resolveDouble("min-y-force", MechanicDefaults.getDouble("charge-jump", "min-y-force", 0.4));
        double maxYForce = context.resolveDouble("max-y-force", MechanicDefaults.getDouble("charge-jump", "max-y-force", 2.2));

        ChargeJumpTracker.startCharge(player.getUniqueId(),
                new ChargeJumpTracker.ChargeParams(maxChargeMs, minYForce, maxYForce));
    }
}
