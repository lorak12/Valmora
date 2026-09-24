package org.nakii.valmora.module.script.pipeline;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.LoadSession;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
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

        try (LoadSession session = LoadSession.open(plugin, "Pipeline " + fileName, fileName)) {
            FileConfiguration config = session.readYaml(file, fileName);
            if (config == null) return 0;
            List<?> rawStages = config.getList("stages");
            if (rawStages == null || rawStages.isEmpty()) {
                if (config.contains("stages") && !config.isList("stages")) {
                    session.warn(fileName, null, "'stages:' must be a list of stages");
                }
                return 0;
            }

            int compiled = 0;
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < rawStages.size(); i++) {
                ConfigurationSection section = toSection(rawStages.get(i));
                if (section == null) {
                    session.warn(fileName, "stages[" + i + "]", "expected a stage (id, when, conditions, on-pass, on-fail) — skipped");
                    continue;
                }
                String entryId = section.getString("id", "stages[" + i + "]");
                try (LoadScope scope = session.entry(fileName, entryId)) {
                    if (section.getString("id") != null && !ids.add(section.getString("id").toLowerCase())) {
                        scope.warn("stage id '" + section.getString("id") + "' is used more than once in this file");
                    }
                    if (compileStage(scriptModule, bus, section, pointPrefix, validPoints)) {
                        compiled++;
                        session.loaded();
                    }
                }
            }
            return compiled;
        }
    }

    private static boolean compileStage(ScriptModule scriptModule, HookBus bus,
                                         ConfigurationSection section, String pointPrefix, List<String> validPoints) {
        ConfigReader reader = ConfigReader.of(section).knownKeys("id", "when", "conditions", "on-pass", "on-fail");
        String id = reader.requireString("id");
        String when = reader.requireString("when");
        if (id == null || when == null) return false;
        if (!validPoints.contains(when)) {
            reader.error("when", "unknown insertion point '" + when + "' (expected one of " + validPoints + ") — stage skipped",
                    Suggestions.hint(when, validPoints));
            return false;
        }

        var conditions = ScriptCompile.at("conditions", () -> scriptModule.getConditionParser().parseList(section.getStringList("conditions")));
        var onPass = ScriptCompile.at("on-pass", () -> scriptModule.getEventParser().parseList(section.getStringList("on-pass")));
        var onFail = ScriptCompile.at("on-fail", () -> scriptModule.getEventParser().parseList(section.getStringList("on-fail")));

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
