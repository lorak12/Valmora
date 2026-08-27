package org.nakii.valmora.module.modifier.value;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.rarity.RarityDefinition;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the generic value-resolution pipeline (docs/Valmora_Modifier_Framework_Design.docx §5/§18)
 * — the part of the framework most sensitive to getting the ADD/MULTIPLY semantics right, since
 * every default reforge/gemstone-equivalent value in the shipped content depends on it.
 */
class ValueResolverTest {

    private static final RarityDefinition LEGENDARY = new RarityDefinition("LEGENDARY", "legendary", "Legendary", "<gold>", 4, 2.0);
    private static final RarityDefinition COMMON = new RarityDefinition("COMMON", "common", "Common", "<white>", 0, 1.0);

    @Test
    void literalValueIgnoresRarityAndTier() {
        ValueResolver v = new LiteralValue(42);
        assertEquals(42.0, v.resolve(LEGENDARY, 3, null));
        assertEquals(42.0, v.resolve(null, 1, null));
    }

    @Test
    void rarityScaleMultiplyMatchesDesignDocExample() {
        // §5: base: 10, scaling: { property: power, operation: MULTIPLY } -> base * rarity.power
        ValueResolver v = new RarityScaleValue(10, "power", "MULTIPLY", 1.0);
        assertEquals(20.0, v.resolve(LEGENDARY, 1, null)); // 10 * 2.0
        assertEquals(10.0, v.resolve(COMMON, 1, null));    // 10 * 1.0
    }

    @Test
    void rarityScaleAddMatchesDesignDocExample() {
        // §5: base: 5, scaling: { property: rank, operation: ADD, factor: 3 } -> base + rank*factor
        ValueResolver v = new RarityScaleValue(5, "rank", "ADD", 3.0);
        assertEquals(17.0, v.resolve(LEGENDARY, 1, null)); // 5 + 4*3
        assertEquals(5.0, v.resolve(COMMON, 1, null));     // 5 + 0*3
    }

    @Test
    void rarityScaleFallsBackToBaseWhenRarityIsNull() {
        ValueResolver v = new RarityScaleValue(10, "power", "MULTIPLY", 1.0);
        assertEquals(10.0, v.resolve(null, 1, null));
    }

    @Test
    void rarityScaleFallsBackToBaseForUnknownProperty() {
        ValueResolver v = new RarityScaleValue(10, "nonexistent", "MULTIPLY", 1.0);
        assertEquals(10.0, v.resolve(LEGENDARY, 1, null));
    }

    @Test
    void valueParserParsesLiteralNumber() {
        ValueResolver v = ValueParser.parse(15);
        assertEquals(15.0, v.resolve(null, 1, null));
    }

    @Test
    void valueParserParsesNumericString() {
        ValueResolver v = ValueParser.parse("7.5");
        assertEquals(7.5, v.resolve(null, 1, null));
    }

    @Test
    void valueParserParsesRarityScaleMap() {
        Map<String, Object> scaling = new HashMap<>();
        scaling.put("type", "RARITY");
        scaling.put("property", "power");
        scaling.put("operation", "MULTIPLY");

        Map<String, Object> value = new HashMap<>();
        value.put("base", 10);
        value.put("scaling", scaling);

        ValueResolver v = ValueParser.parse(value);
        assertEquals(20.0, v.resolve(LEGENDARY, 1, null));
    }

    @Test
    void valueParserWithoutScalingSectionIsLiteralBase() {
        Map<String, Object> value = new HashMap<>();
        value.put("base", 9);
        ValueResolver v = ValueParser.parse(value);
        assertEquals(9.0, v.resolve(LEGENDARY, 1, null));
    }

    @Test
    void customValueDelegatesToRegisteredResolver() {
        ModifierValueResolverRegistry.clear();
        try {
            ModifierValueResolverRegistry.register("test:double_rank", (rarity, tier, ctx) -> rarity == null ? 0 : rarity.getRank() * 2);
            ValueResolver v = new CustomValue("test:double_rank");
            assertEquals(8.0, v.resolve(LEGENDARY, 1, null));
        } finally {
            ModifierValueResolverRegistry.clear();
        }
    }

    @Test
    void customValueWithUnregisteredIdResolvesToZero() {
        ValueResolver v = new CustomValue("nope:missing");
        assertEquals(0.0, v.resolve(LEGENDARY, 1, null));
    }
}
