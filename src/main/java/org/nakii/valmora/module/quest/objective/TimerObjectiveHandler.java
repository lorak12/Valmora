package org.nakii.valmora.module.quest.objective;

import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.quest.ObjectiveHandler;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.QuestObjectiveTypes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Handles timer objectives by scheduling a repeating 1-second tick per player+objective.
 *
 * Target format: any string (conventionally the objective id or "timer")
 * Required: target seconds to wait
 *
 * The task calls trigger() every second. trigger() increments the counter and
 * handles completion when it reaches required. The counter is the objective's saved progress,
 * so seconds already counted survive a relog/reload/restart: {@link #onResume} simply starts
 * ticking again from there. Only online time counts.
 */
public class TimerObjectiveHandler implements ObjectiveHandler {

    private final Valmora plugin;
    /** Key = "<playerUuid>:<objectiveId>" */
    private final Map<String, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    public TimerObjectiveHandler(Valmora plugin, QuestManager questManager) {
        this.plugin = plugin;
    }

    @Override
    public String getTypeId() { return QuestObjectiveTypes.TIMER; }

    @Override
    public void onQuestStart(Player player, QuestObjective objective, QuestManager qm) {
        startTicking(player.getUniqueId(), objective, qm);
    }

    @Override
    public void onResume(Player player, QuestObjective objective, QuestManager qm, int progress, long startedAtMillis) {
        startTicking(player.getUniqueId(), objective, qm);
    }

    private void startTicking(UUID playerId, QuestObjective objective, QuestManager qm) {
        if (objective.getId() == null) return;
        String taskKey = playerId + ":" + objective.getId();
        if (activeTasks.containsKey(taskKey)) return;

        // HC-260: poll interval configurable for CPU tuning on servers with many concurrent timer
        // objectives; the progress increment scales with it so "1 unit = 1 real second" still
        // holds regardless of poll rate (e.g. a 40-tick interval reports 2 units per fire).
        // Looks the player up each tick rather than capturing the Player object, which goes stale
        // after a relog (and used to keep a dead task blocking a new one for the new session).
        long intervalTicks = plugin.getConfig().getLong("quests.poll.timer-interval-ticks", 20L);
        int amountPerTick = (int) Math.max(1, Math.round(intervalTicks / 20.0));
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) return;
            qm.trigger(online, QuestObjectiveTypes.TIMER, objective.getId(), amountPerTick);
        }, intervalTicks, intervalTicks);

        activeTasks.put(taskKey, task);
    }

    @Override
    public void onPlayerQuit(Player player) {
        String prefix = player.getUniqueId() + ":";
        activeTasks.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(prefix)) return false;
            e.getValue().cancel();
            return true;
        });
    }

    /** Cancels all running timer tasks (call from QuestModule.onDisable). */
    @Override
    public void cancelAll() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }
}
