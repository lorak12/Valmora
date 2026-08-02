package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

/**
 * Grants coins to the caster (e.g. Raider Axe "earn 20 coins from kills").
 */
public class GiveCoinsMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "GIVE_COINS";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;
        double amount = context.resolveDouble("amount", 0.0);
        if (amount <= 0) return;
        ValmoraAPI.getInstance().getEconomy().addCoins(player, amount);
    }
}
