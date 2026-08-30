package org.nakii.valmora.module.script.event.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.item.TargetResolver;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Visual/audio-only lightning strike — see {@link HealEventFactory}'s class doc for why this
 * lives here rather than under {@code module.enchant}. Deliberately uses
 * {@link World#strikeLightningEffect} (no vanilla damage or fire) rather than a real lightning
 * strike: an enchant like {@code thunderlord} pairs this with its own explicit {@code damage}
 * event for calculated damage, and a real strike would additionally deal vanilla lightning damage
 * on top of that — this keeps the event a pure effect, with damage always an explicit, separate
 * step.
 *
 * <p>DSL: {@code strike_lightning [selector]} — {@code selector} per {@link TargetResolver},
 * default {@code @target}.
 */
public class StrikeLightningEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "strike_lightning";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        String selector = args.length > 0 ? args[0] : "@target";

        return context -> {
            for (LivingEntity target : TargetResolver.resolve(selector, context)) {
                Location loc = target.getLocation();
                World world = loc.getWorld();
                if (world != null) world.strikeLightningEffect(loc);
            }
        };
    }
}
