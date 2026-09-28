package org.nakii.valmora.module.item;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.FlowYamlLoader;

/**
 * Loads {@code item_pipeline.yml}'s {@code cast} flow ({@link AbilityFlows}) and its legacy
 * {@code stages:} (run at the {@code pre_ability}/{@code post_ability} anchors). See
 * {@link FlowYamlLoader} for the format and fallback rules.
 */
public class ItemPipelineLoader {

    static final String POINT_PREFIX = AbilityFlows.POINT_PREFIX;

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public ItemPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        FlowYamlLoader.load(plugin, scriptModule, AbilityFlows.DOMAIN);
    }
}
