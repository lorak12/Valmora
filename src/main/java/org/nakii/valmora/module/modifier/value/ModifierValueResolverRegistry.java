package org.nakii.valmora.module.modifier.value;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java API extension point for plugin-registered value providers (docs/
 * Valmora_Modifier_Framework_Design.docx §20), referenced from YAML as {@code type: CUSTOM,
 * resolver: "my_plugin:boss_power"}. Prefer namespaced ids to avoid collisions.
 */
public final class ModifierValueResolverRegistry {

    private static final Map<String, ValueResolver> RESOLVERS = new ConcurrentHashMap<>();

    private ModifierValueResolverRegistry() {}

    public static void register(String id, ValueResolver resolver) {
        RESOLVERS.put(id.toLowerCase(Locale.ROOT), resolver);
    }

    public static void unregister(String id) {
        RESOLVERS.remove(id.toLowerCase(Locale.ROOT));
    }

    public static ValueResolver get(String id) {
        return id == null ? null : RESOLVERS.get(id.toLowerCase(Locale.ROOT));
    }

    public static void clear() {
        RESOLVERS.clear();
    }
}
