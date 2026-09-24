package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.combat.DamageCalculator;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.item.TargetResolver;
import org.nakii.valmora.module.script.event.EventArgs;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Generic entity-damage event — see {@link HealEventFactory}'s class doc for why this lives here
 * rather than under {@code module.enchant} despite being added for the enchant overhaul (e.g.
 * {@code thorns}' {@code ON_DEFEND_POST} reflecting damage back at the attacker).
 *
 * <p>DSL: {@code damage <selector> <amount> [type]} — {@code selector} per {@link TargetResolver},
 * {@code amount} a literal or {@code $}-formula (see {@code EventArgs}), {@code type} a
 * {@link DamageType} id (default {@code MAGIC}, matching the ability-mechanic equivalent {@code
 * DamageMechanic}). Routes through {@link DamageCalculator#calculateDamage} exactly like {@code
 * DamageMechanic} does, so defense/resistances/damage-indicator all apply consistently.
 */
public class DamageEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "damage";
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 3;
    }

    @Override
    public String usage() {
        return "damage <selector> <amount> [type]";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return context -> {};
        String selector = args[0];
        String rawAmount = args[1];
        String rawType = args.length > 2 ? args[2] : "MAGIC";

        return context -> {
            double amount = EventArgs.resolveDouble(rawAmount, context);
            if (amount <= 0) return;
            DamageType damageType = mapType(rawType);

            for (LivingEntity target : TargetResolver.resolve(selector, context)) {
                if (target.isDead()) continue;
                DamageResult result = DamageCalculator.calculateDamage(context.getCaster(), target, damageType, amount);
                result.apply();
                ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(result);
            }
        };
    }

    private DamageType mapType(String raw) {
        String upper = raw.toUpperCase();
        if (upper.equals("PHYSICAL")) return DamageType.MELEE;
        try {
            return DamageType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return DamageType.MAGIC;
        }
    }
}
