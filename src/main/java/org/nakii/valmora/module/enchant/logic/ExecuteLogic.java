package org.nakii.valmora.module.enchant.logic;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.enchant.EnchantmentLogic;

/**
 * "Execute" (`valmora:execute`) — increases damage by {@code percentPerMissingPercent} for every
 * 1% of the target's health that's already missing, per level. Added 2026-08-07 to wire up one
 * of the 7 previously-inert shipped enchants (`enchants/example_enchantments.yml`).
 */
public class ExecuteLogic implements EnchantmentLogic {

    private final double percentPerMissingPercent;

    public ExecuteLogic(double percentPerMissingPercent) {
        this.percentPerMissingPercent = percentPerMissingPercent;
    }

    @Override
    public void modifyAttack(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {
        var maxHealthAttr = victim.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;
        double maxHealth = maxHealthAttr.getValue();
        if (maxHealth <= 0) return;

        double missingPercent = Math.max(0, 100.0 * (1.0 - victim.getHealth() / maxHealth));
        double bonus = 1.0 + (percentPerMissingPercent / 100.0) * missingPercent * level;
        context.setDamageMultiplier(context.getDamageMultiplier() * bonus);
    }
}
