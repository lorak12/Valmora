package org.nakii.valmora.api.registry;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link SimpleRegistry}'s source-tagging primitives added for the content pack manager
 * (docs/modules/design/pack.md): entries registered under a source id can later be looked up and
 * bulk-removed by that source without disturbing entries from other sources, while the plain
 * two-arg {@link Registry#register} path (used by every non-pack content loader today) keeps
 * behaving exactly as before.
 */
class SimpleRegistryTest {

    @Test
    void plainRegisterHasNoSource() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("item_a", "Item A");

        assertTrue(registry.getIdsBySource("anything").isEmpty());
        assertEquals(0, registry.unregisterAllBySource("anything"));
        assertTrue(registry.get("item_a").isPresent(), "plain register must still work");
    }

    @Test
    void entriesAreTaggedWithTheirSourceAndCaseInsensitive() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("packA:item_one", "One", "PackA");
        registry.register("packA:item_two", "Two", "packa");
        registry.register("packB:item_three", "Three", "PackB");

        Set<String> fromA = registry.getIdsBySource("packa");
        assertEquals(Set.of("packa:item_one", "packa:item_two"), fromA);
        assertEquals(Set.of("packb:item_three"), registry.getIdsBySource("PACKB"));
    }

    @Test
    void unregisterAllBySourceOnlyRemovesThatSourcesEntries() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("packA:one", "One", "packA");
        registry.register("packA:two", "Two", "packA");
        registry.register("packB:three", "Three", "packB");
        registry.register("base_item", "Base"); // no source — must survive any pack uninstall

        int removed = registry.unregisterAllBySource("packA");

        assertEquals(2, removed);
        assertTrue(registry.get("packA:one").isEmpty());
        assertTrue(registry.get("packA:two").isEmpty());
        assertTrue(registry.get("packB:three").isPresent(), "other pack's entries must be untouched");
        assertTrue(registry.get("base_item").isPresent(), "base content must be untouched");
        assertTrue(registry.getIdsBySource("packA").isEmpty(), "source index must be cleaned up too");
    }

    @Test
    void reRegisteringUnderANewSourceMovesItsTag() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("shared_id", "V1", "packA");
        registry.register("shared_id", "V2", "packB");

        assertTrue(registry.getIdsBySource("packA").isEmpty());
        assertEquals(Set.of("shared_id"), registry.getIdsBySource("packB"));
        assertEquals("V2", registry.get("shared_id").orElseThrow());
    }

    @Test
    void unregisterSingleEntryAlsoClearsItsSourceTag() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("packA:one", "One", "packA");

        registry.unregister("packA:one");

        assertTrue(registry.getIdsBySource("packA").isEmpty());
    }

    @Test
    void clearWipesSourceTagsToo() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("packA:one", "One", "packA");

        registry.clear();

        assertTrue(registry.getIdsBySource("packA").isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    void nullSourceIdIsTreatedAsBaseContent() {
        SimpleRegistry<String> registry = new SimpleRegistry<>();
        registry.register("item", "Value", null);

        assertTrue(registry.get("item").isPresent());
        assertTrue(registry.getIdsBySource(null).isEmpty());
    }
}
