package org.nakii.valmora.module.script.compile;

/**
 * Bumped whenever some modules reload while others stay up (a content pack reload). Scripts
 * compiled at load time may have bound to event factories of a module that just reloaded, so
 * {@link CompiledScript}/{@link CompiledConditions} recompile lazily when the epoch moved on.
 */
public final class ScriptEpoch {

    private static volatile int current;

    private ScriptEpoch() {}

    public static int current() {
        return current;
    }

    public static void bump() {
        current++;
    }
}
