package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TitleIOTest {

    private final TitleIO io = new TitleIO();

    @Test
    void getName_returnsTitle() {
        assertEquals("title", io.getName());
    }

    @Test
    void singleLineMessage_emptySubtitle() {
        Player player = mock(Player.class);
        io.send(player, "<gold>Level Up!", Map.of());

        verify(player).showTitle(argThat((Title t) ->
                t.subtitle().equals(net.kyori.adventure.text.Component.empty())));
    }

    @Test
    void backslashN_splitsTitleAndSubtitle() {
        Player player = mock(Player.class);
        io.send(player, "<gold>Title\\nSubtitle text", Map.of());

        verify(player).showTitle(argThat((Title t) -> !t.subtitle().equals(net.kyori.adventure.text.Component.empty())));
    }

    @Test
    void defaultTimings_areAppliedWhenNoSettings() {
        Player player = mock(Player.class);
        io.send(player, "Hi", Map.of());

        verify(player).showTitle(argThat((Title t) -> {
            Title.Times times = t.times();
            return times != null
                    && times.fadeIn().equals(Duration.ofMillis(500))   // 10 ticks
                    && times.stay().equals(Duration.ofMillis(3500))    // 70 ticks
                    && times.fadeOut().equals(Duration.ofMillis(1000)); // 20 ticks
        }));
    }

    @Test
    void customTimings_areAppliedFromSettings() {
        Player player = mock(Player.class);
        io.send(player, "Hi", Map.of("fadeIn", "5", "stay", "40", "fadeOut", "10"));

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
        io.send(player, "Hi", Map.of("fadeIn", "abc"));

        verify(player).showTitle(argThat((Title t) -> t.times() != null
                && t.times().fadeIn().equals(Duration.ofMillis(500))));
    }
}
