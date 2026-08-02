package org.nakii.valmora.module.progression;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.stat.StatManager;

/**
 * Aggregates stat bonuses granted by leveled progression-tree nodes (e.g. Geomancy's
 * Mining Speed/Fortune/Spread branches) into a player's {@link StatManager}. Purely a
 * function of current node level — no bookkeeping needed on reset.
 */
public final class ProgressionStatService {

    private ProgressionStatService() {}

    public static void applyTo(Player player, StatManager statManager) {
        ProgressionManager manager = ValmoraAPI.getInstance().getProgressionManager();
        if (manager == null) return;

        for (ProgressionTreeDefinition tree : manager.getRegistry().values()) {
            for (ProgressionNode node : tree.getNodes().values()) {
                ProgressionNode.StatBonus bonus = node.getStatBonus();
                if (bonus == null) continue;

                int level = manager.getNodeLevel(player.getUniqueId(), tree.getId(), node.getId());
                if (level <= 0) continue;

                statManager.addModifier(bonus.stat(), bonus.perLevel() * level);
            }
        }
    }
}
