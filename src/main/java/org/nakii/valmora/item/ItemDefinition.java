package org.nakii.valmora.item;

import org.bukkit.Material;
import org.nakii.valmora.stat.Stat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemDefinition {
    private final String id;
    private final String name;
    private final Material material;
    private final Rarity rarity;
    private final ItemType itemType;
    private final List<String> lore;
    private final Map<Stat, Double> stats;

    private ItemDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.material = builder.material;
        this.rarity = builder.rarity;
        this.itemType = builder.itemType;
        this.lore = builder.lore;
        this.stats = builder.stats;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getMaterial() { return material; }
    public Rarity getRarity() { return rarity; }
    public ItemType getItemType() { return itemType; }
    public List<String> getLore() { return lore; }
    public Map<Stat, Double> getStats() { return stats; }

    public static class Builder {
        private final String id;
        private String name;
        private Material material;
        private Rarity rarity = Rarity.COMMON;
        private ItemType itemType = ItemType.NONE;
        private List<String> lore = List.of();
        private Map<Stat, Double> stats = new HashMap<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder material(Material material) { this.material = material; return this; }
        public Builder rarity(Rarity rarity) { this.rarity = rarity; return this; }
        public Builder itemType(ItemType itemType) { this.itemType = itemType; return this; }
        public Builder lore(List<String> lore) { this.lore = lore; return this; }
        public Builder stat(Stat stat, double value) { this.stats.put(stat, value); return this; }

        public ItemDefinition build() {
            return new ItemDefinition(this);
        }
    }
}
