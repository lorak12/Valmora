package org.nakii.valmora.module.script.pipeline;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.pipeline.CompiledPipelineStage;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.module.script.ScriptModule;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Shared YAML-stage compiler for every domain that registers on {@link HookBus} (combat, resource
 * mining, fishing — see docs/COMBAT_PIPELINE_ANALYSIS.md). Extracted so each domain's "pipeline
 * loader" is a two-line wrapper instead of a fourth copy of the same
 * "read stages: list, compile id/when/conditions/on-pass/on-fail, register" logic that combat's
 * {@code CombatPipelineLoader} originally had — the exact kind of per-subsystem reimplementation
 * the analysis doc flagged (GUI event blocks / item abilities / mob abilities each already
 * duplicate this shape).
 *
 * <p>File format, identical across every domain that uses it:
 * <pre>{@code
 * stages:
 *   - id: my_stage
 *     when: <one of validPoints>
 *     conditions: [ "..." ]
 *     on-pass: [ "..." ]
 *     on-fail: [ "..." ]
 * }</pre>
 */
public final class PipelineYamlLoader {

    private PipelineYamlLoader() {}

    /**
     * Loads {@code fileName} from the plugin data folder, clears any previously-loaded YAML stages
     * under {@code pointPrefix}, and re-registers the compiled stages onto the shared
     * {@link HookBus}. Safe to call with a missing file or empty {@code stages:} list — registers
     * nothing (see {@link HookBus#hasStages}, the zero-cost-when-unused guard every domain caller
     * checks before touching the pipeline on its hot path).
     *
     * @return the number of stages compiled and registered.
     */
    public static int loadFile(Valmora plugin, ScriptModule scriptModule, String fileName,
                                String pointPrefix, List<String> validPoints) {
        HookBus bus = scriptModule.getHookBus();
        bus.clearYamlStages(pointPrefix);

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            return 0;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> rawStages = config.getList("stages");
        if (rawStages == null || rawStages.isEmpty()) {
            return 0;
        }

        int compiled = 0;
        for (Object raw : rawStages) {
            ConfigurationSection section = toSection(raw);
            if (section == null) continue;
            if (compileStage(plugin, scriptModule, bus, section, pointPrefix, validPoints)) compiled++;
        }
        if (compiled > 0) {
            plugin.getLogger().info("[PipelineYamlLoader] " + fileName + ": registered " + compiled + " stage(s).");
        }
        return compiled;
    }

    private static boolean compileStage(Valmora plugin, ScriptModule scriptModule, HookBus bus,
                                         ConfigurationSection section, String pointPrefix, List<String> validPoints) {
        String id = section.getString("id");
        String when = section.getString("when");
        if (id == null || when == null) {
            plugin.getLogger().warning("[PipelineYamlLoader] Skipping stage missing 'id' or 'when': " + section);
            return false;
        }
        if (!validPoints.contains(when)) {
            plugin.getLogger().warning("[PipelineYamlLoader] Skipping stage '" + id + "': unknown insertion point '"
                    + when + "' (expected one of " + validPoints + ")");
            return false;
        }

        var conditions = scriptModule.getConditionParser().parseList(section.getStringList("conditions"));
        var onPass = scriptModule.getEventParser().parseList(section.getStringList("on-pass"));
        var onFail = scriptModule.getEventParser().parseList(section.getStringList("on-fail"));

        bus.registerYamlStage(pointPrefix + when, new CompiledPipelineStage(id, conditions, onPass, onFail));
        return true;
    }

    /** {@code YamlConfiguration#getList} returns raw {@code Map} entries for list-of-maps YAML; wrap them as a section. */
    private static ConfigurationSection toSection(Object raw) {
        if (raw instanceof ConfigurationSection cs) return cs;
        if (raw instanceof Map<?, ?> map) {
            YamlConfiguration section = new YamlConfiguration();
            for (var entry : map.entrySet()) {
                section.set(String.valueOf(entry.getKey()), entry.getValue());
            }
            return section;
        }
        return null;
    }
}
