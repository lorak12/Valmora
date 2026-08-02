package org.nakii.valmora.module.progression;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProgressionLoader {

    private final Valmora plugin;
    private final ProgressionRegistry registry;

    public ProgressionLoader(Valmora plugin, ProgressionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void load() {
        registry.clear();
        new YamlLoader<ProgressionTreeDefinition>(plugin, "progression", "Progression Trees")
                .load(this::parse, registry::registerTree);
    }

    private LoadResult<ProgressionTreeDefinition, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            String name = sec.getString("name", id);
            String description = sec.getString("description", "");
            String levelCurrency = sec.getString("level-currency");
            String tierCurrency = sec.getString("tier-currency");
            if (levelCurrency == null || tierCurrency == null) {
                return LoadResult.failure("[" + path + "] Tree '" + id + "' missing level-currency/tier-currency.");
            }

            List<ProgressionTier> tiers = new ArrayList<>();
            ConfigurationSection tiersSec = sec.getConfigurationSection("tiers");
            if (tiersSec != null) {
                for (String tierKey : tiersSec.getKeys(false)) {
                    ConfigurationSection tierSec = tiersSec.getConfigurationSection(tierKey);
                    if (tierSec == null) continue;
                    int index;
                    try { index = Integer.parseInt(tierKey); }
                    catch (NumberFormatException e) { continue; }
                    String tierName = tierSec.getString("name", "Tier " + index);
                    int unlockCost = tierSec.getInt("unlock-cost", 0);
                    List<String> nodeIds = tierSec.getStringList("nodes");
                    tiers.add(new ProgressionTier(index, tierName, unlockCost, nodeIds));
                }
            }

            Map<String, ProgressionNode> nodes = new HashMap<>();
            ConfigurationSection nodesSec = sec.getConfigurationSection("nodes");
            if (nodesSec != null) {
                for (String nodeKey : nodesSec.getKeys(false)) {
                    ConfigurationSection nodeSec = nodesSec.getConfigurationSection(nodeKey);
                    if (nodeSec == null) continue;

                    String nodeName = nodeSec.getString("name", nodeKey);
                    String nodeDesc = nodeSec.getString("description", "");
                    Material icon = Material.matchMaterial(nodeSec.getString("icon", "BOOK"));
                    if (icon == null) icon = Material.BOOK;
                    int tierIndex = nodeSec.getInt("tier", 0);
                    int maxLevel = nodeSec.getInt("max-level", 1);
                    String costCurve = nodeSec.getString("cost-curve", "1");
                    List<String> prereqs = nodeSec.getStringList("prerequisites");

                    ProgressionNode.StatBonus statBonus = null;
                    ConfigurationSection statBonusSec = nodeSec.getConfigurationSection("stat-bonus");
                    if (statBonusSec != null) {
                        statBonus = new ProgressionNode.StatBonus(
                                statBonusSec.getString("stat", "").toLowerCase(),
                                statBonusSec.getDouble("per-level", 0.0));
                    }

                    ProgressionNode.DailyBonus dailyBonus = null;
                    ConfigurationSection dailyBonusSec = nodeSec.getConfigurationSection("daily-bonus");
                    if (dailyBonusSec != null) {
                        dailyBonus = new ProgressionNode.DailyBonus(
                                dailyBonusSec.getString("category", "").toLowerCase(),
                                dailyBonusSec.getDouble("per-level", 0.0));
                    }

                    nodes.put(nodeKey.toLowerCase(), new ProgressionNode(
                            nodeKey, nodeName, nodeDesc, icon, tierIndex, maxLevel, costCurve,
                            prereqs, statBonus, dailyBonus));
                }
            }

            return LoadResult.success(new ProgressionTreeDefinition(
                    id, name, description, levelCurrency.toLowerCase(), tierCurrency.toLowerCase(), tiers, nodes));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing progression tree '" + id + "': " + e.getMessage());
        }
    }
}
