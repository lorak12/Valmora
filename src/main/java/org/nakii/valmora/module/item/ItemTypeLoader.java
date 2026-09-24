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

        try (org.nakii.valmora.infrastructure.config.diag.LoadSession session =
                     org.nakii.valmora.infrastructure.config.diag.LoadSession.open(plugin, "Item types", "item_types.yml")) {
            YamlConfiguration config = session.readYaml(file, "item_types.yml");
            if (config == null) return;
            if (!config.isList("item_types")) {
                session.warn("item_types.yml", null, "expected an 'item_types:' list — only the built-in types are available");
                return;
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String id : config.getStringList("item_types")) {
                if (id == null || id.isBlank()) continue;
                if (!seen.add(id.trim().toUpperCase(java.util.Locale.ROOT))) {
                    session.warn("item_types.yml", id, "listed more than once");
                    continue;
                }
                ItemType.define(id.trim());
                session.loaded();
            }
        }
    }
}
