package org.nakii.valmora.module.item;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.module.enchant.EnchantStateStore;
import org.nakii.valmora.util.Keys;

/**
 * Versioned upgrades for the data Valmora stores on an item, mirroring the database's migration
 * ladder. {@code valmora:item_data_version} records which shape an item's PDC is in (absent = 0),
 * and {@link #migrate} walks it forward one step at a time. It runs from {@link ItemRefresher},
 * i.e. whenever an item is seen (join, inventory open, pickup, held, after reload), so old items
 * upgrade lazily without a world scan.
 *
 * <p>To change how something is stored on items: bump {@link #LATEST_VERSION} and add a
 * {@code toVN} step. Readers may then drop their fallback for the old shape once the step exists
 * (the migration still covers items that sat untouched in a chest).
 */
public final class ItemMigrator {

    /** The item data version this build writes. */
    public static final int LATEST_VERSION = 1;

    private ItemMigrator() {}

    /** Whether {@code meta} needs {@link #migrate}. Cheap: one PDC read. */
    public static boolean needsMigration(ItemMeta meta) {
        return version(meta.getPersistentDataContainer()) < LATEST_VERSION;
    }

    /**
     * Upgrades {@code meta} in place. Returns {@code true} if anything changed. Items written by a
     * newer plugin version are left untouched.
     */
    public static boolean migrate(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int version = version(pdc);
        if (version >= LATEST_VERSION) return false;
        if (version < 1) toV1(meta);
        pdc.set(Keys.ITEM_DATA_VERSION_KEY, PersistentDataType.INTEGER, LATEST_VERSION);
        return true;
    }

    /** Stamps a freshly created item as current, so it never goes through the ladder. */
    public static void stampCurrent(ItemMeta meta) {
        meta.getPersistentDataContainer().set(Keys.ITEM_DATA_VERSION_KEY, PersistentDataType.INTEGER, LATEST_VERSION);
    }

    private static int version(PersistentDataContainer pdc) {
        return pdc.getOrDefault(Keys.ITEM_DATA_VERSION_KEY, PersistentDataType.INTEGER, 0);
    }

    /**
     * v1: the legacy flat enchant CSV ({@code valmora_enchants_container}, {@code "id:level,..."})
     * becomes the structured per-instance format. Before this, items only switched format when
     * their enchants were next changed, so the read-only CSV fallback in EnchantStateStore had to
     * live forever.
     */
    private static void toV1(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING)
                && !pdc.has(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY)) {
            // load() reads the CSV fallback; save() writes the structured format and drops the CSV.
            EnchantStateStore.save(meta, EnchantStateStore.load(pdc));
        }
    }
}
