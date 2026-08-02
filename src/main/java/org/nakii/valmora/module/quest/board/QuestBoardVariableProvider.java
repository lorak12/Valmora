package org.nakii.valmora.module.quest.board;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Resolves {@code $questboard.<boardId>.slot.<n>.<field>$} — the quest currently occupying a
 * board slot, its display name, and its status. Used by quest-board GUIs to render each slot
 * without needing to know which pool quest is currently assigned ahead of time.
 */
public class QuestBoardVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "questboard";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length < 4) return null;
        if (!path[1].equalsIgnoreCase("slot")) return null;

        Player player = context.getPlayerCaster().orElse(null);
        if (player == null) return null;

        String boardId = path[0];
        String slot = path[2];
        String field = path[3].toLowerCase();

        ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return null;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return null;

        Object questIdObj = profile.getVariables().get("questboard." + boardId + ".slot." + slot);
        String questId = questIdObj != null ? String.valueOf(questIdObj) : null;

        QuestManager qm = ValmoraAPI.getInstance().getQuestManager();
        if (qm == null) return null;

        return switch (field) {
            case "quest_id" -> questId;
            case "name" -> questId != null
                    ? qm.getRegistry().get(questId).map(q -> q.getName()).orElse(questId)
                    : "";
            case "status" -> questId != null ? qm.getStatus(profile, questId) : "empty";
            default -> null;
        };
    }
}
