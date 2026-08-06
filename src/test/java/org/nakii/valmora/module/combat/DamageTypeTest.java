package org.nakii.valmora.module.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers Phase 2.1 of the refactor (docs/REFACTOR/PROGRESS.md): {@link DamageType} replaced the
 * old {@code enum DamageType} with a registry-backed class that keeps the same static-field /
 * {@code valueOf} / {@code ==} call-site contract enums had, while being redefinable from YAML.
 */
public class DamageTypeTest {

    @Test
    void builtInStaticFieldsAreBackwardCompatible() {
        assertEquals("MELEE", DamageType.MELEE.getId());
        assertSame(DamageType.MELEE, DamageType.valueOf("MELEE"));
        assertSame(DamageType.MELEE, DamageType.valueOf("melee"), "valueOf must be case-insensitive");
    }

    @Test
    void voidDrowningAndFallIgnoreDefenseByDefault() {
        assertTrue(DamageType.VOID.isIgnoresDefense());
        assertTrue(DamageType.DROWNING.isIgnoresDefense());
        assertTrue(DamageType.FALL.isIgnoresDefense());
        assertFalse(DamageType.MELEE.isIgnoresDefense());
        assertFalse(DamageType.FIRE.isIgnoresDefense());
    }

    @Test
    void valueOfUnknownIdThrowsLikeEnumValueOfDid() {
        assertThrows(IllegalArgumentException.class, () -> DamageType.valueOf("not_a_real_type"));
    }

    @Test
    void findUnknownIdReturnsEmptyOptional() {
        assertTrue(DamageType.find("not_a_real_type").isEmpty());
    }

    @Test
    void defineWithExistingIdReusesTheSameInstance() {
        DamageType first = DamageType.define("test_custom_type", "<red>", false, List.of());
        DamageType second = DamageType.define("test_custom_type", "<blue>", true, List.of());

        assertSame(first, second, "redefining an id must mutate in place, not replace the instance");
        assertEquals("<blue>", first.getColor());
        assertTrue(first.isIgnoresDefense());
    }

    @Test
    void newlyDefinedTypeIsDiscoverableThroughValueOfAndFind() {
        DamageType.define("test_electric", "<yellow>", false, List.of());

        assertSame(DamageType.valueOf("test_electric"), DamageType.find("test_electric").orElseThrow());
        assertTrue(DamageType.values().stream().anyMatch(t -> t.getId().equals("TEST_ELECTRIC")));
    }

    @Test
    void typeWithNoOnHitEventsFiresWithoutError() {
        // Built-ins ship with an empty on-hit list — firing must be a safe no-op, not NPE.
        assertDoesNotThrow(() -> DamageType.MELEE.fireOnHit(
                new org.nakii.valmora.api.execution.SimpleExecutionContext(null, null, null)));
    }
}
