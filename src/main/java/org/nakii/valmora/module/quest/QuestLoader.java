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
        // A folder holding a quest.yml is a quest package, loaded by QuestPackageManager — parsing its
        // files here too would register their top-level keys (quests, events, conversations, ...)
        // as empty flat quests.
        new YamlLoader<QuestDefinition>(plugin, "quests", "Quests").kind(org.nakii.valmora.infrastructure.config.refs.Kinds.QUEST)
                .skipDirectories(dir -> new java.io.File(dir, "quest.yml").isFile())
                .load(this::parse, def -> registry.register(def.getId(), def));
    }

    private LoadResult<QuestDefinition, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            String name = sec.getString("name", id);
            List<QuestObjective> objectives = new ArrayList<>();
            org.nakii.valmora.infrastructure.config.read.ConfigReader.of(sec).knownKeys(
                    "name", "objectives", "reward-events", "cooldown-seconds");
            int index = -1;
            for (Map<?, ?> m : sec.getMapList("objectives")) {
                index++;
                String objId = m.containsKey("id") ? m.get("id").toString() : null;
                String type = m.containsKey("type") ? m.get("type").toString().toLowerCase() : "kill";
                String target = m.containsKey("target") ? m.get("target").toString() : "";
                int amount = 1;
                if (m.containsKey("amount")) {
                    if (m.get("amount") instanceof Number n) amount = n.intValue();
                    else org.nakii.valmora.infrastructure.config.diag.Diagnostics.warn("objectives[" + index
                            + "].amount: expected a number, got '" + m.get("amount") + "' — using 1");
                }

                List<String> conditions = new ArrayList<>();
                List<String> events = new ArrayList<>();
                if (m.get("conditions") instanceof List<?> cl) cl.forEach(o -> conditions.add(o.toString()));
                if (m.get("events") instanceof List<?> el) el.forEach(o -> events.add(o.toString()));
                boolean persistent = m.containsKey("persistent") && Boolean.parseBoolean(m.get("persistent").toString());
                boolean autoOnce = m.containsKey("auto-once") && Boolean.parseBoolean(m.get("auto-once").toString());
                int notifyInterval = 0;
                if (m.containsKey("notify")) {
                    try { notifyInterval = Integer.parseInt(m.get("notify").toString()); }
                    catch (NumberFormatException e) { notifyInterval = 1; }
                }

                objectives.add(new QuestObjective(objId, type, target, amount,
                        conditions, events, persistent, autoOnce, notifyInterval));
            }
            List<String> rewardEvents = sec.getStringList("reward-events");
            var script = plugin.getScriptModule();
            if (script != null && !rewardEvents.isEmpty()) {
                org.nakii.valmora.infrastructure.config.diag.ScriptCompile.at("reward-events", () -> script.compileCached(rewardEvents));
            }
            long cooldownSeconds = sec.getLong("cooldown-seconds", 0);
            return LoadResult.success(new QuestDefinition(id, name, objectives, rewardEvents, cooldownSeconds));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing quest '" + id + "': " + e.getMessage());
        }
    }
}
