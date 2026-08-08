package org.nakii.valmora.module.pet;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Covers {@link PetDefinition}'s pure stat/XP/milestone math — previously untested beyond the
 *  XP-threshold table itself ({@code PetXpFormulaTest}). Per docs/IMPLEMENTATION_BACKLOG.md's
 *  cross-cutting "unit tests for untested modules" item. */
public class PetDefinitionTest {

    private PetDefinition definition(Map<String, Double> baseStats, Map<String, Double> statsPerLevel,
                                      TreeMap<Integer, List<String>> milestones, long[] xpThresholds) {
        return new PetDefinition("test_pet", "Test Pet", EntityType.WOLF,
                baseStats, statsPerLevel, List.of(), milestones, xpThresholds);
    }

    @Test
    void computeStatsAddsBaseStatsPlusPerLevelScaling() {
        PetDefinition def = definition(
                Map.of("strength", 10.0, "health", 50.0),
                Map.of("strength", 2.0),
                new TreeMap<>(),
                new long[]{100L});

        Map<String, Double> stats = def.computeStats(5);
        assertEquals(20.0, stats.get("strength")); // 10 base + 2*5
        assertEquals(50.0, stats.get("health"));    // no per-level entry, unchanged
    }

    @Test
    void computeStatsAtLevelZeroReturnsJustBaseStats() {
        PetDefinition def = definition(
                Map.of("strength", 10.0),
                Map.of("strength", 2.0),
                new TreeMap<>(),
                new long[]{100L});

        assertEquals(10.0, def.computeStats(0).get("strength"));
    }

    @Test
    void computeStatsWithPerLevelStatNotInBaseStatsStillMerges() {
        PetDefinition def = definition(
                Map.of(),
                Map.of("defense", 3.0),
                new TreeMap<>(),
                new long[]{100L});

        assertEquals(15.0, def.computeStats(5).get("defense")); // merge starting from absent base
    }

    @Test
    void computeStatsDoesNotMutateTheDefinitionsOwnMaps() {
        Map<String, Double> baseStats = Map.of("strength", 10.0);
        PetDefinition def = definition(baseStats, Map.of("strength", 2.0), new TreeMap<>(), new long[]{100L});

        def.computeStats(10);
        assertEquals(10.0, def.getBaseStats().get("strength")); // original base unaffected by the merge
    }

    @Test
    void getMaxLevelReflectsThresholdTableLength() {
        PetDefinition def = definition(Map.of(), Map.of(), new TreeMap<>(), new long[]{100L, 400L, 900L});
        assertEquals(3, def.getMaxLevel());
    }

    @Test
    void xpForLevelLooksUpThePrecomputedTable() {
        PetDefinition def = definition(Map.of(), Map.of(), new TreeMap<>(), new long[]{100L, 400L, 900L});
        assertEquals(100L, def.xpForLevel(1));
        assertEquals(400L, def.xpForLevel(2));
        assertEquals(900L, def.xpForLevel(3));
    }

    @Test
    void xpForLevelClampsAtZeroForNonPositiveLevels() {
        PetDefinition def = definition(Map.of(), Map.of(), new TreeMap<>(), new long[]{100L});
        assertEquals(0L, def.xpForLevel(0));
        assertEquals(0L, def.xpForLevel(-5));
    }

    @Test
    void xpForLevelClampsToTopOfTableAboveMaxLevel() {
        PetDefinition def = definition(Map.of(), Map.of(), new TreeMap<>(), new long[]{100L, 400L, 900L});
        assertEquals(900L, def.xpForLevel(50));
    }

    @Test
    void milestonesMapExposesExactLevelKeyedEvents() {
        TreeMap<Integer, List<String>> milestones = new TreeMap<>();
        milestones.put(25, List.of("give DIAMOND:1"));
        milestones.put(50, List.of("give EMERALD:1", "notify Milestone reached!"));
        PetDefinition def = definition(Map.of(), Map.of(), milestones, new long[]{100L});

        assertNull(def.getMilestones().get(1));
        assertEquals(List.of("give DIAMOND:1"), def.getMilestones().get(25));
        assertEquals(2, def.getMilestones().get(50).size());
    }

    @Test
    void gettersExposeIdNameAndEntityType() {
        PetDefinition def = definition(Map.of(), Map.of(), new TreeMap<>(), new long[]{100L});
        assertEquals("test_pet", def.getId());
        assertEquals("Test Pet", def.getName());
        assertEquals(EntityType.WOLF, def.getEntityType());
    }
}
