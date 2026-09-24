package org.nakii.valmora.module.enchant;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.item.ItemType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnchantmentHelper {

    public static boolean canApplyEnchantment(ItemStack item, String enchantId) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        EnchantmentDefinition def = ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(enchantId).orElse(null);
        if (def == null) {
            return false;
        }
        // Resolves the item's ItemType via the canonical PDC-first/material-fallback resolver
        // (ItemType.fromItemStack) rather than duplicating that fallback logic locally, as this
        // method used to (fixed as part of the storage overhaul — see EnchantStateStore).
        return def.canApplyTo(ItemType.fromItemStack(item));
    }

    /**
     * Writes a complete enchantment map directly to the item, bypassing the item-type check.
     * Use this when the caller has already validated the enchant set (e.g. anvil merging).
     */
    public static void applyEnchantmentMap(ItemStack item, Map<String, Integer> enchantMap) {
        if (item == null || !item.hasItemMeta() || enchantMap.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        Map<String, EnchantStateStore.EnchantInstance> instances = mergeLevels(item, enchantMap);
        EnchantStateStore.save(meta, instances);
        applyGlowAndLore(item, meta, toLevelMap(instances));
        item.setItemMeta(meta);
    }

    /** Clamps every level to {@link EnchantmentDefinition#getAbsoluteMaxLevel()} — the anvil/admin
     *  path's historical behavior. Use {@link #applyEnchantment(ItemStack, String, int, boolean)}
     *  with {@code enforceEtableCap=true} for the enchanting-table path. */
    public static void applyEnchantment(ItemStack item, String enchantId, int level) {
        applyEnchantment(item, enchantId, level, false);
    }

    /**
     * Applies (or raises the level of) one enchant on {@code item}.
     *
     * @param enforceEtableCap when {@code true}, clamps to {@link EnchantmentDefinition#getEtableMaxLevel()}
     *                          instead of {@link EnchantmentDefinition#getAbsoluteMaxLevel()} — the
     *                          enchanting-table apply path must pass {@code true} (previously it
     *                          only clamped to the absolute cap, so a modified client/GUI wiring
     *                          calling the same script event with an out-of-range level could
     *                          silently exceed the etable cap; the etable GUI only ever *offered*
     *                          in-range levels, it never enforced the cap server-side).
     */
    public static void applyEnchantment(ItemStack item, String enchantId, int level, boolean enforceEtableCap) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        if (!canApplyEnchantment(item, enchantId)) {
            return;
        }

        EnchantmentDefinition def = ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(enchantId).orElse(null);
        if (def == null) return;

        ItemMeta meta = item.getItemMeta();
        Map<String, EnchantStateStore.EnchantInstance> instances = EnchantStateStore.load(meta);

        // Conflict enforcement — checked both directions since a shipped conflicts: list isn't
        // guaranteed to be declared symmetrically on both entries.
        for (String existingId : instances.keySet()) {
            if (existingId.equalsIgnoreCase(enchantId)) continue;
            if (def.conflictsWith(existingId)) return;
            EnchantmentDefinition existingDef = ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(existingId).orElse(null);
            if (existingDef != null && existingDef.conflictsWith(enchantId)) return;
        }

        int cap = enforceEtableCap ? def.getEtableMaxLevel() : def.getAbsoluteMaxLevel();
        int clampedLevel = Math.max(1, Math.min(level, cap));
        String key = enchantId.toLowerCase();
        EnchantStateStore.EnchantInstance existing = instances.get(key);
        if (existing != null) {
            existing.setLevel(clampedLevel);
        } else {
            instances.put(key, new EnchantStateStore.EnchantInstance(enchantId, clampedLevel, Map.of()));
        }

        EnchantStateStore.save(meta, instances);
        applyGlowAndLore(item, meta, toLevelMap(instances));
        item.setItemMeta(meta);
        DebugManager.log("enchants", "applied '" + enchantId + "' level=" + clampedLevel
                + " (requested=" + level + ", cap=" + cap + ", enforceEtableCap=" + enforceEtableCap + ") to " + item.getType());
    }

    public static Map<String, Integer> getEnchantments(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new HashMap<>();
        }
        return toLevelMap(EnchantStateStore.load(item));
    }

    public static int getEnchantLevel(ItemStack item, String enchantId) {
        return getEnchantments(item).getOrDefault(enchantId.toLowerCase(), 0);
    }

    public static void removeEnchantment(ItemStack item, String enchantId) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        Map<String, EnchantStateStore.EnchantInstance> instances = EnchantStateStore.load(meta);
        instances.remove(enchantId.toLowerCase());

        if (instances.isEmpty()) {
            meta.removeEnchant(Enchantment.UNBREAKING);
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        EnchantStateStore.save(meta, instances);

        applyGlowAndLore(item, meta, toLevelMap(instances));
        item.setItemMeta(meta);
        DebugManager.log("enchants", "removed '" + enchantId + "' from " + item.getType());
    }

    public static boolean hasValmoraEnchants(ItemStack item) {
        return item != null && item.hasItemMeta() && !EnchantStateStore.load(item).isEmpty();
    }

    /** Legacy id:level view over the structured store, kept for the many callers ({@code
     *  StatManager}, {@code DamageCalculator}, {@code ItemFactory}, the anvil handler, ...) that
     *  only need level lookups and never touch per-enchant state directly. */
    public static Map<String, Integer> loadEnchantMap(PersistentDataContainer pdc) {
        return toLevelMap(EnchantStateStore.load(pdc));
    }

    private static Map<String, Integer> toLevelMap(Map<String, EnchantStateStore.EnchantInstance> instances) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (EnchantStateStore.EnchantInstance instance : instances.values()) {
            result.put(instance.getId(), instance.getLevel());
        }
        return result;
    }

    private static Map<String, EnchantStateStore.EnchantInstance> mergeLevels(ItemStack item, Map<String, Integer> enchantMap) {
        Map<String, EnchantStateStore.EnchantInstance> instances = EnchantStateStore.load(item);
        for (Map.Entry<String, Integer> entry : enchantMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            EnchantStateStore.EnchantInstance existing = instances.get(key);
            if (existing != null) {
                existing.setLevel(entry.getValue());
            } else {
                instances.put(key, new EnchantStateStore.EnchantInstance(entry.getKey(), entry.getValue(), Map.of()));
            }
        }
        return instances;
    }

    private static String serializeLore(List<Component> lore) {
        StringBuilder sb = new StringBuilder();
        for (Component line : lore) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(MiniMessage.miniMessage().serialize(line));
        }
        return sb.toString();
    }

    private static List<Component> deserializeLore(String serialized) {
        List<Component> lore = new ArrayList<>();
        if (serialized == null || serialized.isEmpty()) return lore;
        for (String line : serialized.split("\n", -1)) {
            lore.add(MiniMessage.miniMessage().deserialize(line));
        }
        return lore;
    }

    public static void updateItemLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        Map<String, Integer> enchantMap = toLevelMap(EnchantStateStore.load(meta));

        if (enchantMap.isEmpty()) {
            return;
        }

        applyGlowAndLore(item, meta, enchantMap);
        item.setItemMeta(meta);
    }

    /**
     * Re-renders the enchant lore of an item WITHOUT a Valmora item id (e.g. an enchanted book or a
     * plain vanilla sword), from its saved base lore plus live enchant names/descriptions. Does
     * not call {@code setItemMeta}. Used by ItemRefresher after enchant YAML changes.
     */
    public static void rerenderGenericLore(ItemStack item, ItemMeta meta) {
        Map<String, Integer> enchantMap = toLevelMap(EnchantStateStore.load(meta));
        if (!enchantMap.isEmpty()) applyGlowAndLore(item, meta, enchantMap);
    }

    private static void applyGlowAndLore(ItemStack item, ItemMeta meta, Map<String, Integer> enchantMap) {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        String itemId = meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);

        if (itemId != null) {
            // For Valmora items, use the centralized ItemFactory to rebuild the lore correctly
            // This ensures stats, abilities, and rarity are preserved.
            ValmoraAPI.getInstance().getItemManager().getItemFactory().updateLore(item, meta);
            return;
        }

        // For generic (non-Valmora) items there is no ItemFactory to rebuild lore from scratch, so we
        // snapshot the item's lore the first time it's enchanted (before any enchant block is added) and
        // always rebuild from that snapshot. Without this, re-enchanting would re-append a fresh enchant
        // block on top of the previous one every time, since meta.lore() already contains it.
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        List<Component> baseLore;
        if (itemPdc.has(Keys.GENERIC_BASE_LORE_KEY, PersistentDataType.STRING)) {
            baseLore = deserializeLore(itemPdc.get(Keys.GENERIC_BASE_LORE_KEY, PersistentDataType.STRING));
        } else {
            baseLore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            itemPdc.set(Keys.GENERIC_BASE_LORE_KEY, PersistentDataType.STRING, serializeLore(baseLore));
        }

        List<Component> newLore = new ArrayList<>(baseLore);
        List<Component> formattedEnchants = formatEnchants(enchantMap);
        if (!formattedEnchants.isEmpty()) {
            if (!newLore.isEmpty()) {
                newLore.add(Component.empty());
            }
            newLore.addAll(formattedEnchants);
        }

        meta.lore(newLore);
    }

    public static List<Component> formatEnchants(Map<String, Integer> enchantMap) {
        List<Component> lore = new ArrayList<>();
        List<String> sortedIds = new ArrayList<>(enchantMap.keySet());

        sortedIds.sort(String::compareToIgnoreCase);

        if (enchantMap.size() < 4) {
            for (String id : sortedIds) {
                int level = enchantMap.get(id);
                EnchantmentDefinition def = ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(id).orElse(null);
                lore.add(Formatter.format("<blue>" + displayName(id, def) + " " + Formatter.toRoman(level) + "</blue>"));

                if (def != null && def.getDescription() != null) {
                    for (String descLine : def.getDescription()) {
                        lore.add(Formatter.format("<gray>" + descLine + "</gray>"));
                    }
                }
            }
        } else {
            List<Component> shortEnchants = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();

            for (String id : sortedIds) {
                int level = enchantMap.get(id);
                EnchantmentDefinition def = ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(id).orElse(null);
                String enchantStr = displayName(id, def) + " " + Formatter.toRoman(level);

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
        lore.add(Component.empty());

        return lore;
    }

    /** The enchant's configured {@code name:} (e.g. "Sharpness"), falling back to a title-cased
     *  version of the raw id (e.g. {@code "fire_aspect"} -&gt; "Fire Aspect") when the enchant isn't
     *  registered — previously the raw lowercase id (e.g. "sharpness 3") was shown unconditionally,
     *  ignoring the definition's display name entirely. */
    private static String displayName(String id, EnchantmentDefinition def) {
        if (def != null && def.getName() != null && !def.getName().isEmpty()) return def.getName();
        return Formatter.capitalize(id.replace("_", " "));
    }

    public static ItemStack createEnchantedBook(String enchantId, int level) {
        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            EnchantmentDefinition def = ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(enchantId).orElse(null);
            int clampedLevel = def != null ? Math.max(1, Math.min(level, def.getAbsoluteMaxLevel())) : level;

            Map<String, EnchantStateStore.EnchantInstance> instances = new LinkedHashMap<>();
            instances.put(enchantId.toLowerCase(), new EnchantStateStore.EnchantInstance(enchantId, clampedLevel, Map.of()));

            EnchantStateStore.save(meta, instances);
            applyGlowAndLore(book, meta, toLevelMap(instances));
            book.setItemMeta(meta);
        }

        return book;
    }
}
