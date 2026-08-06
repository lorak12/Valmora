package org.nakii.valmora.module.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.util.Keys;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 2.3 of the refactor (docs/REFACTOR/PROGRESS.md): PDC-backed damage-type
 * resistances that apply to any {@link LivingEntity}, not just custom Valmora mobs.
 *
 * Note: like {@code DamageCalculatorTest}, this never calls {@code Keys.init(plugin)} — the
 * (null) {@code Keys.DAMAGE_RESISTANCES_KEY} static field is used consistently as the mock
 * matcher and the real call site, so identity/equality still holds without a live plugin.
 */
public class DamageResistanceComponentTest {

    @Test
    void nullEntityIsSafeAndReturnsZero() {
        assertEquals(0.0, DamageResistanceComponent.getResistance(null, DamageType.FIRE));
        assertTrue(DamageResistanceComponent.read(null).isEmpty());
    }

    @Test
    void entityWithNoPdcEntryHasZeroResistance() {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.DAMAGE_RESISTANCES_KEY, PersistentDataType.STRING)).thenReturn(null);

        assertEquals(0.0, DamageResistanceComponent.getResistance(entity, DamageType.FIRE));
    }

    @Test
    void parsesSerializedResistanceString() {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.DAMAGE_RESISTANCES_KEY, PersistentDataType.STRING)).thenReturn("FIRE=0.25;POISON=1.0");

        assertEquals(0.25, DamageResistanceComponent.getResistance(entity, DamageType.FIRE));
        assertEquals(1.0, DamageResistanceComponent.getResistance(entity, DamageType.POISON));
        assertEquals(0.0, DamageResistanceComponent.getResistance(entity, DamageType.MELEE));
    }

    @Test
    void lookupIsCaseInsensitiveOnTheDamageTypeId() {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        // Stored lowercase — read() must normalize to uppercase before matching type.getId().
        when(pdc.get(Keys.DAMAGE_RESISTANCES_KEY, PersistentDataType.STRING)).thenReturn("fire=0.5");

        assertEquals(0.5, DamageResistanceComponent.getResistance(entity, DamageType.FIRE));
    }

    @Test
    void corruptEntriesAreSkippedRatherThanThrowing() {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.DAMAGE_RESISTANCES_KEY, PersistentDataType.STRING)).thenReturn("FIRE=not_a_number;POISON=0.3");

        Map<String, Double> parsed = DamageResistanceComponent.read(entity);
        assertFalse(parsed.containsKey("FIRE"));
        assertEquals(0.3, parsed.get("POISON"));
    }

    @Test
    void writeThenReadRoundTripsThroughTheSameBackingField() {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);

        // Simulate real PDC storage: set() captures the value, get() returns it back.
        String[] backing = new String[1];
        doAnswer(inv -> { backing[0] = inv.getArgument(2); return null; })
                .when(pdc).set(eq(Keys.DAMAGE_RESISTANCES_KEY), eq(PersistentDataType.STRING), anyString());
        when(pdc.get(Keys.DAMAGE_RESISTANCES_KEY, PersistentDataType.STRING)).thenAnswer(inv -> backing[0]);

        DamageResistanceComponent.write(entity, Map.of("FIRE", 0.4));
        assertEquals(0.4, DamageResistanceComponent.getResistance(entity, DamageType.FIRE));
    }

    @Test
    void writeWithEmptyMapRemovesTheKey() {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);

        DamageResistanceComponent.write(entity, Map.of());

        verify(pdc).remove(Keys.DAMAGE_RESISTANCES_KEY);
    }
}
