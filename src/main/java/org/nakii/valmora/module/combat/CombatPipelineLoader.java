package org.nakii.valmora.module.combat;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.PipelineYamlLoader;

import java.util.List;

/**
 * Loads {@code combat_pipeline.yml} onto the shared {@link org.nakii.valmora.api.pipeline.HookBus}
 * under the {@code "combat:<when>"} insertion points. This is the MVP scope from
 * docs/COMBAT_PIPELINE_ANALYSIS.md §9.1: {@code pre_damage}, {@code post_calculation},
 * {@code post_application}, {@code on_dmg_dealt}, {@code on_death}.
 *
 * <p>The file is entirely optional — if it doesn't exist, or has an empty/missing
 * {@code stages:} list, no stages are registered and {@link CombatListener} /
 * {@link org.nakii.valmora.module.mob.MobDeathListener} skip the pipeline entirely
 * (see {@link org.nakii.valmora.api.pipeline.HookBus#hasStages}), so a default install pays zero
 * cost for this feature. See {@link PipelineYamlLoader} for the shared stage-compiling logic and
 * exact YAML format.
 */
public class CombatPipelineLoader {

    private static final String POINT_PREFIX = "combat:";
    private static final List<String> VALID_POINTS = List.of(
            "pre_damage", "post_calculation", "post_application", "on_dmg_dealt", "on_death"
    );

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public CombatPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        PipelineYamlLoader.loadFile(plugin, scriptModule, "combat_pipeline.yml", POINT_PREFIX, VALID_POINTS);
    }
}
