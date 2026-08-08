package org.nakii.valmora.module.notify.io;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatIOTest {

    private final ChatIO io = new ChatIO();

    @Test
    void getName_returnsChat() {
        assertEquals("chat", io.getName());
    }

    @Test
    void send_formatsMessageAndSendsToPlayer() {
        Player player = mock(Player.class);
        io.send(player, "<red>Hello", Map.of());
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
