package org.nakii.valmora.module.blockloot;

import org.bukkit.Material;
import org.nakii.valmora.Valmora;

import java.util.EnumMap;
import java.util.Map;

/**
 * Material-keyed lookup for global block-loot overrides. A plain {@code EnumMap<Material, ...>}
 * rather than the generic string-keyed {@code Registry<T>} — the lookup key here is a
 * {@code Material} enum (same convention as {@code ZoneDefinition.getResourceBlocks()}), not an
 * arbitrary string id. The YAML id (see {@link BlockLootLoader}) only exists to survive
 * content-pack namespacing and plays no role in this registry.
 */
public class BlockLootRegistry {

    private final Valmora plugin;
    private final Map<Material, BlockLootConfig> configs = new EnumMap<>(Material.class);

    public BlockLootRegistry(Valmora plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a config, keyed by its material. Two different YAML ids can end up targeting the
     * same material now that the id is decoupled from it (see {@link BlockLootLoader}) — on a
     * collision, the first one registered wins and a warning is logged rather than silently
     * overwriting.
     */
    public void register(Material material, BlockLootConfig config) {
        BlockLootConfig existing = configs.putIfAbsent(material, config);
        if (existing != null) {
            plugin.getLogger().warning("[BlockLoot] Ignoring a duplicate block-loot config for "
                    + material + " — a config for this material is already registered.");
        }
    }

    public BlockLootConfig get(Material material) {
        return configs.get(material);
    }

    public void clear() {
        configs.clear();
    }

    public int size() {
        return configs.size();
    }
}
