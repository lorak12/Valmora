package org.nakii.valmora.module.quest.board;

import java.util.List;

public class QuestBoardDefinition {
    private final String id;
    private final int slots;
    private final List<String> pool;

    public QuestBoardDefinition(String id, int slots, List<String> pool) {
        this.id = id;
        this.slots = slots;
        this.pool = pool != null ? pool : List.of();
    }

    public String getId() { return id; }
    public int getSlots() { return slots; }
    public List<String> getPool() { return pool; }
}
