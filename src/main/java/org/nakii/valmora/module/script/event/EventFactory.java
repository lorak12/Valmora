package org.nakii.valmora.module.script.event;

import org.nakii.valmora.api.scripting.CompiledEvent;

/**
 * Factory for creating compiled events from DSL arguments.
 *
 * <p>Implementations should report bad arguments through
 * {@code org.nakii.valmora.infrastructure.config.diag.Diagnostics} (it attributes the problem to
 * the file/entry being loaded) and then return a no-op, rather than failing silently.
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

    /** Fewest positional arguments this event accepts — checked (with {@link #usage()}) before {@link #compile}. */
    default int minArgs() {
        return 0;
    }

    /** Most positional arguments this event accepts. */
    default int maxArgs() {
        return Integer.MAX_VALUE;
    }

    /** One-line usage shown when the arguments are wrong, e.g. {@code "give <item>[:amount]"}. */
    default String usage() {
        return getName() + " ...";
    }

    /**
     * Reports the content ids these arguments refer to (e.g. {@code open_gui shop} → gui "shop"),
     * so they're checked once all content has loaded. Called at compile time.
     */
    default void references(String[] args, ReferenceSink sink) {
    }

    /** Receives {@code (kind, id)} references — see {@code org.nakii.valmora.infrastructure.config.refs.Kinds}. */
    @FunctionalInterface
    interface ReferenceSink {
        void ref(String kind, String id);
    }
}
