package org.nakii.valmora.module.quest.objective;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.quest.ObjectiveHandler;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.QuestObjectiveTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DELAY objectives: complete a fixed time after the quest starts (one-shot), or tick once per
 * {@code interval} until the delay has elapsed.
 *
 * <p>Wall-clock based: the quest engine records when each objective started, so after a relog,
 * reload or restart {@link #onResume} schedules only the time still remaining, or completes
 * immediately if it already elapsed. The scheduled tasks used to be untracked, so they died on
 * restart (quest stuck forever) and survived reloads pointing at a discarded QuestManager.
 */
public class DelayObjectiveHandler implements ObjectiveHandler {

    private final Valmora plugin;
    private final Map<UUID, List<BukkitTask>> tasks = new ConcurrentHashMap<>();

    public DelayObjectiveHandler(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTypeId() {
        return QuestObjectiveTypes.DELAY;
    }

    @Override
    public void onQuestStart(Player player, QuestObjective objective, QuestManager questManager) {
        schedule(player.getUniqueId(), objective, questManager, 0, objective.getDelayTicks());
    }

    @Override
    public void onResume(Player player, QuestObjective objective, QuestManager questManager, int progress, long startedAtMillis) {
        long remainingTicks = objective.getDelayTicks();
        if (startedAtMillis > 0) {
            long elapsedTicks = (System.currentTimeMillis() - startedAtMillis) / 50L;
            remainingTicks = Math.max(0, objective.getDelayTicks() - elapsedTicks);
        }
        schedule(player.getUniqueId(), objective, questManager, progress, remainingTicks);
    }

    private void schedule(UUID playerId, QuestObjective objective, QuestManager questManager, int progress, long remainingTicks) {
        if (objective.getDelayTicks() <= 0) return;
        String objectiveId = objective.getId();
        if (objectiveId == null) return;

        BukkitTask task;
        if (objective.getIntervalTicks() > 0) {
            // HC-261: floors a malformed `interval: 1` (or similarly tiny) objective config so it
            // can't schedule a near-per-tick BukkitRunnable for the whole delay duration.
            int minInterval = plugin.getConfig().getInt("quests.limits.delay-min-interval-ticks", 20);
            int interval = Math.max(minInterval, objective.getIntervalTicks());
            // One tick of progress per interval; resume with however many are still owed.
            int owed = Math.max(0, objective.getRequired() - progress);
            if (owed == 0) return;
            if (remainingTicks <= 0) {
                trigger(playerId, questManager, objectiveId, owed);
                return;
            }
            task = new org.bukkit.scheduler.BukkitRunnable() {
                int left = owed;
                @Override
                public void run() {
                    left--;
                    trigger(playerId, questManager, objectiveId, 1);
                    if (left <= 0) cancel();
                }
            }.runTaskTimer(plugin, interval, interval);
        } else {
            task = plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> trigger(playerId, questManager, objectiveId, objective.getRequired()),
                    Math.max(1L, remainingTicks));
        }
        tasks.computeIfAbsent(playerId, k -> new ArrayList<>()).add(task);
    }

    private void trigger(UUID playerId, QuestManager questManager, String objectiveId, int amount) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) questManager.trigger(online, QuestObjectiveTypes.DELAY, objectiveId, amount);
    }

    @Override
    public void onPlayerQuit(Player player) {
        List<BukkitTask> list = tasks.remove(player.getUniqueId());
        if (list != null) list.forEach(BukkitTask::cancel);
    }

    @Override
    public void cancelAll() {
        tasks.values().forEach(list -> list.forEach(BukkitTask::cancel));
        tasks.clear();
    }
}
