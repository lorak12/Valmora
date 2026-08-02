package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

/**
 * Removes coins from the caster's purse (e.g. Crown of Greed "costs 100x weapon damage").
 */
public class TakeCoinsMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "TAKE_COINS";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;
        double amount = context.resolveDouble("amount", 0.0);
        if (amount <= 0) return;
        ValmoraAPI.getInstance().getEconomy().removeCoins(player, amount);
    }
}
