package org.nakii.valmora.module.item;

import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;

/**
 * Registers extra {@link ItemType} ids from {@code item_types.yml} (Phase 4.1 — see
 * docs/REFACTOR/PROGRESS.md). The original 21 types are always present as static fields on
 * {@link ItemType} regardless of this file — this loader only matters for adding new ones
 * without a code change.
 */
public final class ItemTypeLoader {

    private ItemTypeLoader() {}

    public static void load(Valmora plugin) {
        ItemType.resetToBuiltins(); // types removed from the file don't linger across reloads
        File file = new File(plugin.getDataFolder(), "item_types.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int count = 0;
        for (String id : config.getStringList("item_types")) {
            if (id == null || id.isBlank()) continue;
            ItemType.define(id.trim());
            count++;
        }
        plugin.getLogger().info("[ItemTypeLoader] Registered " + count + " item types from item_types.yml.");
    }
}
