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
}
