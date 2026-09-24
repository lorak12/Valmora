package org.nakii.valmora.module.mob;

import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;

/**
 * Registers extra {@link MobCategory} ids from {@code mob_categories.yml} (Phase 3.4 — see
 * docs/REFACTOR/PROGRESS.md). The original 10 categories are always present as static fields on
 * {@link MobCategory} regardless of this file — this loader only matters for adding new ones
 * (e.g. {@code DRAGON}, {@code ELEMENTAL}) without a code change.
 */
public final class MobCategoryLoader {

    private MobCategoryLoader() {}

    public static void load(Valmora plugin) {
        MobCategory.resetToBuiltins(); // categories removed from the file don't linger across reloads
        File file = new File(plugin.getDataFolder(), "mob_categories.yml");
        if (!file.exists()) return;

        try (org.nakii.valmora.infrastructure.config.diag.LoadSession session = org.nakii.valmora.infrastructure.config.diag.LoadSession.open(plugin, "Mob categories", "mob_categories.yml")) {
            YamlConfiguration config = session.readYaml(file, "mob_categories.yml");
            if (config == null) return;
            if (!config.isList("categories")) {
                session.warn("mob_categories.yml", null, "expected a 'categories:' list — only the built-in categories are available");
                return;
            }
            for (String id : config.getStringList("categories")) {
                if (id == null || id.isBlank()) continue;
                MobCategory.define(id.trim());
                session.loaded();
            }
        }
    }
}
