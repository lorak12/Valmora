package org.nakii.valmora.module.combat;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.FlowYamlLoader;

/**
 * Loads {@code combat_pipeline.yml}'s flows ({@link CombatFlows}: {@code entity_hit},
 * {@code environment_damage}) and its legacy {@code stages:}. Stages with {@code when: on_death}
 * still belong here but run at the {@code combat:on_death} anchor of {@code mob_pipeline.yml}'s
 * {@code death} flow. See {@link FlowYamlLoader} for the format and fallback rules.
 */
public class CombatPipelineLoader {

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public CombatPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        FlowYamlLoader.load(plugin, scriptModule, CombatFlows.DOMAIN);
    }
}
