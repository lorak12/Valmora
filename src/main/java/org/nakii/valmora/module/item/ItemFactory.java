package org.nakii.valmora.module.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.item.AbilityDefinition;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

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
                    loreLines.add("<gray> ◈ " + entry.getKey().format(entry.getValue()));
                }
            }

            if (definition.getAbilities() != null && !definition.getAbilities().isEmpty()) {
                loreLines.add(""); // Spacer below stats
                
                for (AbilityDefinition ability : definition.getAbilities().values()) {
                    // Format trigger (e.g., RIGHT_CLICK -> RIGHT CLICK)
                    String triggerText = ability.getTrigger().name().replace("_", " ");
                    
                    // Header: Ability: Blood Hunter RIGHT CLICK
                    loreLines.add("<gold>Ability: " + ability.getName() + " <yellow><bold>" + triggerText);
                    
                    // Description lines
                    if (!ability.getDescription().isEmpty()) {
                        loreLines.addAll(ability.getDescription());
                    }
                    
                    // Mana & Cooldown
                    if (ability.getManaCost() > 0) {
                        loreLines.add("<dark_gray>Mana Cost: <aqua>" + (int) ability.getManaCost());
                    }
                    if (ability.getCooldown() > 0) {
                        loreLines.add("<dark_gray>Cooldown: <green>" + ability.getCooldown() + "s");
                    }
                    
                    loreLines.add(""); // Spacer between multiple abilities
                }
            }

            // 3. Rarity Tag at the end
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
            plugin.getStatModule().saveStats(meta, definition.getStats());

            item.setItemMeta(meta);
        }
        return item;
    }
}
