package org.nakii.valmora.module.notify;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link NotifyManager}'s IO/category resolution — previously untested (see
 * docs/IMPLEMENTATION_BACKLOG.md "Add unit tests for untested modules" / notify item).
 */
class NotifyManagerTest {

    private NotifyManager manager;
    private NotifyIO chatIo;
    private NotifyIO actionBarIo;
    private Player player;

    @BeforeEach
    void setUp() {
        manager = new NotifyManager();

        chatIo = mock(NotifyIO.class);
        when(chatIo.getName()).thenReturn("chat");
        actionBarIo = mock(NotifyIO.class);
        when(actionBarIo.getName()).thenReturn("actionbar");
        manager.registerIO(chatIo);
        manager.registerIO(actionBarIo);

        player = mock(Player.class);
    }

    @Test
    void send_explicitIoName_takesPriorityOverCategory() {
        manager.send(player, "hi", "actionbar", "info", null); // category "info" defaults to chat
        verify(actionBarIo).send(eq(player), eq("hi"), anyMap());
        verify(chatIo, never()).send(any(), any(), anyMap());
    }

    @Test
    void send_noExplicitIo_usesBuiltinCategoryDefault() {
        manager.send(player, "hi", null, "error", null); // builtin "error" -> actionbar
        verify(actionBarIo).send(eq(player), eq("hi"), anyMap());
    }

    @Test
    void send_noIoOrCategory_defaultsToChat() {
        manager.send(player, "hi", null, null, null);
        verify(chatIo).send(eq(player), eq("hi"), anyMap());
    }

    @Test
    void send_customCategory_resolvesRegisteredIo() {
        manager.loadCategory("quest_complete", Map.of("io", "actionbar"));
        manager.send(player, "done", null, "quest_complete", null);
        verify(actionBarIo).send(eq(player), eq("done"), anyMap());
    }

    @Test
    void send_unknownIoName_fallsBackToChat() {
        manager.send(player, "hi", "totally_bogus", null, null);
        verify(chatIo).send(eq(player), eq("hi"), anyMap());
    }

    @Test
    void send_perCallOverridesWinOverCategoryDefaults() {
        manager.loadCategory("custom", Map.of("io", "chat", "sound", "block.note.pling"));
        manager.send(player, "hi", null, "custom", Map.of("io", "actionbar"));
        verify(actionBarIo).send(eq(player), eq("hi"), anyMap());
    }

    @Test
    void send_settingsMapPassedThroughToIo() {
        manager.loadCategory("custom", Map.of("io", "chat", "barColor", "RED"));
        manager.send(player, "hi", null, "custom", Map.of("extra", "value"));

        verify(chatIo).send(eq(player), eq("hi"), argThat(settings ->
                "RED".equals(settings.get("barColor")) && "value".equals(settings.get("extra"))));
    }

    @Test
    void sendCategory_delegatesToSendWithNoOverridesOrExplicitIo() {
        manager.loadCategory("info2", Map.of("io", "actionbar"));
        manager.sendCategory(player, "hi", "info2");
        verify(actionBarIo).send(eq(player), eq("hi"), anyMap());
    }

    @Test
    void categoryLookup_isCaseInsensitive() {
        manager.loadCategory("MyCategory", Map.of("io", "actionbar"));
        manager.send(player, "hi", null, "mycategory", null);
        verify(actionBarIo).send(eq(player), eq("hi"), anyMap());
    }

    @Test
    void noIoRegisteredAtAll_isNoOp() {
        NotifyManager empty = new NotifyManager();
        assertDoesNotThrow(() -> empty.send(player, "hi", null, null, null));
    }
}
