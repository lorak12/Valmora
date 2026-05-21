package org.nakii.valmora.module.stat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class StatDefinitionTest {

    private StatDefinition make(String color, String name) {
        return new StatDefinition("health", name, 100.0, 10000.0, color, "APPLE", "desc", false, null);
    }

    @Test
    void testGetFormattedName_returnsColorPlusName() {
        StatDefinition def = make("<red>", "Health");
        assertEquals("<red>Health", def.getFormattedName());
    }

    @Test
    void testFormat_positiveValue_includesPlusSign() {
        StatDefinition def = make("<red>", "Health");
        assertEquals("<red>Health: +50", def.format(50.0));
    }

    @Test
    void testFormat_negativeValue_includesMinusSign() {
        StatDefinition def = make("<red>", "Health");
        assertEquals("<red>Health: -10", def.format(-10.0));
    }

    @Test
    void testFormat_zeroValue_includesPlusSign() {
        StatDefinition def = make("<gray>", "Defense");
        assertEquals("<gray>Defense: +0", def.format(0.0));
    }

    @Test
    void testFormat_fractionalValue_truncated() {
        StatDefinition def = make("<gold>", "Crit");
        // (int) 99.9 == 99
        assertEquals("<gold>Crit: +99", def.format(99.9));
    }

    @Test
    void testGetters_returnCorrectValues() {
        StatDefinition def = new StatDefinition("mana", "Mana", 50.0, 5000.0, "<aqua>", "LAPIS_LAZULI", "Mana pool", true, "generic.max_absorption");
        assertEquals("mana", def.getId());
        assertEquals("Mana", def.getDisplayName());
        assertEquals(50.0, def.getDefaultValue());
        assertEquals(5000.0, def.getMaxValue());
        assertEquals("<aqua>", def.getColor());
        assertEquals("LAPIS_LAZULI", def.getIcon());
        assertEquals("Mana pool", def.getDescription());
        assertTrue(def.isPool());
        assertEquals("generic.max_absorption", def.getVanillaAttribute());
    }
}
