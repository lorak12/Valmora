package org.nakii.valmora.module.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.stat.StatDefinition;
import org.nakii.valmora.module.stat.StatRegistry;
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
        
        // Try to get definition
        var definitionOpt = itemId != null ? plugin.getItemManager().getItemRegistry().getItem(itemId) : java.util.Optional.<ItemDefinition>empty();

        Rarity rarity;
        String rarityColor;
        String name;
        List<String> baseLore = new ArrayList<>();

        if (definitionOpt.isPresent()) {
            ItemDefinition definition = definitionOpt.get();
            rarity = definition.getRarity() != null ? definition.getRarity() : Rarity.COMMON;
            rarityColor = rarity.getColor();
            name = definition.getName();
            if (definition.getLore() != null) baseLore.addAll(definition.getLore());
        } else {
            // Fallback for translated vanilla items
            String rarityName = meta.getPersistentDataContainer().get(Keys.RARITY_KEY, PersistentDataType.STRING);
            try {
                rarity = rarityName != null ? Rarity.valueOf(rarityName) : Rarity.COMMON;
            } catch (IllegalArgumentException e) {
                rarity = Rarity.COMMON;
            }
            rarityColor = rarity.getColor();
            // Use capitalized material name if it's a translated vanilla item
            name = (itemId != null && itemId.startsWith("vanilla_")) ? Formatter.capitalize(item.getType().name().replace("_", " ")) : null;
        }

        // Set Display Name
        if (name != null) {
            meta.displayName(Formatter.format(rarityColor + name));
        }

        // Assemble Lore
        List<Component> finalLore = new ArrayList<>();
        
        // 1. Base Lore
        if (!baseLore.isEmpty()) {
            finalLore.addAll(Formatter.formatList(baseLore));
        }

        // 2. Stats Section
        Map<String, Double> stats = plugin.getStatModule().loadStats(meta);
        if (!stats.isEmpty()) {
            if (!finalLore.isEmpty()) finalLore.add(Component.empty()); // Spacer
            StatRegistry statRegistry = plugin.getStatModule().getStatRegistry();
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                StatDefinition def = statRegistry.get(entry.getKey()).orElse(null);
                String formatted = def != null
                        ? "<gray> ◈ " + def.format(entry.getValue())
                        : "<gray> ◈ <white>" + entry.getKey() + ": +" + entry.getValue().intValue();
                finalLore.add(Formatter.format(formatted));
            }
        }

        // 3. Enchantments Section
        Map<String, Integer> enchants = org.nakii.valmora.module.enchant.EnchantmentHelper.loadEnchantMap(meta.getPersistentDataContainer());
        if (!enchants.isEmpty()) {
            if (!finalLore.isEmpty()) finalLore.add(Component.empty()); // Spacer
            finalLore.addAll(org.nakii.valmora.module.enchant.EnchantmentHelper.formatEnchants(enchants));
        }

        // 4. Abilities (Only if definition present)
        if (definitionOpt.isPresent()) {
            ItemDefinition definition = definitionOpt.get();
            if (definition.getAbilities() != null && !definition.getAbilities().isEmpty()) {
                finalLore.add(Component.empty()); // Spacer
                
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
        }

        // 5. Rarity Tag (e.g. EPIC SWORD)
        String typeName = meta.getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        String typeDisplay = (typeName != null && !typeName.equalsIgnoreCase("NONE")) ? " " + typeName.toUpperCase() : "";
        
        finalLore.add(Formatter.format(rarityColor + "<bold>" + rarity.getName().toUpperCase() + typeDisplay));

        meta.lore(finalLore);
    }
}
