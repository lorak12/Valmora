package org.nakii.valmora.module.script;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every task scheduled by a script's {@code delay:<ticks>} option.
 *
 * <p>These used to be fire-and-forget {@code runTaskLater}s: after a reload they still ran
 * against the discarded module state they captured, and they ran even after their player had
 * logged out. Now a player's pending delayed events are cancelled when they quit, everything is
 * cancelled when the script engine reloads, and a task whose player went offline doesn't run.
 */
public class DelayedEventTracker implements Listener {

    private static final UUID NO_PLAYER = new UUID(0, 0);

    private final Valmora plugin;
    private final Map<UUID, Set<BukkitTask>> tasks = new ConcurrentHashMap<>();

    public DelayedEventTracker(Valmora plugin) {
        this.plugin = plugin;
    }

    public void schedule(ExecutionContext context, Runnable action, long delayTicks) {
        UUID owner = context.getCaster() instanceof Player player ? player.getUniqueId() : NO_PLAYER;
        Set<BukkitTask> ownerTasks = tasks.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet());
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ownerTasks.remove(holder[0]);
            if (owner != NO_PLAYER && Bukkit.getPlayer(owner) == null) return;
            action.run();
        }, delayTicks);
        ownerTasks.add(holder[0]);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Set<BukkitTask> ownerTasks = tasks.remove(event.getPlayer().getUniqueId());
        if (ownerTasks != null) ownerTasks.forEach(BukkitTask::cancel);
    }

    public void cancelAll() {
        tasks.values().forEach(set -> set.forEach(BukkitTask::cancel));
        tasks.clear();
    }
}
