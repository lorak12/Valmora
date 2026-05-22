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
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return ctx -> {};

        long interval;
        int times;
        try {
            interval = Long.parseLong(args[0]);
            times = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            return ctx -> {};
        }

        if (interval <= 0 || times <= 0) return ctx -> {};

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        CompiledEvent inner = module.getEventParser().parse(sb.toString());

        return ctx -> {
            AtomicInteger remaining = new AtomicInteger(times);
            BukkitTask[] taskHolder = new BukkitTask[1];
            taskHolder[0] = module.getValmora().getServer().getScheduler()
                    .runTaskTimer(module.getValmora(), () -> {
                        inner.execute(ctx);
                        if (remaining.decrementAndGet() <= 0) {
                            taskHolder[0].cancel();
                        }
                    }, interval, interval);
        };
    }
}
