package org.nakii.valmora.module.script.event;

/**
 * Common execution options for events (delay, notifyPlayer, etc.).
 */
public record EventOptions(int delay, boolean notifyPlayer) {
    public static final EventOptions DEFAULT = new EventOptions(0, false);
}
