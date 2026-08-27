package org.nakii.valmora.module.modifier;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModifierDefinitionTest {

    private static final ConditionGroup NO_CONDITIONS = new ConditionGroup(Collections.emptyList());

    @Test
    void untieredModifierAlwaysReturnsBaseEffectsRegardlessOfRequestedTier() {
        ModifierEffect e = fakeEffect("STAT");
        ModifierDefinition def = new ModifierDefinition("fierce", "traits", "Fierce", null, null,
                List.of(), NO_CONDITIONS, Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), List.of(e), 1.0, Set.of());

        assertFalse(def.isTiered());
        assertEquals(List.of(e), def.getEffects(1));
        assertEquals(List.of(e), def.getEffects(5)); // tier is meaningless for an untiered modifier
    }

    @Test
    void tieredModifierReturnsThatTiersEffectsAndClampsOutOfRangeRequests() {
        ModifierEffect tier1Effect = fakeEffect("STAT");
        ModifierEffect tier3Effect = fakeEffect("STAT");
        Map<Integer, ModifierTier> tiers = Map.of(
                1, new ModifierTier(1, "Rough Ruby", List.of(tier1Effect)),
                3, new ModifierTier(3, "Flawless Ruby", List.of(tier3Effect))
        );
        ModifierDefinition def = new ModifierDefinition("ruby", "gemstones", "Ruby", null, null,
                List.of(), NO_CONDITIONS, Set.of(), Set.of(), Set.of(), Map.of(), tiers, List.of(), 1.0, Set.of());

        assertTrue(def.isTiered());
        assertEquals(3, def.getMaxTier());
        assertEquals(List.of(tier1Effect), def.getEffects(1));
        assertEquals("Rough Ruby", def.getDisplayName(1));
        assertEquals("Flawless Ruby", def.getDisplayName(3));
        // Tier 10 doesn't exist -> clamp to max tier (3)
        assertEquals(List.of(tier3Effect), def.getEffects(10));
    }

    private static ModifierEffect fakeEffect(String type) {
        return new ModifierEffect() {
            @Override public String getType() { return type; }
            @Override public ConditionGroup getConditions() { return NO_CONDITIONS; }
        };
    }
}
