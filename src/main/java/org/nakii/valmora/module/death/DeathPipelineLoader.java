package org.nakii.valmora.module.death;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.FlowYamlLoader;

/**
 * Loads {@code death_pipeline.yml}'s flows ({@link DeathFlows}: {@code death}, {@code respawn})
 * and its legacy {@code stages:} (run at the {@code player:on_death}/{@code player:on_respawn}
 * anchors). Kept separate from {@code combat:on_death}, which is about custom mob deaths. See
 * {@link FlowYamlLoader} for the format and fallback rules.
 */
public class DeathPipelineLoader {

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public DeathPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        FlowYamlLoader.load(plugin, scriptModule, DeathFlows.DOMAIN);
    }
}
