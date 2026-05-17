package org.nakii.valmora.module.script.condition;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.quest.QuestManager;

public record QuestStatusCondition(String questId, String requiredStatus) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        return context.getPlayerCaster()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .map(p -> {
                    ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(p.getUniqueId());
                    if (vp == null || vp.getActiveProfile() == null) return false;
                    QuestManager qm = ValmoraAPI.getInstance().getQuestManager();
                    if (qm == null) return false;
                    return requiredStatus.equalsIgnoreCase(qm.getStatus(vp.getActiveProfile(), questId));
                })
                .orElse(false);
    }
}
