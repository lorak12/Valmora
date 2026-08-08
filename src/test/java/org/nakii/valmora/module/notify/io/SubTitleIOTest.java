package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTitleIOTest {

    private final SubTitleIO io = new SubTitleIO();

    @Test
    void getName_returnsSubtitle() {
        assertEquals("subtitle", io.getName());
    }

    @Test
    void send_emptyMainTitleWithMessageAsSubtitle() {
        Player player = mock(Player.class);
        io.send(player, "<gray>details here", Map.of());

        verify(player).showTitle(argThat((Title t) ->
                t.title().equals(net.kyori.adventure.text.Component.empty())
                        && !t.subtitle().equals(net.kyori.adventure.text.Component.empty())));
    }

    @Test
    void customTimings_areAppliedFromSettings() {
        Player player = mock(Player.class);
        io.send(player, "hi", Map.of("fadeIn", "5", "stay", "40", "fadeOut", "10"));

        verify(player).showTitle(argThat((Title t) -> {
            Title.Times times = t.times();
            return times != null
                    && times.fadeIn().equals(Duration.ofMillis(250))
                    && times.stay().equals(Duration.ofMillis(2000))
                    && times.fadeOut().equals(Duration.ofMillis(500));
        }));
    }

    @Test
    void malformedTimings_fallBackToDefaults() {
        Player player = mock(Player.class);
        io.send(player, "hi", Map.of("stay", "notanumber"));

        verify(player).showTitle(argThat((Title t) -> t.times() != null
                && t.times().stay().equals(Duration.ofMillis(3500))));
    }
}
