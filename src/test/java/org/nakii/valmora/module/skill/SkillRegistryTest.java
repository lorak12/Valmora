package org.nakii.valmora.module.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@Tag("skill")
class SkillRegistryTest {

    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
    }

    // getLevelFromXp — default curve thresholds start at 10, 20, 50, 100...

    @Test
    void testGetLevelFromXp_zeroXp_returnsLevel0() {
        assertEquals(0, registry.getLevelFromXp("default", 0));
    }

    @Test
    void testGetLevelFromXp_belowFirstThreshold_returnsLevel0() {
        assertEquals(0, registry.getLevelFromXp("default", 9.9));
    }

    @Test
    void testGetLevelFromXp_exactFirstThreshold_returnsLevel1() {
        // xp < 10 → level 0; xp >= 10 and < 20 → level 1
        assertEquals(1, registry.getLevelFromXp("default", 10));
    }

    @Test
    void testGetLevelFromXp_betweenFirstAndSecondThreshold_returnsLevel1() {
        assertEquals(1, registry.getLevelFromXp("default", 15));
    }

    @Test
    void testGetLevelFromXp_exactSecondThreshold_returnsLevel2() {
        assertEquals(2, registry.getLevelFromXp("default", 20));
    }

    @Test
    void testGetLevelFromXp_veryHighXp_returnsMaxLevel() {
        // array length is 59 thresholds; last threshold is 10,000,000
        int level = registry.getLevelFromXp("default", 10_000_001.0);
        assertEquals(59, level);
    }

    @Test
    void testGetXpForLevel_level0_returnsZero() {
        assertEquals(0, registry.getXpForLevel("default", 0));
    }

    @Test
    void testGetXpForLevel_level1_returnsFirstThreshold() {
        assertEquals(10, registry.getXpForLevel("default", 1));
    }

    @Test
    void testGetXpForLevel_level2_returnsSecondThreshold() {
        assertEquals(20, registry.getXpForLevel("default", 2));
    }

    @Test
    void testGetProgressData_zeroXp_level0_zeroPercent() {
        SkillRegistry.ProgressData pd = registry.getProgressData("default", 0);
        assertEquals(0, pd.currentLevel());
        assertEquals(1, pd.nextLevel());
        assertEquals(0, pd.xpInLevel());
        assertEquals(10, pd.xpRequired());
        assertEquals(0, pd.percent());
    }

    @Test
    void testGetProgressData_midLevel_correctPercent() {
        // Level 1 starts at 10, level 2 at 20. xp=15 is halfway through level 1.
        SkillRegistry.ProgressData pd = registry.getProgressData("default", 15);
        assertEquals(1, pd.currentLevel());
        assertEquals(5, pd.xpInLevel());
        assertEquals(10, pd.xpRequired());
        assertEquals(50, pd.percent());
    }

    @Test
    void testGetProgressData_atMaxThreshold_percent100() {
        // Past the last threshold → full progress
        SkillRegistry.ProgressData pd = registry.getProgressData("default", 11_000_000);
        assertEquals(100, pd.percent());
    }

    @Test
    void testRegisterAndRetrieveSkill() {
        SkillDefinition def = new SkillDefinition("mining", "Mining", "Mine blocks", null, 60, "default", null, null, null);
        registry.registerSkill(def);
        assertTrue(registry.getSkill("mining").isPresent());
        assertSame(def, registry.getSkill("mining").get());
    }

    @Test
    void testGetSkill_unknownId_returnsEmpty() {
        assertTrue(registry.getSkill("nonexistent").isEmpty());
    }
}
