package org.nakii.valmora.module.item.set;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a single set-bonus section. Expected schema:
 *
 * <pre>
 * young_dragon:
 *   set-id: "young_dragon"      # optional; defaults to the section key
 *   name: "Young Blood"
 *   bonuses:
 *     - pieces-required: 4
 *       stats:
 *         SPEED: 70
 * </pre>
 */
public final class SetBonusParser {

    private SetBonusParser() {}

    public static LoadResult<SetBonusDefinition, String> parse(String id, ConfigurationSection section, String file) {
        String setId = section.getString("set-id", id);
        String name = section.getString("name", setId);

        List<SetBonusDefinition.Tier> tiers = new ArrayList<>();
        StatRegistry statRegistry = ValmoraAPI.getInstance().getStatRegistry();

        List<Map<?, ?>> bonuses = section.getMapList("bonuses");
        for (Map<?, ?> bonus : bonuses) {
            Object piecesObj = bonus.get("pieces-required");
            int pieces = piecesObj instanceof Number n ? n.intValue() : 1;

            Map<String, Double> stats = new HashMap<>();
            if (bonus.get("stats") instanceof Map<?, ?> statsMap) {
                for (Map.Entry<?, ?> e : statsMap.entrySet()) {
                    String statKey = String.valueOf(e.getKey());
                    if (!statRegistry.contains(statKey)) {
                        return LoadResult.failure("[" + file + "] Set '" + setId + "' references unknown stat '" + statKey + "'.");
                    }
                    if (e.getValue() instanceof Number num) {
                        stats.put(statKey.toLowerCase(), num.doubleValue());
                    }
                }
            }
            tiers.add(new SetBonusDefinition.Tier(pieces, stats));
        }

        return LoadResult.success(new SetBonusDefinition(setId, name, tiers));
    }
}
