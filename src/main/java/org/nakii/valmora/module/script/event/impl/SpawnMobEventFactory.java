package org.nakii.valmora.module.script.event.impl;

import org.bukkit.Location;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns one or more custom mobs near the caster.
 *
 * DSL:
 *   spawn_mob <mob_id>
 *   spawn_mob <mob_id> <count>
 *   spawn_mob <mob_id> <count> radius:<r>
 */
public class SpawnMobEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "spawn_mob";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length == 0) return ctx -> {};

        String mobId = args[0];

        int count = 1;
        if (args.length > 1) {
            try { count = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        double radius = 0.0;
        for (String arg : args) {
            if (arg.startsWith("radius:")) {
                try { radius = Double.parseDouble(arg.substring(7)); } catch (NumberFormatException ignored) {}
            }
        }

        final int finalCount = Math.max(1, count);
        final double finalRadius = radius;

        return ctx -> {
            MobManager mobManager = ValmoraAPI.getInstance().getMobManager();
            if (mobManager == null) return;

            MobDefinition def = mobManager.getMobDefinition(mobId);
            if (def == null) {
                ValmoraAPI.getInstance().getScriptModule().getValmora()
                        .getLogger().warning("[spawn_mob] Unknown mob id: " + mobId);
                return;
            }

            Location base = ctx.getLocation();
            if (base == null || base.getWorld() == null) return;

            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < finalCount; i++) {
                Location spawnLoc;
                if (finalRadius > 0) {
                    double dx = (rng.nextDouble() * 2 - 1) * finalRadius;
                    double dz = (rng.nextDouble() * 2 - 1) * finalRadius;
                    spawnLoc = base.clone().add(dx, 0, dz);
                } else {
                    spawnLoc = base.clone();
                }
                mobManager.spawnMob(def, spawnLoc);
            }
        };
    }
}
