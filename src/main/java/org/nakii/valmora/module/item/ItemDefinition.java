package org.nakii.valmora.module.item;

import org.bukkit.Material;

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
    private final List<String> loreTemplate;
    private final int customModelData;
    private final Map<String, Double> stats;
    private final Map<String, AbilityDefinition> abilities;
    private final List<String> reforgePool;
    private final String set;

    private ItemDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.material = builder.material;
        this.rarity = builder.rarity;
        this.itemType = builder.itemType;
        this.lore = builder.lore;
        this.loreTemplate = builder.loreTemplate;
        this.customModelData = builder.customModelData;
        this.stats = builder.stats;
        this.abilities = builder.abilities;
        this.reforgePool = builder.reforgePool;
        this.set = builder.set;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getMaterial() { return material; }
    public Rarity getRarity() { return rarity; }
    public ItemType getItemType() { return itemType; }
    public List<String> getLore() { return lore; }
    public List<String> getLoreTemplate() { return loreTemplate; }
    public int getCustomModelData() { return customModelData; }
    public Map<String, Double> getStats() { return stats; }
    public Map<String, AbilityDefinition> getAbilities() { return abilities; }
    public List<String> getReforgePool() { return reforgePool; }
    public String getSet() { return set; }

    public static class Builder {
        private final String id;
        private String name;
        private Material material;
        private Rarity rarity = Rarity.COMMON;
        private ItemType itemType = ItemType.NONE;
        private List<String> lore = List.of();
        private List<String> loreTemplate = List.of();
        private int customModelData = 0;
        private Map<String, Double> stats = new HashMap<>();
        private Map<String, AbilityDefinition> abilities = new HashMap<>();
        private List<String> reforgePool = List.of();
        private String set = null;

        public Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder material(Material material) { this.material = material; return this; }
        public Builder rarity(Rarity rarity) { this.rarity = rarity; return this; }
        public Builder itemType(ItemType itemType) { this.itemType = itemType; return this; }
        public Builder lore(List<String> lore) { this.lore = lore; return this; }
        public Builder loreTemplate(List<String> loreTemplate) { this.loreTemplate = loreTemplate; return this; }
        public Builder customModelData(int cmd) { this.customModelData = cmd; return this; }
        public Builder stat(String statId, double value) { this.stats.put(statId.toLowerCase(), value); return this; }
        public Builder ability(String id, AbilityDefinition ability) { this.abilities.put(id, ability); return this; }
        public Builder reforgePool(List<String> pool) { this.reforgePool = pool; return this; }
        public Builder set(String set) { this.set = set; return this; }

        public ItemDefinition build() {
            return new ItemDefinition(this);
        }
    }
}
