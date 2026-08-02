package org.nakii.valmora.module.quest.board;

import org.nakii.valmora.api.registry.SimpleRegistry;

import java.util.Optional;

public class QuestBoardRegistry extends SimpleRegistry<QuestBoardDefinition> {

    public void registerBoard(QuestBoardDefinition definition) {
        register(definition.getId(), definition);
    }

    public Optional<QuestBoardDefinition> getBoard(String id) {
        return get(id);
    }
}
