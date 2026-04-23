package org.nakii.valmora.module.enchant;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.stat.StatManager;

public interface EnchantmentLogic {

    default void applyStats(Player player, int level, StatManager stats) {
    }

    default void onAttack(DamageResult result, int level) {
    }

    default void onDefend(DamageResult result, int level) {
    }
}