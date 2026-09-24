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
            if (seaCreatureMobId != null) {
                org.nakii.valmora.infrastructure.config.diag.LoadScope.current().ifPresent(sc -> sc.sub("sea-creature-mob").ref(org.nakii.valmora.infrastructure.config.refs.Kinds.MOB, seaCreatureMobId));
            }
            List<Map<?, ?>> entryList = sec.getMapList("entries");
            if (entryList.isEmpty()) org.nakii.valmora.infrastructure.config.diag.Diagnostics.warn("entries: no loot entries — this table never yields anything");
            int index = -1;
            for (Map<?, ?> m : entryList) {
                index++;
                String item = m.containsKey("item") ? m.get("item").toString() : "COD";
                final int at = index;
                org.nakii.valmora.infrastructure.config.diag.LoadScope.current().ifPresent(sc -> sc.sub("entries").sub(String.valueOf(at)).sub("item").ref(org.nakii.valmora.infrastructure.config.refs.Kinds.ITEM_OR_MATERIAL, item));
                // HC-200: fishing.loot.default-weight — used when an entry omits its own weight.
                int defaultWeight = plugin.getConfig().getInt("fishing.loot.default-weight", 10);
                int weight = m.get("weight") instanceof Number w ? w.intValue() : defaultWeight;
                int min = m.get("min") instanceof Number mn ? mn.intValue() : 1;
                int max = m.get("max") instanceof Number mx ? mx.intValue() : 1;
                if (max < min) {
                    org.nakii.valmora.infrastructure.config.diag.Diagnostics.warn("entries[" + index + "]: max " + max + " is below min " + min + " — using " + min);
                    max = min;
                }
                entries.add(new FishingLootEntry(item, weight, min, max));
            }
            return LoadResult.success(new FishingLootTable(id, entries, seaCreatureChance, seaCreatureMobId));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing fishing table '" + id + "': " + e.getMessage());
        }
    }
}
