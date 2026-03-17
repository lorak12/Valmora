package org.nakii.valmora.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Keys;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.stat.Stat;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemFactory {

    private final Valmora plugin;

    public ItemFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(ItemDefinition definition) {
        ItemStack item = new ItemStack(definition.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Rarity rarity = definition.getRarity() != null ? definition.getRarity() : Rarity.COMMON;
            String rarityColor = rarity.getColor();

            // Set Display Name with Rarity Color
            if (definition.getName() != null) {
                meta.displayName(Formatter.format(rarityColor + definition.getName()));
            }

            // Assemble Lore
            List<String> loreLines = new ArrayList<>();
            
            // 1. Base Lore from Config
            if (definition.getLore() != null && !definition.getLore().isEmpty()) {
                loreLines.addAll(definition.getLore());
            }

            // 2. Stats Section
            if (!definition.getStats().isEmpty()) {
                if (!loreLines.isEmpty()) loreLines.add(""); // Spacer
                for (Map.Entry<Stat, Double> entry : definition.getStats().entrySet()) {
                    // Cool format for stats
                    loreLines.add("<gray> ◈ " + entry.getKey().format(entry.getValue()));
                }
            }

            // 3. Rarity Tag at the end
            loreLines.add("");
            loreLines.add(rarityColor + "<bold>" + rarity.getName().toUpperCase());

            meta.lore(Formatter.formatList(loreLines));

            // Set the ID
            meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, definition.getId());

            // Set custom properties
            meta.getPersistentDataContainer().set(Keys.RARITY_KEY, PersistentDataType.STRING, rarity.name());
            
            if (definition.getItemType() != null) {
                 meta.getPersistentDataContainer().set(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING, definition.getItemType().name());
            }

            // Add all stats to the stats map
            plugin.getStatStorage().saveStats(meta, definition.getStats());

            item.setItemMeta(meta);
        }
        return item;
    }
}
