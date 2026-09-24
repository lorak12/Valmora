package org.nakii.valmora.module.item;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.rarity.RarityDefinition;
import org.nakii.valmora.module.rarity.RarityModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Rarity display data for items, read live from {@code rarities.yml} (the {@link RarityModule}
 * registry) and falling back to the legacy {@link Rarity} enum only when that registry has no
 * entry. Previously item lore used the hardcoded enum while the modifier engine used
 * {@code rarities.yml}, so editing a rarity's colour or name there never showed on items, and
 * custom rarities couldn't be used on items at all.
 */
public final class ItemRarities {

    private ItemRarities() {}

    private static Optional<RarityDefinition> lookup(String key) {
        if (key == null) return Optional.empty();
        ValmoraAPI api = ValmoraAPI.getInstance();
        RarityModule module = api != null ? api.getRarityModule() : null;
        return module != null ? module.getRegistry().getByKey(key) : Optional.empty();
    }

    private static Rarity legacy(String key) {
        if (key != null) {
            try {
                return Rarity.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // custom rarity — no enum equivalent
            }
        }
        return Rarity.COMMON;
    }

    /** MiniMessage colour tag for the rarity, e.g. {@code "<gold>"}. */
    public static String color(String key) {
        return lookup(key).map(RarityDefinition::getColor).orElseGet(() -> legacy(key).getColor());
    }

    /** Display name, e.g. {@code "Legendary"}. */
    public static String displayName(String key) {
        return lookup(key).map(RarityDefinition::getName).orElseGet(() -> legacy(key).getName());
    }

    /** Whether {@code key} names a rarity in {@code rarities.yml} (or the legacy enum). */
    public static boolean isKnown(String key) {
        if (key == null) return false;
        if (lookup(key).isPresent()) return true;
        try {
            Rarity.valueOf(key.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Every usable key, for error messages. */
    public static List<String> knownKeys() {
        List<String> keys = new ArrayList<>();
        ValmoraAPI api = ValmoraAPI.getInstance();
        RarityModule module = api != null ? api.getRarityModule() : null;
        if (module != null) {
            for (RarityDefinition def : module.getRegistry().getOrdered()) keys.add(def.getKey());
        }
        for (Rarity r : Rarity.values()) {
            if (!keys.contains(r.name())) keys.add(r.name());
        }
        return keys;
    }
}
