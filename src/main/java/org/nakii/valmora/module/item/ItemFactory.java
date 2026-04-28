package org.nakii.valmora.module.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;
import net.kyori.adventure.text.Component;

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
            // Set the ID first so other methods can find it
            meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, definition.getId());
            
            if (definition.getItemType() != null) {
                 meta.getPersistentDataContainer().set(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING, definition.getItemType().name());
            }

            // Set custom properties
            Rarity rarity = definition.getRarity() != null ? definition.getRarity() : Rarity.COMMON;
            meta.getPersistentDataContainer().set(Keys.RARITY_KEY, PersistentDataType.STRING, rarity.name());

            // Add all stats to the stats map
            plugin.getStatModule().saveStats(meta, definition.getStats());

            item.setItemMeta(meta);
            
            // Now update the lore properly
            updateLore(item);
        }
        return item;
    }

    public void updateLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        updateLore(item, meta);
        item.setItemMeta(meta);
    }

    public void updateLore(ItemStack item, ItemMeta meta) {
        String itemId = meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return;

        plugin.getItemManager().getItemRegistry().getItem(itemId).ifPresent(definition -> {
            Rarity rarity = definition.getRarity() != null ? definition.getRarity() : Rarity.COMMON;
            String rarityColor = rarity.getColor();

            // Set Display Name with Rarity Color
            if (definition.getName() != null) {
                meta.displayName(Formatter.format(rarityColor + definition.getName()));
            }

            // Assemble Lore
            List<Component> finalLore = new ArrayList<>();
            
            // 1. Base Lore from Config
            if (definition.getLore() != null && !definition.getLore().isEmpty()) {
                finalLore.addAll(Formatter.formatList(definition.getLore()));
            }

            // 2. Stats Section
            Map<Stat, Double> stats = plugin.getStatModule().loadStats(meta);
            if (!stats.isEmpty()) {
                if (!finalLore.isEmpty()) finalLore.add(Component.empty()); // Spacer
                for (Map.Entry<Stat, Double> entry : stats.entrySet()) {
                    finalLore.add(Formatter.format("<gray> ◈ " + entry.getKey().format(entry.getValue())));
                }
            }

            // 3. Enchantments Section
            Map<String, Integer> enchants = org.nakii.valmora.module.enchant.EnchantmentHelper.loadEnchantMap(meta.getPersistentDataContainer());
            if (!enchants.isEmpty()) {
                if (!finalLore.isEmpty()) finalLore.add(Component.empty()); // Spacer
                finalLore.addAll(org.nakii.valmora.module.enchant.EnchantmentHelper.formatEnchants(enchants));
            }

            if (definition.getAbilities() != null && !definition.getAbilities().isEmpty()) {
                finalLore.add(Component.empty()); // Spacer below stats/enchants
                
                for (AbilityDefinition ability : definition.getAbilities().values()) {
                    String triggerText = ability.getTrigger().name().replace("_", " ");
                    finalLore.add(Formatter.format("<gold>Ability: " + ability.getName() + " <yellow><bold>" + triggerText));
                    if (!ability.getDescription().isEmpty()) {
                        finalLore.addAll(Formatter.formatList(ability.getDescription()));
                    }
                    if (ability.getManaCost() > 0) {
                        finalLore.add(Formatter.format("<dark_gray>Mana Cost: <aqua>" + (int) ability.getManaCost()));
                    }
                    if (ability.getCooldown() > 0) {
                        finalLore.add(Formatter.format("<dark_gray>Cooldown: <green>" + ability.getCooldown() + "s"));
                    }
                    finalLore.add(Component.empty());
                }
            }

            // 4. Rarity Tag
            finalLore.add(Formatter.format(rarityColor + "<bold>" + rarity.getName().toUpperCase()));

            meta.lore(finalLore);
        });
    }
}
