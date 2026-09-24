package org.nakii.valmora.module.quest.pkg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.npc.dialogue.DialogueDefinition;
import org.nakii.valmora.module.npc.dialogue.DialogueNode;
import org.nakii.valmora.module.quest.QuestDefinition;
import org.nakii.valmora.module.quest.QuestObjective;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers two quest-package parsing fixes: named package conditions resolving inside objective
 * {@code conditions:} (both the one-line DSL and the structured map form), and a conversation's
 * player option firing its own {@code events} even when it has no {@code pointers}.
 */
public class QuestPackageManagerTest {

    @TempDir
    File dataFolder;

    private QuestPackageManager manager;

    @BeforeEach
    void setUp() throws IOException {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("QuestPackageManagerTest"));

        File pkgDir = new File(dataFolder, "quests/village");
        pkgDir.mkdirs();
        Files.writeString(new File(pkgDir, "quest.yml").toPath(), """
                package:
                  enabled: true
                conditions:
                  in_mine: "zone mine"
                  is_hunter: "tag hunter"
                """);
        Files.writeString(new File(pkgDir, "quests.yml").toPath(), """
                quests:
                  wolves:
                    objectives:
                      structured:
                        type: kill
                        target: WOLF
                        amount: 5
                        conditions: "in_mine"
                      dsl: "kill WOLF 5 conditions:!in_mine,is_hunter"
                """);
        Files.writeString(new File(pkgDir, "conversations.yml").toPath(), """
                conversations:
                  elder:
                    first: [greeting]
                    NPC_options:
                      greeting:
                        text: "Will you help?"
                        pointers: [accept]
                    player_options:
                      accept:
                        text: "Yes."
                        events: "quest_start wolves"
                """);

        manager = new QuestPackageManager(plugin);
        manager.loadAll();
    }

    private QuestPackage pkg() {
        assertEquals(1, manager.getPackages().size());
        return manager.getPackages().get(0);
    }

    private QuestObjective objective(String id) {
        QuestDefinition quest = pkg().getQuests().get("wolves");
        return quest.getObjectives().stream().filter(o -> id.equals(o.getId())).findFirst().orElseThrow();
    }

    @Test
    void structuredObjectiveResolvesNamedCondition() {
        assertEquals(List.of("zone mine"), objective("structured").getConditions());
    }

    @Test
    void dslObjectiveResolvesNegatedAndPlainNamedConditions() {
        assertEquals(List.of("!zone mine", "tag hunter"), objective("dsl").getConditions());
    }

    @Test
    void playerOptionWithoutPointersStillCarriesItsEvents() {
        DialogueDefinition conversation = pkg().getConversations().get("elder");
        DialogueNode accept = conversation.getNode("player.accept").orElseThrow();
        assertEquals(List.of("quest_start wolves"), accept.getEvents());
        assertTrue(accept.getChoices().isEmpty());
    }
}
