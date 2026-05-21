package org.nakii.valmora.module.mob;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.combat.DamageType;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MobDefinitionTest {

    private MobDefinition build(double baseDamage, int level, int baseXp) {
        return new MobDefinition.Builder("test_mob")
                .baseDamage(baseDamage)
                .level(level)
                .baseXp(baseXp)
                .build();
    }

    @Test
    void testGetScaledDamage_level1_equalsBaseDamage() {
        assertEquals(5.0, build(5.0, 1, 2).getScaledDamage());
    }

    @Test
    void testGetScaledDamage_level5_addsLevelMinusOne() {
        assertEquals(9.0, build(5.0, 5, 2).getScaledDamage());
    }

    @Test
    void testGetScaledDamage_level10_scales() {
        assertEquals(19.0, build(10.0, 10, 3).getScaledDamage());
    }

    @Test
    void testGetXpReward_level1_equalsBaseXp() {
        assertEquals(2, build(5.0, 1, 2).getXpReward());
    }

    @Test
    void testGetXpReward_level5_multiplies() {
        assertEquals(10, build(5.0, 5, 2).getXpReward());
    }

    @Test
    void testGetXpReward_level10_multiplies() {
        assertEquals(30, build(10.0, 10, 3).getXpReward());
    }

    @Test
    void testBuilderDefaults() {
        MobDefinition mob = new MobDefinition.Builder("defaults").build();
        assertEquals(5.0, mob.getBaseDamage());
        assertEquals(1, mob.getLevel());
        assertEquals(2, mob.getBaseXp());
        assertEquals(DamageType.MELEE, mob.getDamageType());
        assertEquals(0, mob.getGoldReward());
    }
}
