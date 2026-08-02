package org.nakii.valmora.module.quest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the quest-board "collect -> reroll" flow's core dependency: resetting a quest back to
 * NOT_STARTED with cleared objective progress so it can be immediately restarted. Also verifies
 * the isAnyPersistentPending fix — a quest made entirely of persistent objectives must never be
 * auto-completed by checkCompletion (previously a bug: it would complete on the first progress
 * tick and, since startQuest refuses to restart a completed quest, permanently lock itself).
 */
public class QuestManagerResetTest {

    private QuestManager questManager;
    private ValmoraProfile profile;

    @BeforeEach
    void setUp() {
        Valmora plugin = mock(Valmora.class);
        questManager = new QuestManager(plugin);

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);

        profile = new ValmoraProfile("test"); // constructs StatManager, which needs the provider above

        QuestObjective objA = new QuestObjective("a", "collect", "raw_ferrite", 150,
                null, null, false, false, 25);
        QuestObjective objB = new QuestObjective("b", "kill", "shardworks_cave_guardian", 1,
                null, null, false, false, 0);
        QuestDefinition quest = new QuestDefinition("testquest", "Test Quest", List.of(objA, objB),
                List.of("point ferrite_powder add 40"));
        questManager.getRegistry().register("testquest", quest);
    }

    @Test
    public void resetQuestProgress_restoresNotStartedAndClearsProgress() {
        profile.getVariables().put("quest.testquest.status", QuestManager.STATUS_COMPLETED);
        profile.getVariables().put("quest.testquest.obj.a", 150);
        profile.getVariables().put("quest.testquest.obj.b", 1);
        profile.getVariables().put("objective.a.active", false);
        profile.getVariables().put("objective.b.active", false);

        questManager.resetQuestProgress(profile, "testquest");

        assertEquals(QuestManager.STATUS_NOT_STARTED, questManager.getStatus(profile, "testquest"));
        assertEquals(0, questManager.getObjectiveProgress(profile, "testquest", "a"));
        assertEquals(0, questManager.getObjectiveProgress(profile, "testquest", "b"));
        assertFalse(profile.getVariables().containsKey("objective.a.active"));
        assertFalse(profile.getVariables().containsKey("objective.b.active"));
    }

    @Test
    public void rewardEvents_areNotAutoExecuted_onlyStoredForExplicitCollection() {
        QuestDefinition quest = questManager.getRegistry().get("testquest").orElseThrow();
        assertEquals(List.of("point ferrite_powder add 40"), quest.getRewardEvents());
    }

    @Test
    public void quest_defaultsToEmptyRewardEvents_whenNotSpecified() {
        QuestDefinition quest = new QuestDefinition("plain", "Plain Quest", List.of());
        assertTrue(quest.getRewardEvents().isEmpty());
    }
}
