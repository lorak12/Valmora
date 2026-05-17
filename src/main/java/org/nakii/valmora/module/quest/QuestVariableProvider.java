package org.nakii.valmora.module.quest;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.Optional;

/**
 * Variable namespace: quest
 *
 * Paths:
 *   quest.<id>.status
 *   quest.<id>.progress.<index>        (legacy index-based)
 *   quest.<id>.objective.<objId>.progress
 *   quest.<id>.objective.<objId>.required
 *   objective.<objId>.active
 */
public class QuestVariableProvider implements VariableProvider {

    @Override public String getNamespace() { return "quest"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length < 2) return null;
        Optional<Player> maybePlayer = context.getPlayerCaster()
                .filter(e -> e instanceof Player).map(e -> (Player) e);
        if (maybePlayer.isEmpty()) return null;

        ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(maybePlayer.get().getUniqueId());
        if (vp == null) return null;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return null;

        QuestManager qm = ValmoraAPI.getInstance().getQuestManager();
        if (qm == null) return null;

        // objective.<id>.active  — special sub-namespace
        if (path[0].equalsIgnoreCase("objective") && path.length >= 2) {
            String objId = path[1];
            if (path.length >= 3 && path[2].equalsIgnoreCase("active"))
                return qm.isObjectiveActive(profile, objId);
            return null;
        }

        String questId = path[0];
        String field = path[1].toLowerCase();

        if (field.equals("status")) return qm.getStatus(profile, questId);

        // quest.<id>.progress.<index>   (legacy)
        if (field.equals("progress") && path.length >= 3) {
            try { return qm.getProgress(profile, questId, Integer.parseInt(path[2])); }
            catch (NumberFormatException ignored) {}
        }

        // quest.<id>.objective.<objId>.progress  or  .required
        if (field.equals("objective") && path.length >= 4) {
            String objId = path[2];
            String subField = path[3].toLowerCase();
            if (subField.equals("progress")) return qm.getObjectiveProgress(profile, questId, objId);
            if (subField.equals("required")) {
                return qm.getRegistry().get(questId)
                        .map(def -> def.getObjectives().stream()
                                .filter(o -> objId.equals(o.getId()))
                                .findFirst()
                                .map(o -> (Object) o.getRequired())
                                .orElse(null))
                        .orElse(null);
            }
        }

        return null;
    }
}
