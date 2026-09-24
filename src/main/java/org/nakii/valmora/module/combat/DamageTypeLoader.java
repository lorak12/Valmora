package org.nakii.valmora.module.combat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.LoadSession;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
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
        DamageType.resetToBuiltins(); // types removed from YAML don't linger across reloads
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

        try (LoadSession session = LoadSession.open(plugin, "Damage types", "damage_types")) {
            java.util.Arrays.sort(files);
            for (File file : files) {
                String path = "damage_types/" + file.getName();
                FileConfiguration config = session.readYaml(file, path);
                if (config == null) continue;
                for (String id : config.getKeys(false)) {
                    var section = config.getConfigurationSection(id);
                    if (section == null) {
                        session.warn(path, id, "expected a section (color, ignores-defense, on-hit) — ignored");
                        continue;
                    }
                    try (LoadScope ignored = session.entry(path, id)) {
                        ConfigReader.of(section).knownKeys("color", "ignores-defense", "on-hit");
                        String color = section.getString("color", "<white>");
                        boolean ignoresDefense = section.getBoolean("ignores-defense", false);
                        List<String> onHit = section.getStringList("on-hit");
                        // on-hit is compiled inside define(), i.e. inside this entry's scope.
                        ScriptCompile.at("on-hit", () -> DamageType.define(id, color, ignoresDefense, onHit));
                        session.loaded();
                    }
                }
            }
        }
    }
}
