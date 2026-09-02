package org.nakii.valmora.module.death;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.PipelineYamlLoader;

import java.util.List;

/**
 * Loads {@code death_pipeline.yml} onto the shared {@link org.nakii.valmora.api.pipeline.HookBus}
 * under the {@code "player:<when>"} insertion points. Kept separate from {@code combat:on_death}
 * (which {@code MobVariableProvider}'s docs tie specifically to mob deaths, with {@code mob:level}
 * context) — this is the player-death/respawn equivalent. See {@link PipelineYamlLoader} for the
 * shared stage-compiling logic and exact YAML format.
 */
public class DeathPipelineLoader {

    private static final String POINT_PREFIX = "player:";
    private static final List<String> VALID_POINTS = List.of("on_death", "on_respawn");

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public DeathPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        PipelineYamlLoader.loadFile(plugin, scriptModule, "death_pipeline.yml", POINT_PREFIX, VALID_POINTS);
    }
}
