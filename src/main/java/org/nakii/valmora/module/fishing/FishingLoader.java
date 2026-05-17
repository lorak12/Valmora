package org.nakii.valmora.module.fishing;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FishingLoader {

    private final Valmora plugin;
    private final Registry<FishingLootTable> registry;

    public FishingLoader(Valmora plugin, Registry<FishingLootTable> registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void load() {
        registry.clear();
        new YamlLoader<FishingLootTable>(plugin, "fishing", "Fishing Tables")
                .load(this::parse, table -> registry.register(table.getId(), table));
    }

    private LoadResult<FishingLootTable, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            double seaCreatureChance = sec.getDouble("sea-creature-chance", 0.0);
            String seaCreatureMobId = sec.getString("sea-creature-mob", null);

            List<FishingLootEntry> entries = new ArrayList<>();
            List<Map<?, ?>> entryList = sec.getMapList("entries");
            for (Map<?, ?> m : entryList) {
                String item = m.containsKey("item") ? m.get("item").toString() : "COD";
                int weight = m.containsKey("weight") ? ((Number) m.get("weight")).intValue() : 10;
                int min = m.containsKey("min") ? ((Number) m.get("min")).intValue() : 1;
                int max = m.containsKey("max") ? ((Number) m.get("max")).intValue() : 1;
                entries.add(new FishingLootEntry(item, weight, min, max));
            }
            return LoadResult.success(new FishingLootTable(id, entries, seaCreatureChance, seaCreatureMobId));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing fishing table '" + id + "': " + e.getMessage());
        }
    }
}
