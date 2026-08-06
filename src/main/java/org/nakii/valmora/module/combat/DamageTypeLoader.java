package org.nakii.valmora.module.combat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads {@code damage_types/*.yml} and (re)defines {@link DamageType} instances from it
 * (Phase 2.1 — see docs/REFACTOR/PROGRESS.md). If the folder is missing or empty, the 11
 * built-in defaults declared as static fields on {@link DamageType} remain in effect — this is
 * the backward-compatibility path for servers that don't ship the new config yet.
 */
public class DamageTypeLoader {

    private final Valmora plugin;
    private final Logger log;

    public DamageTypeLoader(Valmora plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "damage_types");
        if (!dir.exists()) {
            log.info("[DamageTypeLoader] damage_types/ folder not found — using built-in defaults.");
            return;
        }

        File[] files = dir.listFiles((FilenameFilter) (d, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.info("[DamageTypeLoader] No damage type YAML files found — using built-in defaults.");
            return;
        }

        int count = 0;
        for (File file : files) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String id : config.getKeys(false)) {
                var section = config.getConfigurationSection(id);
                if (section == null) continue;
                String color = section.getString("color", "<white>");
                boolean ignoresDefense = section.getBoolean("ignores-defense", false);
                List<String> onHit = section.getStringList("on-hit");
                DamageType.define(id, color, ignoresDefense, onHit);
                count++;
            }
        }
        log.info("[DamageTypeLoader] Loaded/overrode " + count + " damage type definitions.");
    }
}
