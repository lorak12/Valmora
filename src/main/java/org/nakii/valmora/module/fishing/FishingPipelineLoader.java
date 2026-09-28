package org.nakii.valmora.module.fishing;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.fishing.flow.FishingFlows;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.FlowYamlLoader;

/**
 * Loads {@code fishing_pipeline.yml}'s flows ({@link FishingFlows}: {@code catch}, {@code bite})
 * and its legacy {@code stages:} (run at the {@code pre_catch}/{@code post_catch} anchors). See
 * {@link FlowYamlLoader} for the format and fallback rules.
 */
public class FishingPipelineLoader {

    static final String POINT_PREFIX = FishingFlows.POINT_PREFIX;

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public FishingPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        FlowYamlLoader.load(plugin, scriptModule, FishingFlows.DOMAIN);
    }
}
