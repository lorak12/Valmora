package org.nakii.valmora.module.script.event;

import org.nakii.valmora.api.scripting.CompiledEvent;

/**
 * Factory for creating compiled events from DSL arguments.
 */
public interface EventFactory {

    /**
     * @return the name of the event (e.g., "give", "tag")
     */
    String getName();

    /**
     * Compiles the event from the given arguments.
     * @param args the arguments from the DSL
     * @param options the execution options (delay, notify, etc.)
     * @return a compiled event
     */
    CompiledEvent compile(String[] args, EventOptions options);
}
