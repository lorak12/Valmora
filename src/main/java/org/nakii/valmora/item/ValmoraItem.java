package org.nakii.valmora.item;

import org.bukkit.Material;
import org.nakii.valmora.stat.Stat;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class ValmoraItemTemplate {

    private final String id;
    private final String displayName;
    private final Material material;
    private final Map<Stat, Double> stats = new HashMap<>();
    private final Rarity rarity;
    private final ItemType itemType;
    private final Timestamp timestamp;

    public ValmoraItemTemplate(String id, String displayName, Material material, Rarity rarity, ItemType itemType) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.rarity = rarity;
        this.itemType = itemType;
        this.timestamp = new Timestamp(System.currentTimeMillis());
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }

}
