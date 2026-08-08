package org.nakii.valmora.module.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.stat.StatDefinition;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.StatRegistry;
import org.nakii.valmora.module.stat.SystemStats;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link PlayerState} — previously untested (per docs/IMPLEMENTATION_BACKLOG.md's
 *  cross-cutting "unit tests for untested modules" item). Focuses on the pure health/mana/combat
 *  math and the save/load round-trip (including the pre-extension {@code double[]} back-compat
 *  path) — GUI/persistence wiring around it is out of scope here. */
public class PlayerStateTest {

    private ValmoraAPI api;
    private SystemStats systemStats;
    private StatRegistry statRegistry;

    @BeforeEach
    void setUp() {
        api = mock(ValmoraAPI.class);
        systemStats = mock(SystemStats.class);
        statRegistry = mock(StatRegistry.class);
        when(systemStats.getHealth()).thenReturn("health");
        when(systemStats.getMana()).thenReturn("mana");
        when(api.getSystemStats()).thenReturn(systemStats);
        when(api.getStatRegistry()).thenReturn(statRegistry);

        StatDefinition healthDef = mock(StatDefinition.class);
        when(healthDef.getDefaultValue()).thenReturn(100.0);
        StatDefinition manaDef = mock(StatDefinition.class);
        when(manaDef.getDefaultValue()).thenReturn(50.0);
        when(statRegistry.get("health")).thenReturn(Optional.of(healthDef));
        when(statRegistry.get("mana")).thenReturn(Optional.of(manaDef));

        ValmoraAPI.setProvider(api);
    }

    @Test
    void constructorSeedsFromStatRegistryDefaults() {
        PlayerState state = new PlayerState();
        assertEquals(100.0, state.getCurrentHealth());
        assertEquals(50.0, state.getCurrentMana());
    }

    @Test
    void constructorFallsBackTo100WhenSystemStatsUnavailable() {
        when(api.getSystemStats()).thenReturn(null);
        PlayerState state = new PlayerState();
        assertEquals(100.0, state.getCurrentHealth());
        assertEquals(100.0, state.getCurrentMana());
    }

    @Test
    void constructorFallsBackTo100OnException() {
        when(api.getSystemStats()).thenThrow(new RuntimeException("boom"));
        PlayerState state = new PlayerState();
        assertEquals(100.0, state.getCurrentHealth());
        assertEquals(100.0, state.getCurrentMana());
    }

    @Test
    void reduceHealthClampsAtZero() {
        PlayerState state = new PlayerState();
        state.reduceHealth(500);
        assertEquals(0.0, state.getCurrentHealth());
    }

    @Test
    void reduceHealthNeverGoesNegative() {
        PlayerState state = new PlayerState();
        state.reduceHealth(30);
        assertEquals(70.0, state.getCurrentHealth());
        state.reduceHealth(1000);
        assertEquals(0.0, state.getCurrentHealth());
    }

    @Test
    void healClampsAtStatManagerMaxHealth() {
        PlayerState state = new PlayerState();
        state.reduceHealth(90); // 10 hp left
        StatManager stats = mock(StatManager.class);
        when(stats.getStat("health")).thenReturn(100.0);

        state.heal(50, stats); // 10 + 50 = 60, under cap
        assertEquals(60.0, state.getCurrentHealth());

        state.heal(1000, stats); // clamps at 100
        assertEquals(100.0, state.getCurrentHealth());
    }

    @Test
    void reduceManaClampsAtZero() {
        PlayerState state = new PlayerState();
        state.reduceMana(1000);
        assertEquals(0.0, state.getCurrentMana());
    }

    @Test
    void restoreManaClampsAtStatManagerMaxMana() {
        PlayerState state = new PlayerState();
        state.reduceMana(50); // 0 mana left
        StatManager stats = mock(StatManager.class);
        when(stats.getStat("mana")).thenReturn(50.0);

        state.restoreMana(1000, stats);
        assertEquals(50.0, state.getCurrentMana());
    }

    @Test
    void capToMaxClampsHealthAndManaDownToCurrentStatMax() {
        PlayerState state = new PlayerState(); // health=100, mana=50
        StatManager stats = mock(StatManager.class);
        when(stats.getStat("health")).thenReturn(80.0);
        when(stats.getStat("mana")).thenReturn(30.0);

        state.capToMax(stats);
        assertEquals(80.0, state.getCurrentHealth());
        assertEquals(30.0, state.getCurrentMana());
    }

    @Test
    void capToMaxIsNoOpWhenAlreadyUnderMax() {
        PlayerState state = new PlayerState(); // health=100, mana=50
        StatManager stats = mock(StatManager.class);
        when(stats.getStat("health")).thenReturn(500.0);
        when(stats.getStat("mana")).thenReturn(500.0);

        state.capToMax(stats);
        assertEquals(100.0, state.getCurrentHealth());
        assertEquals(50.0, state.getCurrentMana());
    }

    @Test
    void zoneIdGetterSetterRoundTrips() {
        PlayerState state = new PlayerState();
        assertNull(state.getCurrentZoneId());
        state.setCurrentZoneId("hub");
        assertEquals("hub", state.getCurrentZoneId());
    }

    @Test
    void setInCombatMarksInCombatUntilWindowExpires() {
        PlayerState state = new PlayerState();
        assertFalse(state.isInCombat());
        state.setInCombat();
        assertTrue(state.isInCombat());
    }

    @Test
    void loadDataFromLegacyDoubleArrayRestoresHealthAndMana() {
        PlayerState state = new PlayerState();
        state.loadData(new double[]{42.0, 7.0});
        assertEquals(42.0, state.getCurrentHealth());
        assertEquals(7.0, state.getCurrentMana());
    }

    @Test
    void loadDataFromLegacyArrayIgnoresNullOrTooShort() {
        PlayerState state = new PlayerState();
        state.loadData((double[]) null);
        assertEquals(100.0, state.getCurrentHealth());

        state.loadData(new double[]{1.0});
        assertEquals(100.0, state.getCurrentHealth());
    }

    @Test
    void saveDataAndLoadDataRoundTripFullShape() {
        PlayerState state = new PlayerState();
        state.reduceHealth(20);
        state.setInCombat();
        state.setCurrentZoneId("mine");

        PlayerState.SaveData saved = state.getSaveData();
        assertEquals(80.0, saved.health);
        assertEquals(50.0, saved.mana);
        assertTrue(saved.lastCombatTime > 0);
        assertEquals("mine", saved.zoneId);

        PlayerState fresh = new PlayerState();
        fresh.loadData(saved);
        assertEquals(80.0, fresh.getCurrentHealth());
        assertEquals(50.0, fresh.getCurrentMana());
        assertEquals(saved.lastCombatTime, fresh.getLastCombatTime());
        assertEquals("mine", fresh.getCurrentZoneId());
    }

    @Test
    void loadDataWithNullSaveDataIsNoOp() {
        PlayerState state = new PlayerState();
        state.setCurrentZoneId("hub");
        state.loadData((PlayerState.SaveData) null);
        assertEquals("hub", state.getCurrentZoneId());
        assertEquals(100.0, state.getCurrentHealth());
    }
}
