package org.nakii.valmora.module.enchant;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.item.ItemDefinition;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnchantmentHelper {

    public static void applyEnchantment(ItemStack item, String enchantId, int level) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Map<String, Integer> enchantMap = loadEnchantMap(pdc);
        enchantMap.put(enchantId.toLowerCase(), level);

        saveEnchantMap(pdc, enchantMap);
        applyGlowAndLore(item, meta, enchantMap);
        item.setItemMeta(meta);
    }

    public static Map<String, Integer> getEnchantments(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new HashMap<>();
        }
        return loadEnchantMap(item.getItemMeta().getPersistentDataContainer());
    }

    public static boolean hasValmoraEnchants(ItemStack item) {
        return item != null && item.hasItemMeta() && !loadEnchantMap(item.getItemMeta().getPersistentDataContainer()).isEmpty();
    }

    private static Map<String, Integer> loadEnchantMap(PersistentDataContainer pdc) {
        Map<String, Integer> result = new HashMap<>();

        if (pdc.has(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING)) {
            String serialized = pdc.get(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING);
            if (serialized != null && !serialized.isEmpty()) {
                String[] pairs = serialized.split(",");
                for (String pair : pairs) {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        try {
                            result.put(parts[0], Integer.parseInt(parts[1]));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void saveEnchantMap(PersistentDataContainer pdc, Map<String, Integer> enchantMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : enchantMap.entrySet()) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        pdc.set(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING, sb.toString());
    }

    public static void updateItemLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<String, Integer> enchantMap = loadEnchantMap(pdc);

        if (enchantMap.isEmpty()) {
            return;
        }

        applyGlowAndLore(item, meta, enchantMap);
        item.setItemMeta(meta);
    }

    private static void applyGlowAndLore(ItemStack item, ItemMeta meta, Map<String, Integer> enchantMap) {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> newLore = new ArrayList<>();

        String itemId = Keys.ITEM_ID_KEY != null ?
                meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING) : null;

        if (itemId != null) {
            ItemManager itemManager = ValmoraAPI.getInstance().getItemManager();
            itemManager.getItemRegistry().getItem(itemId).ifPresent(def -> {
                if (def.getLore() != null) {
                    newLore.addAll(def.getLore());
                }
            });
        }

        List<String> formattedEnchants = formatEnchants(enchantMap);
        if (!formattedEnchants.isEmpty()) {
            if (!newLore.isEmpty()) {
                newLore.add("");
            }
            newLore.addAll(formattedEnchants);
        }

        meta.setLore(newLore);
    }

    private static List<String> formatEnchants(Map<String, Integer> enchantMap) {
        List<String> lore = new ArrayList<>();
        List<String> sortedIds = new ArrayList<>(enchantMap.keySet());

        sortedIds.sort(String::compareToIgnoreCase);

        if (enchantMap.size() < 4) {
            for (String id : sortedIds) {
                int level = enchantMap.get(id);
                lore.add(Formatter.format("<blue>" + id + " " + level + "</blue>"));

                EnchantmentDefinition def = ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(id).orElse(null);
                if (def != null && def.getDescription() != null) {
                    for (String descLine : def.getDescription()) {
                        lore.add(Formatter.format("<gray>" + descLine + "</gray>"));
                    }
                }
            }
        } else {
            List<String> shortEnchants = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();

            for (String id : sortedIds) {
                int level = enchantMap.get(id);
                String enchantStr = id + " " + level;

                if (currentLine.length() + enchantStr.length() + 2 > 40) {
                    shortEnchants.add(Formatter.format("<blue>" + currentLine.toString().trim() + "</blue>"));
                    currentLine = new StringBuilder();
                }

                if (currentLine.length() > 0) {
                    currentLine.append(", ");
                }
                currentLine.append(enchantStr);
            }

            if (currentLine.length() > 0) {
                shortEnchants.add(Formatter.format("<blue>" + currentLine.toString().trim() + "</blue>"));
            }

            lore.addAll(shortEnchants);
        }

        return lore;
    }

    public static ItemStack createEnchantedBook(String enchantId, int level) {
        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            Map<String, Integer> enchantMap = new HashMap<>();
            enchantMap.put(enchantId.toLowerCase(), level);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            saveEnchantMap(pdc, enchantMap);

            applyGlowAndLore(book, meta, enchantMap);
            book.setItemMeta(meta);
        }

        return book;
    }
}