package org.nakii.valmora.api.pipeline;

import org.bukkit.plugin.Plugin;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.util.DebugManager;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Generic, reload-safe dispatch bus for named "insertion points" — a shared primitive so every
 * subsystem that needs a "trigger name -> conditions -> pass/fail actions" pipeline (combat, and
 * in the future block-breaking groups, fishing, etc.) registers stages here instead of hand-rolling
 * its own dispatch loop (see docs/COMBAT_PIPELINE_ANALYSIS.md — GUI event blocks, item abilities,
 * and mob abilities each independently reimplement this shape today).
 *
 * <p>Points are plain strings, namespaced by convention (e.g. {@code "combat:pre_damage"},
 * {@code "combat:on_death"}) so unrelated domains can't collide. Two kinds of stages can be
 * registered at the same point:
 * <ul>
 *     <li><b>YAML stages</b> — compiled from a config file by a domain-specific loader (e.g.
 *     {@code CombatPipelineLoader}). Cleared and rebuilt on every {@code /valmora reload} via
 *     {@link #clearYamlStages(String)}.</li>
 *     <li><b>Java hooks</b> — registered once by any plugin (including Valmora itself) via
 *     {@link #registerHook(String, String, PipelineHook)}. These survive Valmora module reloads;
 *     the registering plugin is responsible for calling {@link #unregisterHook(String, String)} on
 *     its own shutdown.</li>
 * </ul>
 *
 * <p><b>Zero-cost when unused:</b> {@link #hasStages(String)} / {@link #hasAnyStages(String...)}
 * let hot-path callers (e.g. {@code CombatListener}) skip building an {@link ExecutionContext} and
 * skip calling {@link #runPoint(String, ExecutionContext)} entirely when no stage is registered at
 * a point — the default, no-config install pays nothing for this feature.
 */
public class HookBus {

    private final Plugin plugin;
    private final Map<String, List<PipelineStage>> yamlStages = new ConcurrentHashMap<>();
    private final Map<String, List<PipelineStage>> javaHooks = new ConcurrentHashMap<>();

    public HookBus(Plugin plugin) {
        this.plugin = plugin;
    }

    // --- YAML-loaded stages (owned by a domain loader, rebuilt on reload) ---

    /** Appends a compiled stage at the given insertion point. */
    public void registerYamlStage(String point, PipelineStage stage) {
        yamlStages.computeIfAbsent(point, p -> new CopyOnWriteArrayList<>()).add(stage);
    }

    /** Removes every YAML stage registered at any point starting with {@code pointPrefix} (e.g. {@code "combat:"}). */
    public void clearYamlStages(String pointPrefix) {
        yamlStages.keySet().removeIf(point -> point.startsWith(pointPrefix));
    }

    // --- Java hooks (registered once by addon code, persist across reload) ---

    /** Registers a Java-side hook at the given insertion point. {@code id} must be unique for this point+registrant. */
    public void registerHook(String point, String id, PipelineHook hook) {
        javaHooks.computeIfAbsent(point, p -> new CopyOnWriteArrayList<>())
                .add(new JavaHookStage(id, hook));
    }

    /** Unregisters a previously-registered Java hook. No-op if not found. */
    public void unregisterHook(String point, String id) {
        List<PipelineStage> stages = javaHooks.get(point);
        if (stages == null) return;
        stages.removeIf(stage -> stage.getId().equals(id));
    }

    // --- Dispatch ---

    /** @return true if any stage (YAML or Java) is registered at this point. */
    public boolean hasStages(String point) {
        return nonEmpty(javaHooks.get(point)) || nonEmpty(yamlStages.get(point));
    }

    /** @return true if any of the given points has at least one registered stage. */
    public boolean hasAnyStages(String... points) {
        for (String point : points) {
            if (hasStages(point)) return true;
        }
        return false;
    }

    /**
     * Runs every stage registered at {@code point} (Java hooks first, then YAML stages, each in
     * registration order), stopping early if a stage returns {@link StageResult#INTERRUPT}. A
     * throwing stage is logged and treated as {@link StageResult#CONTINUE} so one broken hook can't
     * take down the surrounding combat/event pipeline.
     *
     * @return false if a stage interrupted the pipeline, true otherwise.
     */
    public boolean runPoint(String point, ExecutionContext context) {
        boolean debug = DebugManager.isEnabled("script");
        if (debug && hasStages(point)) {
            DebugManager.log("script", "runPoint '" + point + "': java=" + getJavaHookIds(point)
                    + " yaml=" + getYamlStageIds(point));
        }
        if (!runList(javaHooks.get(point), point, context)) {
            if (debug) DebugManager.log("script", "runPoint '" + point + "' INTERRUPTED by a java hook");
            return false;
        }
        boolean result = runList(yamlStages.get(point), point, context);
        if (debug && !result) DebugManager.log("script", "runPoint '" + point + "' INTERRUPTED by a yaml stage");
        return result;
    }

    private boolean runList(List<PipelineStage> stages, String point, ExecutionContext context) {
        if (stages == null || stages.isEmpty()) return true;
        for (Iterator<PipelineStage> it = stages.iterator(); it.hasNext(); ) {
            PipelineStage stage = it.next();
            try {
                if (stage.run(context) == StageResult.INTERRUPT) {
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[HookBus] Stage '" + stage.getId() + "' at '" + point
                        + "' threw an exception, skipping it: " + e);
            }
        }
        return true;
    }

    /** Clears everything — YAML and Java hooks alike. Only meant for full plugin shutdown. */
    public void clearAll() {
        yamlStages.clear();
        javaHooks.clear();
    }

    // --- Introspection (backs `/valmora pipeline list` — see docs/COMBAT_PIPELINE_ANALYSIS.md §5
    // "debugging difficulty": this is the runtime visibility into what's actually registered) ---

    /** @return every insertion point with at least one stage registered, YAML or Java, sorted. */
    public java.util.Set<String> getRegisteredPoints() {
        java.util.Set<String> points = new java.util.TreeSet<>(yamlStages.keySet());
        points.addAll(javaHooks.keySet());
        points.removeIf(point -> !hasStages(point)); // drop points left empty by unregisterHook
        return points;
    }

    /** @return the ids of Java hooks registered at {@code point}, in run order. */
    public List<String> getJavaHookIds(String point) {
        return ids(javaHooks.get(point));
    }

    /** @return the ids of YAML-compiled stages registered at {@code point}, in run order. */
    public List<String> getYamlStageIds(String point) {
        return ids(yamlStages.get(point));
    }

    private static List<String> ids(List<PipelineStage> stages) {
        if (stages == null || stages.isEmpty()) return List.of();
        List<String> out = new java.util.ArrayList<>(stages.size());
        for (PipelineStage stage : stages) out.add(stage.getId());
        return out;
    }

    private static boolean nonEmpty(List<PipelineStage> list) {
        return list != null && !list.isEmpty();
    }

    private record JavaHookStage(String id, PipelineHook hook) implements PipelineStage {
        @Override
        public String getId() {
            return id;
        }

        @Override
        public StageResult run(ExecutionContext context) {
            return hook.handle(context);
        }
    }
}
