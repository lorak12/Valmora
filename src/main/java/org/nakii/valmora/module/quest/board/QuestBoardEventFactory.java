package org.nakii.valmora.module.quest.board;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.List;

/**
 * DSL events for the quest-board flow:
 * <pre>
 *   quest_board_assign &lt;boardId&gt;
 *   quest_board_collect &lt;boardId&gt; &lt;slot&gt;
 * </pre>
 */
public class QuestBoardEventFactory {

    private final QuestBoardManager manager;

    public QuestBoardEventFactory(QuestBoardManager manager) {
        this.manager = manager;
    }

    public List<EventFactory> all() {
        return List.of(new Assign(), new Collect());
    }

    private class Assign implements EventFactory {
        @Override public String getName() { return "quest_board_assign"; }

        @Override
        public CompiledEvent compile(String[] args, EventOptions options) {
            if (args.length < 1) return ctx -> {};
            String boardId = args[0];
            return ctx -> ctx.getPlayerCaster().ifPresent(player -> manager.assignIfEmpty(player, boardId));
        }
    }

    private class Collect implements EventFactory {
        @Override public String getName() { return "quest_board_collect"; }

        @Override
        public CompiledEvent compile(String[] args, EventOptions options) {
            if (args.length < 2) return ctx -> {};
            String boardId = args[0];
            int slot;
            try { slot = Integer.parseInt(args[1]); } catch (NumberFormatException e) { return ctx -> {}; }
            return ctx -> ctx.getPlayerCaster().ifPresent(player -> manager.collect(player, boardId, slot));
        }
    }
}
