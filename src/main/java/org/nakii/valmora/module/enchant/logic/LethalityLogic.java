package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.enchant.EnchantmentLogic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Lethality" (`valmora:lethality`) — each hit stacks a defense-reduction debuff on the target
 * (up to {@link #MAX_STACKS} stacks, each stack refreshing a {@link #STACK_DURATION_MS} timer).
 * Added 2026-08-07 to wire up one of the 7 previously-inert shipped enchants.
 */
public class LethalityLogic implements EnchantmentLogic {

    private static final int MAX_STACKS = 4;
    private static final long STACK_DURATION_MS = 4_000;

    private final double percentPerLevelPerStack;
    // victim uuid -> [stackCount, expiryMillis]
    private final Map<UUID, long[]> stacks = new ConcurrentHashMap<>();

    public LethalityLogic(double percentPerLevelPerStack) {
        this.percentPerLevelPerStack = percentPerLevelPerStack;
    }

    @Override
    public void modifyAttack(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {
        long[] state = stacks.get(victim.getUniqueId());
        if (state == null) return;
        long now = System.currentTimeMillis();
        int activeStacks = now < state[1] ? (int) state[0] : 0;
        if (activeStacks <= 0) return;

        double reduction = context.getDefense() * (percentPerLevelPerStack / 100.0) * level * activeStacks;
        context.setDefense(Math.max(0, context.getDefense() - reduction));
    }

    @Override
    public void onPostAttack(DamageResult result, LivingEntity attacker, LivingEntity victim, int level) {
        if (result.isImmune()) return;
        long now = System.currentTimeMillis();
        long[] state = stacks.computeIfAbsent(victim.getUniqueId(), k -> new long[]{0, 0});
        int current = now < state[1] ? (int) state[0] : 0;
        state[0] = Math.min(MAX_STACKS, current + 1);
        state[1] = now + STACK_DURATION_MS;
    }
}
