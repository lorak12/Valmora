package org.nakii.valmora.module.mob;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.PipelineYamlLoader;

import java.util.List;

/**
 * Loads {@code mob_pipeline.yml} onto the shared {@link org.nakii.valmora.api.pipeline.HookBus}
 * under the {@code "mob:<when>"} insertion points: {@code pre_ability} (after a boss ability's own
 * cooldown/interval/chance gate passes, before its mechanics run — an {@code interrupt} here
 * cancels just that firing, e.g. a "silence" debuff) and {@code post_ability} (after mechanics run).
 *
 * <p>Supplementary to — not a replacement for — {@link BossController}'s native
 * timer/cooldown/announce machinery, which stays exactly as it was. See
 * docs/VALMORA_DOCUMENTATION.md §39.
 */
public class MobPipelineLoader {

    static final String POINT_PREFIX = "mob:";
    private static final List<String> VALID_POINTS = List.of("pre_ability", "post_ability");

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public MobPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        PipelineYamlLoader.loadFile(plugin, scriptModule, "mob_pipeline.yml", POINT_PREFIX, VALID_POINTS);
    }
}
