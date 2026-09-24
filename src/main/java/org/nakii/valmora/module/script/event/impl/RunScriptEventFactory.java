package org.nakii.valmora.module.script.event.impl;

import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules an inner event to fire repeatedly on the main thread.
 *
 * DSL:
 *   run_script <interval_ticks> <times> <inner_event...>
 *
 * interval_ticks — how many ticks between each firing
 * times          — how many times to fire (must be > 0)
 *
 * Example: run_script 20 5 spawn_mob zombie_minion 1
 *   → spawns a zombie_minion once per second for 5 seconds
 */
public class RunScriptEventFactory implements EventFactory {

    private final ScriptModule module;

    public RunScriptEventFactory(ScriptModule module) {
        this.module = module;
    }

    @Override
    public String getName() {
        return "run_script";
    }

    @Override
    public int minArgs() {
        return 3;
    }

    @Override
    public String usage() {
        return "run_script <interval_ticks> <times> <event...>";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return ctx -> {};

        long interval;
        int times;
        try {
            interval = Long.parseLong(args[0]);
            times = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            org.nakii.valmora.infrastructure.config.diag.Diagnostics.error("run_script: interval and times must be whole numbers, got '"
                    + args[0] + "' and '" + args[1] + "'", "usage: " + usage());
            return ctx -> {};
        }

        if (interval <= 0 || times <= 0) {
            org.nakii.valmora.infrastructure.config.diag.Diagnostics.error("run_script: interval and times must be greater than 0");
            return ctx -> {};
        }

        // Re-parse the inner event from its original text so quoted arguments survive.
        CompiledEvent inner = module.getEventParser().parse(options.rawArgsAfter(2, args));

        return ctx -> {
            // Captured once at schedule time — a player caster who logs out mid-sequence would
            // otherwise leave this task referencing a stale/offline Player indefinitely until the
            // repeat count ran out (fixed 2026-08-07). Non-player casters (mobs, etc.) have no
            // analogous liveness check here and continue as before.
            org.bukkit.entity.Player casterPlayer = ctx.getCaster() instanceof org.bukkit.entity.Player p ? p : null;

            AtomicInteger remaining = new AtomicInteger(times);
            BukkitTask[] taskHolder = new BukkitTask[1];
            taskHolder[0] = module.getValmora().getServer().getScheduler()
                    .runTaskTimer(module.getValmora(), () -> {
                        if (casterPlayer != null && !casterPlayer.isOnline()) {
                            taskHolder[0].cancel();
                            return;
                        }
                        inner.execute(ctx);
                        if (remaining.decrementAndGet() <= 0) {
                            taskHolder[0].cancel();
                        }
                    }, interval, interval);
        };
    }
}
