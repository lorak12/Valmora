package org.nakii.valmora.module.mob;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.FlowYamlLoader;

/**
 * Loads {@code mob_pipeline.yml}'s flows ({@link MobFlows}: boss {@code cast}, mob {@code death})
 * and its legacy {@code stages:} (run at the {@code pre_ability}/{@code post_ability} anchors).
 * The {@code death} flow's {@code combat:on_death} anchor runs stages from
 * {@code combat_pipeline.yml}. See {@link FlowYamlLoader} for the format and fallback rules.
 */
public class MobPipelineLoader {

    static final String POINT_PREFIX = MobFlows.POINT_PREFIX;

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public MobPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        FlowYamlLoader.load(plugin, scriptModule, MobFlows.DOMAIN);
    }
}
