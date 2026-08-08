package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SoundIOTest {

    private final Plugin plugin = mock(Plugin.class);
    private final SoundIO io = new SoundIO(plugin);

    @Test
    void getName_returnsSound() {
        assertEquals("sound", io.getName());
    }

    @Test
    void noSoundKey_isNoOp() {
        Player player = mock(Player.class);
        io.send(player, "ignored", Map.of());
        verifyNoInteractions(player);
    }

    @Test
    void emptySoundKey_isNoOp() {
        Player player = mock(Player.class);
        io.send(player, "ignored", Map.of("sound", ""));
        verifyNoInteractions(player);
    }

    @Test
    void playsSoundWithDefaultVolumePitchCategory() {
        Player player = mock(Player.class);
        io.send(player, "ignored", Map.of("sound", "block.note_block.pling"));

        // Key.key("block.note_block.pling") gets the default "minecraft" namespace.
        verify(player).playSound(argThat((Sound s) ->
                s.name().asString().equals("minecraft:block.note_block.pling")
                        && s.volume() == 1.0f
                        && s.pitch() == 1.0f
                        && s.source() == Sound.Source.MASTER));
    }

    @Test
    void playsSoundWithCustomVolumePitchCategory() {
        Player player = mock(Player.class);
        io.send(player, "ignored", Map.of(
                "sound", "entity.player.levelup",
                "soundvolume", "0.5",
                "soundpitch", "1.5",
                "soundcategory", "player"));

        verify(player).playSound(argThat((Sound s) ->
                s.volume() == 0.5f && s.pitch() == 1.5f && s.source() == Sound.Source.PLAYER));
    }

    @Test
    void malformedVolumePitch_fallsBackToDefaults() {
        Player player = mock(Player.class);
        io.send(player, "ignored", Map.of("sound", "block.note_block.pling",
                "soundvolume", "abc", "soundpitch", "xyz"));

        verify(player).playSound(argThat((Sound s) -> s.volume() == 1.0f && s.pitch() == 1.0f));
    }

    @Test
    void unknownCategory_fallsBackToMaster() {
        Player player = mock(Player.class);
        io.send(player, "ignored", Map.of("sound", "block.note_block.pling", "soundcategory", "not_a_category"));

        verify(player).playSound(argThat((Sound s) -> s.source() == Sound.Source.MASTER));
    }

    @Test
    void invalidSoundKey_logsWarningAndDoesNotPlaySound() {
        Player player = mock(Player.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SoundIOTest"));

        io.send(player, "ignored", Map.of("sound", "Not A Valid Key!!"));

        verify(player, never()).playSound(any(Sound.class));
    }

    @Test
    void invalidSoundKey_nullPlugin_doesNotThrow() {
        SoundIO noPluginIo = new SoundIO(null);
        Player player = mock(Player.class);
        assertDoesNotThrow(() -> noPluginIo.send(player, "ignored", Map.of("sound", "Not A Valid Key!!")));
        verify(player, never()).playSound(any(Sound.class));
    }
}
