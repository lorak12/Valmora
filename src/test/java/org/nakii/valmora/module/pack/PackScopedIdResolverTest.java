package org.nakii.valmora.module.pack;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.registry.SimpleRegistry;

import static org.junit.jupiter.api.Assertions.*;

class PackScopedIdResolverTest {

    @Test
    void resolvesToPackNamespaceWhenQualifiedFormExists() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("frostspire:frost_blade", "Frost Blade");

        String resolved = PackScopedIdResolver.resolve("frost_blade", "frostspire", registry);
        assertEquals("frostspire:frost_blade", resolved);
    }

    @Test
    void fallsBackToRawIdWhenNotFoundInPackNamespace() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("base_sword", "Base Sword");

        String resolved = PackScopedIdResolver.resolve("base_sword", "frostspire", registry);
        assertEquals("base_sword", resolved);
    }

    @Test
    void alreadyQualifiedIdIsNeverRewritten() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        String resolved = PackScopedIdResolver.resolve("otherpack:item", "frostspire", registry);
        assertEquals("otherpack:item", resolved);
    }

    @Test
    void nullCurrentPackIdLeavesIdUnchanged() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("frostspire:frost_blade", "Frost Blade");
        assertEquals("frost_blade", PackScopedIdResolver.resolve("frost_blade", null, registry));
    }

    @Test
    void nullRawIdPassesThrough() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        assertNull(PackScopedIdResolver.resolve(null, "frostspire", registry));
    }
}
