package org.nakii.valmora.module.enchant;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.item.ItemType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Mirrors {@code ModifierBuilderTest}'s coverage style for {@code ModifierDefinition.builder()}. */
class EnchantmentDefinitionBuilderTest {

    @Test
    void buildsAnEquivalentDefinitionToTheLegacyConstructor() {
        EnchantmentDefinition viaBuilder = EnchantmentDefinition.builder("sharpness")
                .name("Sharpness")
                .description(List.of("Deals more damage."))
                .etableMaxLevel(5)
                .absoluteMaxLevel(7)
                .target(ItemType.SWORD)
                .conflict("smite")
                .build();

        EnchantmentDefinition viaConstructor = new EnchantmentDefinition("sharpness", "Sharpness",
                List.of("Deals more damage."), 5, 7, List.of(ItemType.SWORD), List.of("smite"), null);

        assertEquals(viaConstructor.getId(), viaBuilder.getId());
        assertEquals(viaConstructor.getName(), viaBuilder.getName());
        assertEquals(viaConstructor.getDescription(), viaBuilder.getDescription());
        assertEquals(viaConstructor.getEtableMaxLevel(), viaBuilder.getEtableMaxLevel());
        assertEquals(viaConstructor.getAbsoluteMaxLevel(), viaBuilder.getAbsoluteMaxLevel());
        assertEquals(viaConstructor.getTargets(), viaBuilder.getTargets());
        assertEquals(viaConstructor.getConflicts(), viaBuilder.getConflicts());
        assertTrue(viaBuilder.canApplyTo(ItemType.SWORD));
        assertTrue(viaBuilder.conflictsWith("smite"));
    }

    @Test
    void defaultsMatchTheLegacyConstructorDefaults() {
        EnchantmentDefinition def = EnchantmentDefinition.builder("test").build();

        assertEquals("test", def.getId());
        assertEquals("test", def.getName());
        assertEquals(5, def.getEtableMaxLevel());
        assertEquals(10, def.getAbsoluteMaxLevel());
        assertTrue(def.getTargets().isEmpty());
        assertTrue(def.getConflicts().isEmpty());
        assertNull(def.getLogic());
    }

    @Test
    void variablesAndInertSectionsAreCarriedButOptional() {
        EnchantmentDefinition def = EnchantmentDefinition.builder("first_strike")
                .variable("bonus", "$level$ * 25")
                .build();

        assertEquals(Map.of("bonus", "$level$ * 25"), def.getVariables());
        assertNull(def.getModifyAttack());
        assertNull(def.getModifyDefend());
        assertTrue(def.getTriggers().isEmpty());
        assertTrue(def.getTransientStates().isEmpty());
        assertTrue(def.getPersistentStates().isEmpty());
        assertTrue(def.getStatBonuses().isEmpty());
    }
}
