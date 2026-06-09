package org.nakii.valmora.module.quest.objective;

import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.quest.ObjectiveHandler;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.QuestObjectiveTypes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Handles timer objectives by scheduling a repeating 1-second tick per player+objective.
 *
 * Target format: any string (conventionally the objective id or "timer")
 * Required: target seconds to wait
 *
 * The task calls trigger() every second. trigger() increments the counter and
 * handles completion when it reaches required.
 */
public class TimerObjectiveHandler implements ObjectiveHandler {

    private final Valmora plugin;
    private final QuestManager questManager;
    /** Key = "<playerUuid>:<objectiveId>" */
    private final Map<String, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    public TimerObjectiveHandler(Valmora plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @Override
    public String getTypeId() { return QuestObjectiveTypes.TIMER; }

    @Override
    public void onQuestStart(Player player, QuestObjective objective, QuestManager qm) {
        if (objective.getId() == null) return;
        String taskKey = player.getUniqueId() + ":" + objective.getId();
        if (activeTasks.containsKey(taskKey)) return;

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) return;
            qm.trigger(player, QuestObjectiveTypes.TIMER, objective.getId(), 1);
        }, 20L, 20L);

        activeTasks.put(taskKey, task);
    }

    /** Cancels all running timer tasks (call from QuestModule.onDisable). */
    public void cancelAll() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }
}
