package org.nakii.valmora.module.enchant.state;

/**
 * Parsed {@code state.transient.<key>:} entry — a per-attacker, in-memory (never PDC-persisted)
 * counter tracked by {@link TransientStateTracker}, backing things like Lethality's stack count or
 * First Strike's combo counter.
 *
 * @param resetAfterSeconds seconds of inactivity before the counter resets to 0 on next read/mutate;
 *                           {@code 0} means never expires on its own (only {@code reset-on-target-switch}
 *                           or an explicit {@code enchant_state reset} can clear it).
 * @param resetOnTargetSwitch if true, the counter resets to 0 the moment the tracked attacker's
 *                            target changes — this (plus per-attacker keying, see
 *                            {@link TransientStateTracker}) is the fix for the pre-overhaul
 *                            Lethality logic sharing one stack count across every attacker of the
 *                            same victim.
 * @param maxStacks the highest value the counter is clamped to; {@code 0} means unlimited.
 */
public record TransientStateDefinition(long resetAfterSeconds, boolean resetOnTargetSwitch, int maxStacks) {
}
