package org.nakii.valmora.module.gui;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.PipelineStage;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.script.event.ConditionAbortException;

/**
 * Adapts a {@link GuiEventBlock} (the {@code on-open}/{@code on-close}/{@code on-slot-update}/
 * {@code on-update} blocks — see CLAUDE.md §8.2) onto {@link org.nakii.valmora.api.pipeline.HookBus}
 * as a {@code "gui:<guiId>:<point>"} stage, so GUI lifecycle dispatch reuses the same shared bus
 * combat/resource/fishing do instead of the bespoke inline dispatch that used to live in
 * {@code GuiModule}/{@code GuiListener} (three near-identical copies of
 * "evaluate conditions, execute actions catching {@link ConditionAbortException}, fall back to
 * fail-actions").
 *
 * <p>Deliberately <b>not</b> a {@link org.nakii.valmora.api.pipeline.CompiledPipelineStage}: GUI
 * event blocks have their own long-standing semantics that predate the generic pipeline stage
 * contract (condition group is optional/nullable, and a failed condition or an aborted action list
 * both mean "the block's own actions did not run") — this class reproduces that byte-for-byte
 * instead of forcing it into the generic pass/continue shape. It reports
 * {@link StageResult#INTERRUPT} exactly when the legacy dispatch code would have taken its
 * "fail" branch; callers that used to unconditionally continue afterward (on-close/on-update/
 * on-slot-update) simply ignore {@link org.nakii.valmora.api.pipeline.HookBus#runPoint}'s return
 * value, while the one caller that used to {@code return} early (on-open, aborting the GUI open)
 * now does {@code if (!hookBus.runPoint(...)) return;} — same observable behavior either way.
 */
public class GuiEventBlockStage implements PipelineStage {

    private final String id;
    private final GuiEventBlock block;

    public GuiEventBlockStage(String id, GuiEventBlock block) {
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
            // A `condition` event inside fail-actions itself aborting is treated the same as the
            // legacy inline dispatch did everywhere it appeared: swallowed, nothing further runs.
        }
    }
}
