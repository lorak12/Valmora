package org.nakii.valmora.module.quest.board;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;

public class QuestBoardLoader {

    private final Valmora plugin;
    private final QuestBoardRegistry registry;

    public QuestBoardLoader(Valmora plugin, QuestBoardRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void load() {
        registry.clear();
        new YamlLoader<QuestBoardDefinition>(plugin, "quest_boards", "Quest Boards")
                .load(this::parse, registry::registerBoard);
    }

    private LoadResult<QuestBoardDefinition, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            int slots = sec.getInt("slots", 2);
            var pool = sec.getStringList("pool");
            if (pool.isEmpty()) {
                return LoadResult.failure("[" + path + "] Quest board '" + id + "' has an empty pool.");
            }
            return LoadResult.success(new QuestBoardDefinition(id, slots, pool));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing quest board '" + id + "': " + e.getMessage());
        }
    }
}
