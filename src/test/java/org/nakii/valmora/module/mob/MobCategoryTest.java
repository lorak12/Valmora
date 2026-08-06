package org.nakii.valmora.module.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers Phase 3.4 of the refactor (docs/REFACTOR/PROGRESS.md): {@link MobCategory} replaced the
 * old {@code enum MobCategory} with a registry-backed class, following the same backward-compat
 * pattern proven by {@code DamageType} in Phase 2.
 */
public class MobCategoryTest {

    @Test
    void builtInCategoriesAreBackwardCompatible() {
        assertEquals("UNDEAD", MobCategory.UNDEAD.getId());
        assertSame(MobCategory.UNDEAD, MobCategory.valueOf("UNDEAD"));
        assertSame(MobCategory.UNDEAD, MobCategory.valueOf("undead"), "valueOf must be case-insensitive");
    }

    @Test
    void valueOfUnknownThrowsLikeEnumValueOfDid() {
        assertThrows(IllegalArgumentException.class, () -> MobCategory.valueOf("not_a_real_category"));
    }

    @Test
    void findUnknownReturnsEmptyOptional() {
        assertTrue(MobCategory.find("not_a_real_category").isEmpty());
    }

    @Test
    void defineNewCategoryIsDiscoverableAndStable() {
        MobCategory first = MobCategory.define("test_dragon");
        MobCategory second = MobCategory.define("test_dragon");

        assertSame(first, second);
        assertSame(first, MobCategory.valueOf("test_dragon"));
        assertTrue(MobCategory.values().stream().anyMatch(c -> c.getId().equals("TEST_DRAGON")));
    }
}
