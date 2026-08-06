package org.nakii.valmora.module.script.event.impl;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Marks the current context so any {@link org.nakii.valmora.api.pipeline.CompiledPipelineStage}
 * running it returns {@link org.nakii.valmora.api.pipeline.StageResult#INTERRUPT} after this
 * event runs, stopping the remaining stages at the current {@code HookBus} insertion point.
 * DSL: {@code interrupt}
 */
public class InterruptEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "interrupt";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> context.set("pipeline:interrupted", Boolean.TRUE);
    }
}
