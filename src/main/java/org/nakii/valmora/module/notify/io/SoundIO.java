package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.nakii.valmora.module.notify.NotifyIO;

import java.util.Map;

public class SoundIO implements NotifyIO {
    @Override public String getName() { return "sound"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        String soundKey = settings.get("sound");
        if (soundKey == null || soundKey.isEmpty()) return;
        float volume = parseFloat(settings.get("soundvolume"), 1.0f);
        float pitch = parseFloat(settings.get("soundpitch"), 1.0f);
        Sound.Source category = parseCategory(settings.getOrDefault("soundcategory", "MASTER"));
        player.playSound(Sound.sound(Key.key(soundKey), category, volume, pitch));
    }

    private float parseFloat(String s, float def) {
        if (s == null) return def;
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }

    private Sound.Source parseCategory(String s) {
        try { return Sound.Source.valueOf(s.toUpperCase()); } catch (Exception e) { return Sound.Source.MASTER; }
    }
}
