package org.nakii.valmora.infrastructure.versioning;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.registry.SimpleRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IdAliasesTest {

    private static final String TYPE = "test_type";

    @AfterEach
    void reset() {
        IdAliases.clear(TYPE);
    }

    @Test
    void resolvesPreviousIdsCaseInsensitively() {
        IdAliases.registerAll(TYPE, List.of("Old_Sword"), "new_sword");
        assertEquals("new_sword", IdAliases.resolve(TYPE, "old_sword"));
        assertEquals("unrelated", IdAliases.resolve(TYPE, "Unrelated"));
    }

    @Test
    void ambiguousAliasIsDropped() {
        IdAliases.register(TYPE, "blade", "sword_a");
        IdAliases.register(TYPE, "blade", "sword_b");
        assertEquals("blade", IdAliases.resolve(TYPE, "blade"));
        IdAliases.register(TYPE, "blade", "sword_a");
        assertFalse(IdAliases.isAlias(TYPE, "blade"), "stays ambiguous until the type reloads");
    }

    @Test
    void registryFallsBackToAliasOnlyOnMiss() {
        SimpleRegistry<String> registry = new SimpleRegistry<>(TYPE);
        registry.register("new_sword", "NEW");
        registry.register("old_sword", "STILL_DEFINED");
        IdAliases.register(TYPE, "old_sword", "new_sword");
        IdAliases.register(TYPE, "ancient_sword", "new_sword");
        assertEquals("STILL_DEFINED", registry.get("old_sword").orElseThrow(), "a real id is never shadowed");
        assertEquals("NEW", registry.get("ANCIENT_SWORD").orElseThrow());
        assertTrue(registry.contains("ancient_sword"));
    }
}
