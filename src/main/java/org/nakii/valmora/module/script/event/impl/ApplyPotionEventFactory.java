package org.nakii.valmora.module.script.event.impl;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.item.TargetResolver;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Applies a potion effect to a resolved target — the script-DSL counterpart to the
 * {@code APPLY_EFFECT} ability mechanic ({@code ApplyEffectMechanic}), for use directly inside
 * pipeline stages / event lists rather than an item ability's {@code mechanics:} block.
 *
 * DSL: {@code apply_potion <effect> <amplifier> <durationSeconds> [<target-selector>]}
 * ({@code amplifier} is 1-based, matching item ability YAML; {@code target-selector} defaults to
 * {@code @self} — see {@link TargetResolver}.)
 */
public class ApplyPotionEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "apply_potion";
    }

    @Override
    public int minArgs() {
        return 3;
    }

    @Override
    public int maxArgs() {
        return 4;
    }

    @Override
    public String usage() {
        return "apply_potion <effect> <duration> <amplifier> [selector]";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return context -> {};

        PotionEffectType effectType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(args[0].toLowerCase()));
        if (effectType == null) return context -> {};

        double durationSeconds;
        int amplifier;
        try {
            amplifier = Integer.parseInt(args[1]) - 1; // YAML is 1-based, Bukkit is 0-based
            durationSeconds = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            return context -> {};
        }
        int durationTicks = durationSeconds < 0 ? PotionEffect.INFINITE_DURATION : (int) (durationSeconds * 20);
        int finalAmplifier = Math.max(0, amplifier);
        String selector = args.length > 3 ? args[3] : "@self";

        return context -> {
            PotionEffect effect = new PotionEffect(effectType, durationTicks, finalAmplifier, false, true, true);
            for (LivingEntity target : TargetResolver.resolve(selector, context)) {
                target.addPotionEffect(effect);
            }
        };
    }
}
