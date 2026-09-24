package org.nakii.valmora.module.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.util.Keys;

import java.util.Optional;

/**
 * The one place that decides where an item's template-derived data comes from.
 *
 * <p>An item created from a YAML definition stores only its id plus per-item data (enchants,
 * modifiers, pet progress, storage, ...). Values that belong to the <i>template</i> (rarity, type,
 * base stats, name, lore) are read live from the current {@link ItemDefinition}, so an admin's
 * YAML edit applies to items that already exist. The copies in the item's PDC
 * ({@code rarity}, {@code item_type}, the stats container) are only a fallback, for items with no
 * definition: translated vanilla items, alchemy potions, and items whose definition was deleted.
 *
 * <p>Previously lore used the live definition while the modifier engine, enchant applicability and
 * player stats read the PDC copy made at creation, so after a YAML change the same item disagreed
 * with itself.
 */
public final class ItemView {

    private ItemView() {}

    /** The Valmora item id stored on {@code meta}, or {@code null}. */
    public static String itemId(ItemMeta meta) {
        return meta == null ? null : meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
    }

    /** The live definition behind {@code meta} (following id aliases), if it still exists. */
    public static Optional<ItemDefinition> definition(ItemMeta meta) {
        String id = itemId(meta);
        if (id == null) return Optional.empty();
        ValmoraAPI api = ValmoraAPI.getInstance();
        var itemManager = api != null ? api.getItemManager() : null;
        var registry = itemManager != null ? itemManager.getItemRegistry() : null;
        if (registry == null) return Optional.empty();
        Optional<ItemDefinition> def = registry.getItem(id);
        return def != null ? def : Optional.empty();
    }

    public static Optional<ItemDefinition> definition(ItemStack item) {
        return item != null && item.hasItemMeta() ? definition(item.getItemMeta()) : Optional.empty();
    }

    /** Rarity key (e.g. {@code "EPIC"}): the definition's, else the stored copy, else {@code null}. */
    public static String rarityKey(ItemMeta meta) {
        if (meta == null) return null;
        Optional<ItemDefinition> def = definition(meta);
        if (def.isPresent()) return def.get().getRarityKey();
        return meta.getPersistentDataContainer().get(Keys.RARITY_KEY, PersistentDataType.STRING);
    }

    public static String rarityKey(ItemStack item) {
        return item != null && item.hasItemMeta() ? rarityKey(item.getItemMeta()) : null;
    }

    /**
     * Item type: the definition's (when it declares one), else the stored copy, else derived from
     * the material.
     */
    public static ItemType type(ItemStack item) {
        if (item == null) return ItemType.NONE;
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            Optional<ItemDefinition> def = definition(meta);
            if (def.isPresent() && def.get().getItemType() != null && def.get().getItemType() != ItemType.NONE) {
                return def.get().getItemType();
            }
            String stored = meta.getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
            if (stored != null) {
                Optional<ItemType> found = ItemType.find(stored);
                if (found.isPresent()) return found.get();
            }
        }
        return ItemType.fromMaterial(item.getType());
    }

    /**
     * Like {@link #type} but without the material fallback: {@link ItemType#NONE} for items that
     * are neither defined nor tagged. Used where only Valmora-typed items qualify (modifiers).
     */
    public static ItemType templateType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return ItemType.NONE;
        ItemMeta meta = item.getItemMeta();
        Optional<ItemDefinition> def = definition(meta);
        if (def.isPresent() && def.get().getItemType() != null) return def.get().getItemType();
        String stored = meta.getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        return stored == null ? ItemType.NONE : ItemType.find(stored).orElse(ItemType.NONE);
    }

    /**
     * Rewrites the stored rarity/type copies to match the live definition, so anything still
     * reading them directly (and the fallback, should the definition later be deleted) sees current
     * values. No-op for items without a definition.
     */
    public static void syncStoredCopies(ItemMeta meta) {
        Optional<ItemDefinition> def = definition(meta);
        if (def.isEmpty()) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.RARITY_KEY, PersistentDataType.STRING, def.get().getRarityKey());
        ItemType type = def.get().getItemType();
        if (type != null) {
            pdc.set(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING, type.name());
        } else {
            pdc.remove(Keys.ITEM_TYPE_KEY);
        }
    }
}
