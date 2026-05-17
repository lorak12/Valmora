package org.nakii.valmora.module.quest;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.List;

/**
 * Provides all quest-related script events:
 *   quest_start, quest_complete, quest_cancel, quest_fail,
 *   objective_start, objective_delete
 */
public class QuestEventFactory {

    private final QuestManager questManager;

    public QuestEventFactory(QuestManager questManager) {
        this.questManager = questManager;
    }

    public List<EventFactory> all() {
        return List.of(
            factory("quest_start",    (player, args) -> questManager.startQuest(player, args[0])),
            factory("quest_complete", (player, args) -> questManager.completeQuest(player, args[0])),
            factory("quest_cancel",   (player, args) -> questManager.cancelQuest(player, args[0])),
            factory("quest_fail",     (player, args) -> questManager.failQuest(player, args[0])),
            factory("objective_start",  (player, args) -> questManager.startObjective(player, args[0])),
            factory("objective_delete", (player, args) -> questManager.deleteObjective(player, args[0]))
        );
    }

    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player, String[] args);
    }

    private EventFactory factory(String name, PlayerAction action) {
        return new EventFactory() {
            @Override public String getName() { return name; }

            @Override
            public CompiledEvent compile(String[] args, EventOptions options) {
                if (args.length < 1) return ctx -> {};
                return ctx -> ctx.getPlayerCaster().ifPresent(entity -> {
                    if (entity instanceof Player player) action.run(player, args);
                });
            }
        };
    }
}
