package org.nakii.valmora.module.modifier.effect;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers new effect types consumed by modifier definitions (docs/
 * Valmora_Modifier_Framework_Design.docx §20). The built-in {@code STAT}/{@code ABILITY}/{@code
 * EVENT}/{@code STATE} types are handled directly by {@link ModifierEffectParser}; anything else
 * looked up here. IDs should be namespaced (e.g. {@code "my_plugin:soul_harvest"}).
 */
public final class ModifierEffectRegistry {

    private static final Map<String, ModifierEffectFactory> FACTORIES = new ConcurrentHashMap<>();

    private ModifierEffectRegistry() {}

    public static void register(String type, ModifierEffectFactory factory) {
        FACTORIES.put(type.toUpperCase(Locale.ROOT), factory);
    }

    public static void unregister(String type) {
        FACTORIES.remove(type.toUpperCase(Locale.ROOT));
    }

    public static ModifierEffectFactory get(String type) {
        return type == null ? null : FACTORIES.get(type.toUpperCase(Locale.ROOT));
    }

    public static void clear() {
        FACTORIES.clear();
    }
}
