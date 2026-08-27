package org.nakii.valmora.module.rarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RarityRegistryTest {

    private RarityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RarityRegistry();
        registry.register(new RarityDefinition("COMMON", "common", "Common", "<white>", 0, 1.0));
        registry.register(new RarityDefinition("LEGENDARY", "legendary", "Legendary", "<gold>", 4, 2.0));
        registry.register(new RarityDefinition("RARE", "rare", "Rare", "<blue>", 2, 1.35));
    }

    @Test
    void looksUpByKeyCaseInsensitively() {
        assertTrue(registry.getByKey("legendary").isPresent());
        assertTrue(registry.getByKey("LEGENDARY").isPresent());
        assertEquals("legendary", registry.getByKey("Legendary").get().getId());
    }

    @Test
    void looksUpById() {
        assertTrue(registry.getById("rare").isPresent());
        assertEquals("RARE", registry.getById("rare").get().getKey());
    }

    @Test
    void unknownKeyReturnsEmpty() {
        assertTrue(registry.getByKey("mythic").isEmpty());
        assertTrue(registry.getById("mythic").isEmpty());
    }

    @Test
    void ordersByAscendingRank() {
        List<RarityDefinition> ordered = registry.getOrdered();
        assertEquals(List.of("COMMON", "RARE", "LEGENDARY"), ordered.stream().map(RarityDefinition::getKey).toList());
    }

    @Test
    void propertyLookupSupportsRankAndPower() {
        RarityDefinition legendary = registry.getByKey("LEGENDARY").get();
        assertEquals(4.0, legendary.getProperty("rank"));
        assertEquals(2.0, legendary.getProperty("power"));
        assertTrue(Double.isNaN(legendary.getProperty("unknown_property")));
    }

    @Test
    void clearRemovesEverything() {
        registry.clear();
        assertTrue(registry.isEmpty());
    }
}
