package org.nakii.valmora.module.fishing;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.PipelineYamlLoader;

import java.util.List;

/**
 * Loads {@code fishing_pipeline.yml} onto the shared {@link org.nakii.valmora.api.pipeline.HookBus}
 * under the {@code "fishing:<when>"} insertion points: {@code pre_catch} (before the sea-creature
 * roll / loot table roll — an {@code interrupt} here cancels the catch, granting nothing) and
 * {@code post_catch} (after the reward is resolved).
 *
 * <p>Fishing is a low-frequency event (one {@code PlayerFishEvent.State.CAUGHT_FISH} per
 * successful bite, not per tick), so unlike block-breaking or combat there's no meaningful
 * hot-path risk here — see docs/COMBAT_PIPELINE_ANALYSIS.md's per-domain frequency discussion.
 */
public class FishingPipelineLoader {

    static final String POINT_PREFIX = "fishing:";
    static final String PRE_CATCH = POINT_PREFIX + "pre_catch";
    static final String POST_CATCH = POINT_PREFIX + "post_catch";
    private static final List<String> VALID_POINTS = List.of("pre_catch", "post_catch");

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public FishingPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        PipelineYamlLoader.loadFile(plugin, scriptModule, "fishing_pipeline.yml", POINT_PREFIX, VALID_POINTS);
    }
}
