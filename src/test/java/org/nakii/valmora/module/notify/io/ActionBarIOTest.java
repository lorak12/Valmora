package org.nakii.valmora.module.notify.io;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionBarIOTest {

    private final ActionBarIO io = new ActionBarIO();

    @Test
    void getName_returnsActionbar() {
        assertEquals("actionbar", io.getName());
    }

    @Test
    void send_formatsMessageAndSendsActionBar() {
        Player player = mock(Player.class);
        io.send(player, "<yellow>Low health!", Map.of());
        verify(player).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }
}
