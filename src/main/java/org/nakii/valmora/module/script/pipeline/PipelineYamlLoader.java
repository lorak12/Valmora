package org.nakii.valmora.module.script.pipeline;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.LoadSession;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.pipeline.CompiledPipelineStage;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.script.ScriptModule;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared compiler for a pipeline file's legacy {@code stages:} list — each entry a script stage
 * registered on the {@link HookBus} at the point its {@code when:} names. {@link FlowYamlLoader}
 * uses it for every flow-based {@code *_pipeline.yml} file (stages run at the flow's matching
 * {@code anchor:}); {@link #loadFile} is the stages-only entry point for files without flows.
 *
 * <p>Stage format:
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
     * nothing.
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
            return registerStages(scriptModule, bus, session, fileName, config, pointPrefix, validPoints).size();
        }
    }

    /**
     * Compiles {@code config}'s {@code stages:} list and registers each stage at
     * {@code pointPrefix + when}. Problems are reported into {@code session}.
     *
     * @return stage id → the full point it was registered at, in file order
     */
    public static Map<String, String> registerStages(ScriptModule scriptModule, HookBus bus, LoadSession session,
                                                     String fileName, FileConfiguration config,
                                                     String pointPrefix, List<String> validPoints) {
        Map<String, String> registered = new LinkedHashMap<>();
        List<?> rawStages = config.getList("stages");
        if (rawStages == null || rawStages.isEmpty()) {
            if (config.contains("stages") && !config.isList("stages")) {
                session.warn(fileName, null, "'stages:' must be a list of stages");
            }
            return registered;
        }

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
                String point = compileStage(scriptModule, bus, section, pointPrefix, validPoints);
                if (point != null) {
                    registered.put(section.getString("id"), point);
                    session.loaded();
                }
            }
        }
        return registered;
    }

    /** @return the full point the stage was registered at, or null if it wasn't. */
    private static String compileStage(ScriptModule scriptModule, HookBus bus,
                                       ConfigurationSection section, String pointPrefix, List<String> validPoints) {
        ConfigReader reader = ConfigReader.of(section).knownKeys("id", "when", "conditions", "on-pass", "on-fail");
        String id = reader.requireString("id");
        String when = reader.requireString("when");
        if (id == null || when == null) return null;
        if (!validPoints.contains(when)) {
            reader.error("when", "unknown insertion point '" + when + "' (expected one of " + validPoints + ") — stage skipped",
                    Suggestions.hint(when, validPoints));
            return null;
        }

        bus.registerYamlStage(pointPrefix + when, compileScript(scriptModule, section, id));
        return pointPrefix + when;
    }

    /**
     * Compiles a script stage's {@code conditions}/{@code on-pass}/{@code on-fail} (reporting at
     * those keys inside the current scope). Also used for script steps placed inside a flow.
     */
    public static CompiledPipelineStage compileScript(ScriptModule scriptModule, ConfigurationSection section, String id) {
        Condition conditions = ScriptCompile.at("conditions", () -> scriptModule.getConditionParser().parseList(section.getStringList("conditions")));
        var onPass = ScriptCompile.at("on-pass", () -> scriptModule.getEventParser().parseList(section.getStringList("on-pass")));
        var onFail = ScriptCompile.at("on-fail", () -> scriptModule.getEventParser().parseList(section.getStringList("on-fail")));
        return new CompiledPipelineStage(id, conditions, onPass, onFail);
    }

    /**
     * {@code YamlConfiguration#getList} returns raw {@code Map} entries for list-of-maps YAML, and
     * {@code set()} would keep nested maps raw too; {@code createSection} converts them recursively
     * so a nested {@code params:} block reads as a section.
     */
    public static ConfigurationSection toSection(Object raw) {
        if (raw instanceof ConfigurationSection cs) return cs;
        if (raw instanceof Map<?, ?> map) {
            return new MemoryConfiguration().createSection("entry", map);
        }
        return null;
    }
}
