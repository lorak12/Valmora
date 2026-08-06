package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.nakii.valmora.module.notify.NotifyIO;

import java.util.Map;
import java.util.logging.Level;

public class SoundIO implements NotifyIO {

    private final Plugin plugin;

    public SoundIO(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "sound"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        String soundKey = settings.get("sound");
        if (soundKey == null || soundKey.isEmpty()) return;
        float volume = parseFloat(settings.get("soundvolume"), 1.0f);
        float pitch = parseFloat(settings.get("soundpitch"), 1.0f);
        Sound.Source category = parseCategory(settings.getOrDefault("soundcategory", "MASTER"));

        Key key;
        try {
            key = Key.key(soundKey);
        } catch (InvalidKeyException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "SoundIO: invalid sound key '" + soundKey + "' — " + e.getMessage());
            }
            return;
        }
        player.playSound(Sound.sound(key, category, volume, pitch));
    }

    private float parseFloat(String s, float def) {
        if (s == null) return def;
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }

    private Sound.Source parseCategory(String s) {
        try { return Sound.Source.valueOf(s.toUpperCase()); } catch (Exception e) { return Sound.Source.MASTER; }
    }
}
