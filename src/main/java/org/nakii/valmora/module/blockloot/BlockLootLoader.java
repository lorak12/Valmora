package org.nakii.valmora.module.blockloot;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlockLootLoader {

    private final Valmora plugin;
    private final BlockLootRegistry registry;

    public BlockLootLoader(Valmora plugin, BlockLootRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void load() {
        registry.clear();
        new YamlLoader<BlockLootConfig>(plugin, "block_loot", "Block Loot Overrides")
                .load(this::parse, cfg -> registry.register(cfg.material(), cfg));
    }

    /**
     * The YAML top-level key is an arbitrary id (subject to content-pack namespacing — see
     * {@code YamlLoader}/{@code org.nakii.valmora.module.pack.PackNamespacer}), deliberately
     * <b>not</b> the material name: a pack-owned file's id could be rewritten to e.g.
     * {@code "somepack:stone_override"}, and {@code Material.matchMaterial} on a namespaced string
     * would silently fail. The actual block type is the required {@code material:} field inside
     * the section instead.
     */
    private LoadResult<BlockLootConfig, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            String materialName = sec.getString("material");
            if (materialName == null) {
                return LoadResult.failure("[" + path + "] Block-loot entry '" + id + "' is missing the required 'material' key.");
            }
            Material material = Material.matchMaterial(materialName.toUpperCase());
            if (material == null) {
                String hint = org.nakii.valmora.infrastructure.config.read.ConfigReader.materialHint(materialName);
                return LoadResult.failure("[" + path + "] Block-loot entry '" + id + "' has an unknown material: " + materialName
                        + (hint != null ? " (" + hint + ")" : ""));
            }

            List<BlockLootDrop> drops = new ArrayList<>();
            int index = -1;
            for (Map<?, ?> dropMap : sec.getMapList("drops")) {
                index++;
                Object itemObj = dropMap.get("item");
                if (itemObj == null) {
                    org.nakii.valmora.infrastructure.config.diag.Diagnostics.warn("drops[" + index + "]: missing item: — drop skipped");
                    continue;
                }
                final int at = index;
                org.nakii.valmora.infrastructure.config.diag.LoadScope.current().ifPresent(sc -> sc.sub("drops").sub(String.valueOf(at)).sub("item").ref(org.nakii.valmora.infrastructure.config.refs.Kinds.ITEM_OR_MATERIAL, itemObj.toString()));
                drops.add(new BlockLootDrop(
                        itemObj.toString(),
                        intVal(dropMap, "min", 1),
                        intVal(dropMap, "max", 1),
                        doubleVal(dropMap, "chance", 1.0)
                ));
            }

            return LoadResult.success(new BlockLootConfig(material, drops));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing block-loot entry '" + id + "': " + e.getMessage());
        }
    }

    private int intVal(Map<?, ?> m, String key, int def) { Object v = m.get(key); return v instanceof Number n ? n.intValue() : def; }
    private double doubleVal(Map<?, ?> m, String key, double def) { Object v = m.get(key); return v instanceof Number n ? n.doubleValue() : def; }
}
