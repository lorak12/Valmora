package org.nakii.valmora.module.resource;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.FlowYamlLoader;

/**
 * Loads {@code resource_pipeline.yml}'s flows ({@link ResourceFlows}: {@code break} and the
 * mine/denied/regen feedback cues) and its legacy {@code stages:} (run at the
 * {@code pre_break}/{@code post_break} anchors). See {@link FlowYamlLoader} for the format and
 * fallback rules.
 *
 * <p>Scoped to <b>zone-configured resource blocks only</b> — {@code resolve_resource} stops the
 * flow for every other block before anything else runs, so ordinary building/digging pays for one
 * zone lookup, as before.
 */
public class ResourcePipelineLoader {

    static final String POINT_PREFIX = ResourceFlows.POINT_PREFIX;

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public ResourcePipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        FlowYamlLoader.load(plugin, scriptModule, ResourceFlows.DOMAIN);
    }
}
