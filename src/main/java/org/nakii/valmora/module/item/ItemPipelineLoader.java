package org.nakii.valmora.module.item;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.pipeline.PipelineYamlLoader;

import java.util.List;

/**
 * Loads {@code item_pipeline.yml} onto the shared {@link org.nakii.valmora.api.pipeline.HookBus}
 * under the {@code "item:<when>"} insertion points: {@code pre_ability} (after an item ability's
 * own conditions/cooldown/mana gate passes, before its mechanics run — an {@code interrupt} here
 * cancels just that firing, mechanics don't run, but the cooldown/mana already consumed above stays
 * consumed) and {@code post_ability} (after mechanics run).
 *
 * <p>Supplementary to — not a replacement for — {@link AbilityExecutor}'s native trigger/condition/
 * cooldown/mana machinery, which stays exactly as it was. See docs/VALMORA_DOCUMENTATION.md §39.
 */
public class ItemPipelineLoader {

    static final String POINT_PREFIX = "item:";
    private static final List<String> VALID_POINTS = List.of("pre_ability", "post_ability");

    private final Valmora plugin;
    private final ScriptModule scriptModule;

    public ItemPipelineLoader(Valmora plugin, ScriptModule scriptModule) {
        this.plugin = plugin;
        this.scriptModule = scriptModule;
    }

    public void load() {
        PipelineYamlLoader.loadFile(plugin, scriptModule, "item_pipeline.yml", POINT_PREFIX, VALID_POINTS);
    }
}
