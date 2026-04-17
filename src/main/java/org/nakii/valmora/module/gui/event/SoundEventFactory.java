package org.nakii.valmora.module.gui.event;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

public class SoundEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "sound";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length == 0) return context -> {};
        
        try {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.fromString(args[0].toUpperCase()));
            return context -> context.getPlayerCaster().ifPresent(player -> 
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f));
        } catch (IllegalArgumentException e) {
            return context -> {};
        }
    }
}
