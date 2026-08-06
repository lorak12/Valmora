package org.nakii.valmora.module.resource;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.PipelineYamlLoader;

import java.util.List;

/**
 * Loads {@code resource_pipeline.yml} onto the shared {@link org.nakii.valmora.api.pipeline.HookBus}
 * under the {@code "resource:<when>"} insertion points: {@code pre_break} (before Breaking Power
 * gating already passed / drops rolled — an {@code interrupt} here cancels the break exactly like
 * insufficient power) and {@code post_break} (after drops are granted and the block has
 * progressed/regenerated).
 *
 * <p>Scoped to <b>tracked resource blocks only</b> — the zone-configured ore/resource groups
 * {@link ResourceManager} already tracks — not every vanilla block break in the world. This
 * mirrors the combat pipeline's design: {@link ResourceManager#handleBlockBreak} already returns
 * {@code NOT_TRACKED} for the vast majority of block breaks before this pipeline is ever consulted,
 * so ordinary building/digging pays nothing for this feature. See {@link PipelineYamlLoader} for
 * the shared stage format and docs/COMBAT_PIPELINE_ANALYSIS.md §6 for the "grouped block breaking"
 * rationale.
 */
public class ResourcePipelineLoader {

    static final String POINT_PREFIX = "resource:";
    static final String PRE_BREAK = POINT_PREFIX + "pre_break";
    static final String POST_BREAK = POINT_PREFIX + "post_break";
    private static final List<String> VALID_POINTS = List.of("pre_break", "post_break");

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public ResourcePipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        PipelineYamlLoader.loadFile(plugin, scriptModule, "resource_pipeline.yml", POINT_PREFIX, VALID_POINTS);
    }
}
