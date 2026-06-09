package org.nakii.valmora.module.quest.objective;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.quest.ObjectiveHandler;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.QuestObjectiveTypes;

public class DelayObjectiveHandler implements ObjectiveHandler {

    private final Valmora plugin;

    public DelayObjectiveHandler(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTypeId() {
        return QuestObjectiveTypes.DELAY;
    }

    @Override
    public void onQuestStart(Player player, QuestObjective objective, QuestManager questManager) {
        if (objective.getDelayTicks() <= 0) return;
        String objectiveId = objective.getId();
        if (objectiveId == null) return;

        if (objective.getIntervalTicks() > 0) {
            int interval = objective.getIntervalTicks();
            new BukkitRunnable() {
                long remaining = objective.getDelayTicks();
                @Override
                public void run() {
                    remaining -= interval;
                    questManager.trigger(player, QuestObjectiveTypes.DELAY, objectiveId, 1);
                    if (remaining <= 0) cancel();
                }
            }.runTaskTimer(plugin, interval, interval);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                questManager.trigger(player, QuestObjectiveTypes.DELAY, objectiveId, objective.getRequired()),
                objective.getDelayTicks());
        }
    }
}
