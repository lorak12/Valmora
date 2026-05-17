package org.nakii.valmora.module.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuestLoader {

    private final Valmora plugin;
    private final Registry<QuestDefinition> registry;

    public QuestLoader(Valmora plugin, Registry<QuestDefinition> registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void load() {
        registry.clear();
        new YamlLoader<QuestDefinition>(plugin, "quests", "Quests")
                .load(this::parse, def -> registry.register(def.getId(), def));
    }

    private LoadResult<QuestDefinition, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            String name = sec.getString("name", id);
            List<QuestObjective> objectives = new ArrayList<>();
            for (Map<?, ?> m : sec.getMapList("objectives")) {
                String objId = m.containsKey("id") ? m.get("id").toString() : null;
                String typeStr = m.containsKey("type") ? m.get("type").toString().toUpperCase() : "KILL";
                QuestObjectiveType type;
                try { type = QuestObjectiveType.valueOf(typeStr); } catch (Exception e) { type = QuestObjectiveType.KILL; }
                String target = m.containsKey("target") ? m.get("target").toString() : "";
                int amount = m.containsKey("amount") ? ((Number) m.get("amount")).intValue() : 1;

                List<String> conditions = new ArrayList<>();
                List<String> actions = new ArrayList<>();
                if (m.get("conditions") instanceof List<?> cl) cl.forEach(o -> conditions.add(o.toString()));
                if (m.get("actions") instanceof List<?> al) al.forEach(o -> actions.add(o.toString()));
                boolean persistent = m.containsKey("persistent") && Boolean.parseBoolean(m.get("persistent").toString());
                boolean autoOnce = m.containsKey("auto-once") && Boolean.parseBoolean(m.get("auto-once").toString());
                int notifyInterval = 0;
                if (m.containsKey("notify")) {
                    try { notifyInterval = Integer.parseInt(m.get("notify").toString()); }
                    catch (NumberFormatException e) { notifyInterval = 1; }
                }

                objectives.add(new QuestObjective(objId, type, target, amount,
                        conditions, actions, persistent, autoOnce, notifyInterval));
            }
            return LoadResult.success(new QuestDefinition(
                    id, name, objectives,
                    sec.getStringList("rewards"),
                    sec.getStringList("on-start-actions")
            ));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing quest '" + id + "': " + e.getMessage());
        }
    }
}
