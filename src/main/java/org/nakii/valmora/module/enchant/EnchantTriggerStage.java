package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.PipelineStage;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.script.event.ConditionAbortException;

/**
 * Adapts an {@link EnchantTriggerBlock} onto {@link org.nakii.valmora.api.pipeline.HookBus} as an
 * {@code "enchant:<enchantId>:<trigger>"} stage — an independent copy of {@code
 * module.gui.GuiEventBlockStage}'s exact dispatch shape (conditions gate, actions run catching
 * {@link ConditionAbortException}, fail-actions on either failure), reproduced here rather than
 * reused so {@code module.enchant} stays free of any {@code module.gui} dependency.
 */
public class EnchantTriggerStage implements PipelineStage {

    private final String id;
    private final EnchantTriggerBlock block;

    public EnchantTriggerStage(String id, EnchantTriggerBlock block) {
        this.id = id;
        this.block = block;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public StageResult run(ExecutionContext context) {
        if (block.conditions() != null && !block.conditions().evaluate(context)) {
            runFailActions(context);
            return StageResult.INTERRUPT;
        }
        if (block.actions() != null) {
            try {
                block.actions().execute(context);
            } catch (ConditionAbortException ignored) {
                runFailActions(context);
                return StageResult.INTERRUPT;
            }
        }
        return StageResult.CONTINUE;
    }

    private void runFailActions(ExecutionContext context) {
        if (block.failActions() == null) return;
        try {
            block.failActions().execute(context);
        } catch (ConditionAbortException ignored) {
            // Swallowed, matching GuiEventBlockStage's identical handling.
        }
    }
}
