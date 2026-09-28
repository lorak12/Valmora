package org.nakii.valmora.module.script.pipeline;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.pipeline.CompiledPipelineStage;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.api.pipeline.flow.AnchorFlowStep;
import org.nakii.valmora.api.pipeline.flow.AnchorSpec;
import org.nakii.valmora.api.pipeline.flow.BuiltinFlowStep;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.CompiledFlow;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.api.pipeline.flow.FlowStep;
import org.nakii.valmora.api.pipeline.flow.ScriptFlowStep;
import org.nakii.valmora.api.pipeline.flow.StepParams;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.LoadSession;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.module.script.ScriptModule;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads a flow-based {@code *_pipeline.yml} file (docs/PIPELINE_FLOWS_PLAN.md):
 *
 * <pre>{@code
 * version: 2
 * flows:
 *   catch:
 *     - builtin: resolve_table
 *     - anchor: pre_catch
 *     - builtin: roll_sea_creature
 *       params: { chance: 0.05 }
 *     - id: announce
 *       conditions: [ "$fishing.sea_creature$ != null" ]
 *       on-pass: [ "notify <aqua>Something stirs...</aqua>" ]
 * stages: []        # legacy — each stage runs at the anchor matching its `when:`
 * }</pre>
 *
 * <p>Rules (§3.1/§4 of the plan):
 * <ul>
 *   <li>The copy of the file bundled in the jar is the single source of the defaults: any flow
 *   missing from the data-folder file (or the whole file, if deleted or in the old stages-only
 *   format) uses the bundled flow.</li>
 *   <li>A flow that fails validation (unknown built-in/anchor, a required built-in missing or
 *   disabled, a duplicate step id, a broken required param) keeps its last good compiled version,
 *   or the bundled one on first load.</li>
 *   <li>Legacy {@code stages:} still register on the {@link HookBus}; anchors run them.</li>
 * </ul>
 */
public final class FlowYamlLoader {

    /** Newest {@code version:} this loader understands. */
    public static final int SUPPORTED_VERSION = 2;

    private static final String BUNDLED_PREFIX = "[built-in] ";
    private static final ThreadLocal<FlowDomain> COMPILING = new ThreadLocal<>();

    private FlowYamlLoader() {}

    /** The domain whose file is being compiled on this thread, if any — lets {@code pipeline_set} check its key at load time. */
    public static FlowDomain compilingDomain() {
        return COMPILING.get();
    }

    public static void load(Valmora plugin, ScriptModule scriptModule, FlowDomain domain) {
        scriptModule.getFlowRegistry().register(domain);
        HookBus bus = scriptModule.getHookBus();
        bus.clearYamlStages(domain.pointPrefix());

        String fileName = domain.fileName();
        File file = plugin != null && plugin.getDataFolder() != null ? new File(plugin.getDataFolder(), fileName) : null;

        FlowDomain previous = COMPILING.get();
        COMPILING.set(domain);
        try (LoadSession session = LoadSession.open(plugin, "Pipeline " + fileName, fileName)) {
            boolean present = file != null && file.isFile();
            YamlConfiguration config = present ? session.readYaml(file, fileName) : null;
            boolean unreadable = present && config == null;

            Map<String, String> legacyStages = config == null ? Map.of()
                    : PipelineYamlLoader.registerStages(scriptModule, bus, session, fileName, config,
                    domain.pointPrefix(), domain.legacyPoints());

            ConfigurationSection userFlows = config == null ? null : userFlows(config, session, domain);
            YamlConfiguration bundled = null;
            boolean bundledLoaded = false;

            Set<String> anchorsInUse = new HashSet<>();
            for (FlowSpec<?> spec : domain.flows()) {
                CompiledFlow compiled = null;
                boolean userFlowFailed = false;
                if (userFlows != null && userFlows.contains(spec.id())) {
                    try (LoadScope scope = session.entry(fileName, "flows." + spec.id())) {
                        compiled = compileFlow(scriptModule, bus, domain, spec, userFlows.get(spec.id()), scope, "file");
                        if (compiled == null) {
                            userFlowFailed = true;
                            scope.info(spec.current() != null
                                    ? "keeping the previously loaded version of this flow"
                                    : "using the built-in default version of this flow");
                        }
                    }
                } else if (userFlows != null) {
                    try (LoadScope scope = session.entry(fileName, "flows." + spec.id())) {
                        scope.info("flow '" + spec.id() + "' isn't in the file — using the built-in default");
                    }
                }

                if (compiled == null && (unreadable || userFlowFailed) && spec.current() != null) {
                    compiled = spec.current();
                }
                if (compiled == null) {
                    if (!bundledLoaded) {
                        bundled = readBundled(plugin, fileName, session);
                        bundledLoaded = true;
                    }
                    compiled = compileBundled(scriptModule, bus, domain, spec, bundled, session);
                }
                if (compiled != null) {
                    spec.install(compiled);
                    session.loaded();
                    for (FlowStep step : compiled.steps()) {
                        if (step instanceof AnchorFlowStep anchor) anchorsInUse.add(anchor.point().toLowerCase(Locale.ROOT));
                    }
                }
            }

            if (config != null && !config.contains("flows") && present && !unreadable) {
                try (LoadScope scope = session.file(fileName)) {
                    scope.info("no 'flows:' section (the older stages-only format) — every flow uses its built-in default. "
                            + "If you edited this file, the new default version was saved next to it as " + fileName + ".new");
                }
            }
            reportUnreachable(session, fileName, bus, domain, legacyStages, anchorsInUse);
        } finally {
            if (previous == null) COMPILING.remove();
            else COMPILING.set(previous);
        }
    }

    // ------------------------------------------------------------------ file-level

    private static ConfigurationSection userFlows(YamlConfiguration config, LoadSession session, FlowDomain domain) {
        String fileName = domain.fileName();
        try (LoadScope scope = session.file(fileName)) {
            ConfigReader.of(config, scope).knownKeys("version", "flows", "stages");
            if (!config.contains("flows")) return null;
            int version = config.getInt("version", SUPPORTED_VERSION);
            if (version > SUPPORTED_VERSION) {
                scope.sub("version").error("version " + version + " is newer than this Valmora supports (" + SUPPORTED_VERSION
                        + ") — every flow uses its built-in default");
                return null;
            }
            ConfigurationSection flows = config.getConfigurationSection("flows");
            if (flows == null) {
                scope.sub("flows").warn("'flows:' must map each flow id to a list of steps — every flow uses its built-in default");
                return null;
            }
            for (String key : flows.getKeys(false)) {
                if (domain.flow(key) == null) {
                    scope.sub("flows").sub(key).warn("unknown flow '" + key + "' for " + fileName + " — ignored",
                            Suggestions.hint(key, domain.flowIds()));
                }
            }
            return flows;
        }
    }

    /** Warns about legacy stages whose point no flow runs, and Java hooks sitting on a removed anchor. */
    private static void reportUnreachable(LoadSession session, String fileName, HookBus bus, FlowDomain domain,
                                          Map<String, String> legacyStages, Set<String> anchorsInUse) {
        Set<String> declaredPoints = new HashSet<>();
        for (FlowSpec<?> spec : domain.flows()) {
            for (AnchorSpec anchor : spec.anchors()) declaredPoints.add(anchor.point().toLowerCase(Locale.ROOT));
        }
        try (LoadScope scope = session.file(fileName)) {
            for (Map.Entry<String, String> stage : legacyStages.entrySet()) {
                String point = stage.getValue().toLowerCase(Locale.ROOT);
                // A point this domain's flows never declare as an anchor is owned by another file's flow.
                if (declaredPoints.contains(point) && !anchorsInUse.contains(point)) {
                    scope.sub("stages").warn("stage '" + stage.getKey() + "' runs at '" + stage.getValue()
                            + "', but no flow in this file has that anchor any more — it will never run");
                }
            }
            for (String point : declaredPoints) {
                if (anchorsInUse.contains(point)) continue;
                List<String> hooks = bus.getJavaHookIds(point);
                if (!hooks.isEmpty()) {
                    scope.sub("flows").warn("anchor '" + point + "' was removed, but plugin hooks " + hooks
                            + " are registered there — they will never run");
                }
            }
        }
    }

    // ------------------------------------------------------------------ bundled defaults

    private static YamlConfiguration readBundled(Valmora plugin, String fileName, LoadSession session) {
        InputStream in = null;
        try {
            if (plugin != null) in = plugin.getResource(fileName);
        } catch (RuntimeException ignored) {
            // mocked plugin without resources — fall through to the classpath
        }
        if (in == null) in = FlowYamlLoader.class.getResourceAsStream("/" + fileName);
        if (in == null) {
            session.error(BUNDLED_PREFIX + fileName, null, "the default " + fileName + " is missing from the plugin jar");
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        } catch (Exception e) {
            session.error(BUNDLED_PREFIX + fileName, null, "couldn't read the default " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private static CompiledFlow compileBundled(ScriptModule scriptModule, HookBus bus, FlowDomain domain,
                                               FlowSpec<?> spec, YamlConfiguration bundled, LoadSession session) {
        String file = BUNDLED_PREFIX + domain.fileName();
        try (LoadScope scope = session.entry(file, "flows." + spec.id())) {
            Object raw = bundled == null ? null : bundled.get("flows." + spec.id());
            if (raw == null) {
                scope.error("the default " + domain.fileName() + " has no flow '" + spec.id() + "' — this flow is disabled");
                return null;
            }
            CompiledFlow flow = compileFlow(scriptModule, bus, domain, spec, raw, scope, "bundled");
            if (flow == null) scope.error("the built-in default version of this flow failed to compile — this flow is disabled");
            return flow;
        }
    }

    // ------------------------------------------------------------------ one flow

    /**
     * Compiles one flow's step list. Returns null if any ERROR was reported for it.
     * Public for tests.
     */
    public static <S extends FlowState> CompiledFlow compileFlow(ScriptModule scriptModule, HookBus bus, FlowDomain domain,
                                                                FlowSpec<S> spec, Object raw, LoadScope scope, String source) {
        int errorsBefore = scope.errorCount();
        if (!(raw instanceof List<?> list)) {
            scope.error("a flow must be a list of steps");
            return null;
        }

        List<FlowStep> steps = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> enabledBuiltins = new HashSet<>();

        for (int i = 0; i < list.size(); i++) {
            try (LoadScope at = scope.sub(String.valueOf(i)).push()) {
                ConfigurationSection section = PipelineYamlLoader.toSection(list.get(i));
                if (section == null) {
                    at.error("expected a step (builtin:, anchor:, or a script step with on-pass:) — got '" + list.get(i) + "'");
                    continue;
                }
                FlowStep step = compileStep(scriptModule, bus, domain, spec, section, at, ids, enabledBuiltins);
                if (step != null) steps.add(step);
            }
        }

        for (BuiltinStepFactory<S> factory : spec.builtins()) {
            if (factory.required() && !enabledBuiltins.contains(factory.id().toLowerCase(Locale.ROOT))) {
                scope.error("required step 'builtin: " + factory.id() + "' is missing or disabled — this flow can't work without it");
            }
        }

        if (scope.errorCount() > errorsBefore) return null;
        return new CompiledFlow(spec.key(), steps, source);
    }

    private static <S extends FlowState> FlowStep compileStep(ScriptModule scriptModule, HookBus bus, FlowDomain domain,
                                                             FlowSpec<S> spec, ConfigurationSection section, LoadScope at,
                                                             Set<String> ids, Set<String> enabledBuiltins) {
        ConfigReader reader = ConfigReader.of(section, at);
        String builtin = reader.string("builtin", null);
        String anchor = reader.string("anchor", null);
        boolean script = section.contains("on-pass") || section.contains("on-fail");
        int kinds = (builtin != null ? 1 : 0) + (anchor != null ? 1 : 0) + (script ? 1 : 0);
        if (kinds != 1) {
            at.error(kinds == 0
                    ? "a step needs one of 'builtin:', 'anchor:', or 'on-pass:'/'on-fail:' (a script step)"
                    : "a step can only be one of 'builtin:', 'anchor:', or a script step ('on-pass:'/'on-fail:')");
            return null;
        }

        String id = reader.string("id", builtin != null ? builtin : anchor);
        if (id == null || id.isBlank()) {
            reader.error("id", "a script step needs an 'id:'");
            return null;
        }
        if (!ids.add(id.toLowerCase(Locale.ROOT))) {
            reader.error("id", "step id '" + id + "' is used more than once in this flow — give one of them its own 'id:'");
            return null;
        }
        boolean enabled = reader.bool("enabled", true);
        // A script step compiles its own conditions (with on-fail semantics) in compileScript.
        Condition conditions = !script && section.contains("conditions")
                ? ScriptCompile.at(at, "conditions", () -> scriptModule.getConditionParser().parseList(reader.stringList("conditions")))
                : null;

        if (builtin != null) {
            reader.knownKeys("builtin", "id", "enabled", "conditions", "params");
            BuiltinStepFactory<S> factory = spec.builtin(builtin);
            if (factory == null) {
                List<String> names = new ArrayList<>();
                for (BuiltinStepFactory<S> f : spec.builtins()) names.add(f.id());
                reader.error("builtin", "unknown built-in step '" + builtin + "' for flow '" + spec.key() + "' (available: " + names + ")",
                        Suggestions.hint(builtin, names));
                return null;
            }
            ConfigurationSection paramsSection = section.getConfigurationSection("params");
            if (section.contains("params") && paramsSection == null) {
                reader.warn("params", "'params:' must be a map of name: value — ignored");
            }
            LoadScope paramsScope = at.sub("params");
            StepParams params = new StepParams(paramsSection, paramsScope, scriptModule.getExpressionParser());
            if (paramsSection != null) {
                params.reader().knownKeys(factory.paramKeys());
            }
            var compiled = factory.compile(params);
            if (!enabled) return null;
            enabledBuiltins.add(factory.id().toLowerCase(Locale.ROOT));
            return new BuiltinFlowStep<>(id, factory.id(), conditions, compiled);
        }

        if (anchor != null) {
            reader.knownKeys("anchor", "id", "enabled", "conditions", "stop-on-interrupt");
            String point = anchor.contains(":") ? anchor : domain.pointPrefix() + anchor;
            AnchorSpec anchorSpec = spec.anchorSpec(point);
            if (anchorSpec == null) {
                List<String> names = new ArrayList<>();
                for (AnchorSpec a : spec.anchors()) {
                    names.add(a.point().startsWith(domain.pointPrefix()) ? a.name() : a.point());
                }
                reader.error("anchor", "unknown anchor '" + anchor + "' for flow '" + spec.key() + "' (available: " + names + ")",
                        Suggestions.hint(anchor, names));
                return null;
            }
            boolean stopOnInterrupt = reader.bool("stop-on-interrupt", anchorSpec.stopsOnInterrupt());
            if (!enabled) return null;
            return new AnchorFlowStep(id, anchorSpec.point(), bus, conditions, stopOnInterrupt);
        }

        reader.knownKeys("id", "enabled", "conditions", "on-pass", "on-fail");
        CompiledPipelineStage stage = PipelineYamlLoader.compileScript(scriptModule, section, id);
        if (!enabled) return null;
        return new ScriptFlowStep(stage, section.contains("conditions"));
    }
}
