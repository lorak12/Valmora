package org.nakii.valmora.module.progression;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Resolves {@code $progression.<tree>.<node>.<field>$} and {@code $progression.<tree>.tier...$}
 * variables for any registered progression tree (Geomancy and any future trees).
 */
public class ProgressionVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "progression";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length < 2) return null;
        Player player = context.getPlayerCaster().orElse(null);
        if (player == null) return null;

        ProgressionManager manager = ValmoraAPI.getInstance().getProgressionManager();
        if (manager == null) return null;

        String treeId = path[0].toLowerCase();

        if (path.length == 2 && path[1].equalsIgnoreCase("tier")) {
            return manager.getUnlockedTier(player.getUniqueId(), treeId);
        }

        if (path.length == 3 && path[1].equalsIgnoreCase("tier") && path[2].equalsIgnoreCase("next")) {
            int next = manager.getUnlockedTier(player.getUniqueId(), treeId) + 1;
            return manager.getRegistry().getTree(treeId)
                    .flatMap(t -> t.getTier(next))
                    .map(ProgressionTier::getUnlockCost)
                    .orElse(0);
        }

        if (path.length == 4 && path[1].equalsIgnoreCase("tier") && path[2].equalsIgnoreCase("next")
                && path[3].equalsIgnoreCase("unlock_cost")) {
            int next = manager.getUnlockedTier(player.getUniqueId(), treeId) + 1;
            return manager.getRegistry().getTree(treeId)
                    .flatMap(t -> t.getTier(next))
                    .map(ProgressionTier::getUnlockCost)
                    .orElse(0);
        }

        if (path.length < 3) return null;
        String nodeId = path[1].toLowerCase();
        String field = path[2].toLowerCase();

        return switch (field) {
            case "level" -> manager.getNodeLevel(player.getUniqueId(), treeId, nodeId);
            case "max_level" -> manager.getRegistry().getTree(treeId)
                    .flatMap(t -> t.getNode(nodeId))
                    .map(n -> n.getMaxLevel())
                    .orElse(0);
            case "next_cost" -> manager.getNodeCost(treeId, nodeId,
                    manager.getNodeLevel(player.getUniqueId(), treeId, nodeId));
            case "unlocked" -> manager.isNodeUnlocked(player.getUniqueId(), treeId, nodeId);
            default -> null;
        };
    }
}
