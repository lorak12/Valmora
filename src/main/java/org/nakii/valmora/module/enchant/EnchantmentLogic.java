package org.nakii.valmora.module.enchant;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.stat.StatManager;

public interface EnchantmentLogic {

    default void applyStats(LivingEntity entity, int level, StatManager profile) {}

    default void modifyAttack(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {}

    default void modifyDefend(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {}

    default void onPostAttack(DamageResult result, LivingEntity attacker, LivingEntity victim, int level) {}

    default void onPostDefend(DamageResult result, LivingEntity attacker, LivingEntity victim, int level) {}
}