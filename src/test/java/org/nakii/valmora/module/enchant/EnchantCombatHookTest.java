package org.nakii.valmora.module.enchant;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.combat.DamageType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Covers {@link EnchantCombatHook}'s composition rule for each modifier key: multiplicative for
 *  {@code damage-multiplier} (mirrors {@code DamageMultiplierLogic}), additive for everything else
 *  (mirrors {@code StatBonusLogic}/{@code DefenseReductionLogic}). */
class EnchantCombatHookTest {

    private DamageModifierContext freshContext() {
        return new DamageModifierContext(10.0, 0.0, 5.0, 50.0, 20.0, DamageType.MELEE);
    }

    private SimpleExecutionContext scriptCtx() {
        return new SimpleExecutionContext(mock(org.bukkit.entity.LivingEntity.class), null, null, null);
    }

    @Test
    void applyAttackComposesDamageMultiplierMultiplicatively() {
        DamageModifierContext ctx = freshContext();
        ctx.setDamageMultiplier(2.0); // some earlier logic already contributed
        var block = new EnchantCombatHook.CompiledCombatModifiers(null, Map.of("damage-multiplier", c -> 1.25));

        EnchantCombatHook.applyAttack(ctx, scriptCtx(), block);

        assertEquals(2.5, ctx.getDamageMultiplier(), 1e-9);
    }

    @Test
    void applyAttackComposesCritFieldsAdditively() {
        DamageModifierContext ctx = freshContext();
        var block = new EnchantCombatHook.CompiledCombatModifiers(null,
                Map.of("crit-chance", c -> 10.0, "crit-damage", c -> 25.0));

        EnchantCombatHook.applyAttack(ctx, scriptCtx(), block);

        assertEquals(15.0, ctx.getCritChance(), 1e-9); // 5.0 + 10.0
        assertEquals(75.0, ctx.getCritDamage(), 1e-9); // 50.0 + 25.0
    }

    @Test
    void applyAttackAccumulatesDefenseShredAcrossMultipleCalls() {
        DamageModifierContext ctx = freshContext();
        var block = new EnchantCombatHook.CompiledCombatModifiers(null, Map.of("defense-shred-percent", c -> 3.0));

        EnchantCombatHook.applyAttack(ctx, scriptCtx(), block); // simulate two stacked enchants
        EnchantCombatHook.applyAttack(ctx, scriptCtx(), block);

        assertEquals(6.0, ctx.getDefenseShredPercent(), 1e-9);
    }

    @Test
    void applyAttackSkipsEverythingWhenConditionsFail() {
        DamageModifierContext ctx = freshContext();
        var block = new EnchantCombatHook.CompiledCombatModifiers(c -> false, Map.of("damage-multiplier", c -> 5.0));

        EnchantCombatHook.applyAttack(ctx, scriptCtx(), block);

        assertEquals(1.0, ctx.getDamageMultiplier(), 1e-9); // untouched default
    }

    @Test
    void applyAttackWithNullBlockIsANoOp() {
        DamageModifierContext ctx = freshContext();
        assertDoesNotThrow(() -> EnchantCombatHook.applyAttack(ctx, scriptCtx(), null));
        assertEquals(1.0, ctx.getDamageMultiplier(), 1e-9);
    }

    @Test
    void applyDefendAccumulatesDamageReductionPercent() {
        DamageModifierContext ctx = freshContext();
        var block = new EnchantCombatHook.CompiledCombatModifiers(null, Map.of("damage-reduction-percent", c -> 8.0));

        EnchantCombatHook.applyDefend(ctx, scriptCtx(), block);
        EnchantCombatHook.applyDefend(ctx, scriptCtx(), block);

        assertEquals(16.0, ctx.getDamageReductionPercent(), 1e-9);
    }
}
