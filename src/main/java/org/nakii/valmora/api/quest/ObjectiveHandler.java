package org.nakii.valmora.api.quest;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.QuestManager;

/**
 * Registers a custom (or built-in) objective type with the quest engine.
 *
 * External plugins implement this interface and register it via
 * {@link QuestManager#registerObjectiveHandler(ObjectiveHandler)}.
 * When a quest starts the engine calls {@link #onQuestStart} for every
 * objective whose {@code type} field matches {@link #getTypeId()}.
 *
 * Triggering progress from game events is handled separately by calling
 * {@link QuestManager#trigger(Player, String, String, int)} from any
 * Bukkit listener.
 */
public interface ObjectiveHandler {

    /** Lowercase type ID that matches the {@code type:} field in YAML, e.g. {@code "delay"}. */
    String getTypeId();

    /**
     * Called for each matching objective when the owning quest starts.
     * Use this to schedule timers, start trackers, etc.
     * Default implementation is a no-op.
     */
    default void onQuestStart(Player player, QuestObjective objective, QuestManager questManager) {}

    /**
     * Called for each matching, not-yet-complete objective of an in-progress quest when a
     * player's session (re)starts: on profile load, and for everyone online after the quest module
     * reloads. Handlers that schedule tasks must re-create them here. Tasks don't survive a
     * restart, reload or relog, so without this, timed objectives stall forever.
     *
     * @param progress        the objective's saved progress
     * @param startedAtMillis when the objective started (epoch ms), or 0 if unknown (saved before
     *                        start times were recorded)
     */
    default void onResume(Player player, QuestObjective objective, QuestManager questManager,
                          int progress, long startedAtMillis) {}

    /** Called when a player quits; cancel anything scheduled for them. */
    default void onPlayerQuit(Player player) {}

    /** Called when the quest module disables; cancel everything scheduled. */
    default void cancelAll() {}
}
