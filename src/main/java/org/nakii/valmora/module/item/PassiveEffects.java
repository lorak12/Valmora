package org.nakii.valmora.module.item;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.nakii.valmora.util.Keys;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remembers which infinite ("passive") potion effects Valmora itself put on a player, in the
 * player's PDC so it survives relogs and restarts.
 *
 * <p>Stat recalculation clears passive effects before re-applying the ones current gear grants.
 * It used to strip every effect longer than an hour, including other plugins' permanent effects;
 * now only the ones recorded here are removed.
 */
public final class PassiveEffects {

    private PassiveEffects() {}

    public static void record(LivingEntity entity, PotionEffectType type) {
        if (Keys.PASSIVE_EFFECTS_KEY == null) return; // keys not initialised (unit tests)
        if (!(entity instanceof Player player) || player.getPersistentDataContainer() == null) return;
        Set<String> keys = read(player.getPersistentDataContainer());
        if (keys.add(type.getKey().toString())) write(player.getPersistentDataContainer(), keys);
    }

    /** Removes every recorded passive effect from {@code player} and forgets them. */
    public static void clear(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null || Keys.PASSIVE_EFFECTS_KEY == null) return;
        if (!pdc.has(Keys.PASSIVE_EFFECTS_KEY, PersistentDataType.STRING)) {
            // Never tracked (player predates tracking): fall back once to the old rule so passives
            // applied before the upgrade don't stick forever.
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getDuration() > 20 * 60 * 60 || effect.isInfinite()) player.removePotionEffect(effect.getType());
            }
            pdc.set(Keys.PASSIVE_EFFECTS_KEY, PersistentDataType.STRING, "");
            return;
        }
        for (String key : read(pdc)) {
            NamespacedKey nk = NamespacedKey.fromString(key);
            PotionEffectType type = nk != null ? Registry.POTION_EFFECT_TYPE.get(nk) : null;
            if (type != null) player.removePotionEffect(type);
        }
        pdc.set(Keys.PASSIVE_EFFECTS_KEY, PersistentDataType.STRING, ""); // present = tracked
    }

    private static Set<String> read(PersistentDataContainer pdc) {
        Set<String> keys = new LinkedHashSet<>();
        String raw = pdc.get(Keys.PASSIVE_EFFECTS_KEY, PersistentDataType.STRING);
        if (raw != null && !raw.isEmpty()) {
            for (String k : raw.split(",")) if (!k.isBlank()) keys.add(k);
        }
        return keys;
    }

    private static void write(PersistentDataContainer pdc, Set<String> keys) {
        pdc.set(Keys.PASSIVE_EFFECTS_KEY, PersistentDataType.STRING, String.join(",", keys));
    }
}
