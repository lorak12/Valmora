package org.nakii.valmora.module.quest.board;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.quest.QuestDefinition;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the "always 2 active quests, collect at the NPC to get rewards + a new random quest"
 * flow. Objective progress itself is tracked entirely by the existing {@link QuestManager} (the
 * quest ids in the pool are plain {@link QuestDefinition}s); this class only owns slot
 * assignment and the collect-to-reroll transition.
 */
public class QuestBoardManager {

    private final Valmora plugin;
    private final QuestBoardRegistry registry;

    public QuestBoardManager(Valmora plugin, QuestBoardRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public QuestBoardRegistry getRegistry() { return registry; }

    /** Fills any empty slot for this board with a fresh random quest from the pool. */
    public void assignIfEmpty(Player player, String boardId) {
        QuestBoardDefinition board = registry.getBoard(boardId).orElse(null);
        if (board == null) return;
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        for (int i = 1; i <= board.getSlots(); i++) {
            String key = slotKey(boardId, i);
            Object current = profile.getVariables().get(key);
            if (current != null && !String.valueOf(current).isEmpty()) continue;
            assignSlot(player, profile, board, boardId, i);
        }
    }

    /** Collects the reward for a completed slot, then rerolls a new quest into that slot. */
    public void collect(Player player, String boardId, int slot) {
        QuestBoardDefinition board = registry.getBoard(boardId).orElse(null);
        if (board == null) return;
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        String key = slotKey(boardId, slot);
        Object questIdObj = profile.getVariables().get(key);
        if (questIdObj == null) return;
        String questId = String.valueOf(questIdObj);

        QuestManager qm = plugin.getQuestManager();
        if (qm == null) return;
        if (!qm.getStatus(profile, questId).equals(QuestManager.STATUS_COMPLETED)) return;

        QuestDefinition quest = qm.getRegistry().get(questId).orElse(null);
        if (quest != null && !quest.getRewardEvents().isEmpty()) {
            var ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            plugin.getScriptModule().getEventParser().parseList(quest.getRewardEvents()).execute(ctx);
        }

        qm.resetQuestProgress(profile, questId);
        profile.getVariables().remove(key);
        player.sendMessage(Formatter.format("<gold>Collected rewards for: " + (quest != null ? quest.getName() : questId)));

        assignSlot(player, profile, board, boardId, slot);
    }

    private void assignSlot(Player player, ValmoraProfile profile, QuestBoardDefinition board, String boardId, int slot) {
        QuestManager qm = plugin.getQuestManager();
        if (qm == null) return;

        List<String> occupied = new ArrayList<>();
        for (int i = 1; i <= board.getSlots(); i++) {
            if (i == slot) continue;
            Object v = profile.getVariables().get(slotKey(boardId, i));
            if (v != null) occupied.add(String.valueOf(v));
        }

        List<String> candidates = new ArrayList<>(board.getPool());
        candidates.removeAll(occupied);
        if (candidates.isEmpty()) candidates = new ArrayList<>(board.getPool());
        if (candidates.isEmpty()) return;

        String chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        profile.getVariables().put(slotKey(boardId, slot), chosen);
        qm.startQuest(player, chosen);
    }

    private String slotKey(String boardId, int slot) {
        return "questboard." + boardId + ".slot." + slot;
    }

    private ValmoraProfile getProfile(Player player) {
        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        return vp != null ? vp.getActiveProfile() : null;
    }
}
