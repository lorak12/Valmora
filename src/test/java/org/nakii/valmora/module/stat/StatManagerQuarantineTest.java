package org.nakii.valmora.module.stat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 5 Task 21 of the refactor (docs/REFACTOR/PROGRESS.md): unrecognized stat ids in a
 * profile's saved data must be quarantined — excluded from live stat calculations — rather than
 * dropped, so they survive a load/save cycle unchanged and can come back to life if the stat role
 * is ever re-registered.
 */
public class StatManagerQuarantineTest {

    private StatRegistry statRegistry;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        ValmoraAPI.setProvider(api);
        statRegistry = new StatRegistry();
        statRegistry.register(new StatDefinition("damage", "Damage", 0.0, Double.MAX_VALUE, "<red>", "IRON_SWORD", "", false, null));
        statRegistry.register(new StatDefinition("defense", "Defense", 0.0, Double.MAX_VALUE, "<gray>", "SHIELD", "", false, null));
        when(api.getStatRegistry()).thenReturn(statRegistry);
    }

    @Test
    void recognizedKeysLoadNormallyAndAreNotQuarantined() {
        StatManager statManager = new StatManager();
        Map<String, Double> quarantined = statManager.loadDataAndQuarantineUnrecognized(
                Map.of("damage", 25.0, "defense", 10.0));

        assertTrue(quarantined.isEmpty());
        assertEquals(25.0, statManager.getStat("damage"));
        assertEquals(10.0, statManager.getStat("defense"));
    }

    @Test
    void unrecognizedKeyIsQuarantinedAndExcludedFromEffectiveStats() {
        StatManager statManager = new StatManager();
        Map<String, Double> quarantined = statManager.loadDataAndQuarantineUnrecognized(
                Map.of("deleted_custom_role", 999.0));

        assertEquals(1, quarantined.size());
        assertEquals(999.0, quarantined.get("deleted_custom_role"));
        // Must not silently become a live stat affecting gameplay math.
        assertEquals(0.0, statManager.getStat("deleted_custom_role"));
        assertFalse(statManager.getStatIds().contains("deleted_custom_role"));
    }

    @Test
    void mixOfRecognizedAndUnrecognizedKeysIsSplitCorrectly() {
        StatManager statManager = new StatManager();
        Map<String, Double> quarantined = statManager.loadDataAndQuarantineUnrecognized(
                Map.of("damage", 50.0, "old_removed_role", 5.0));

        assertEquals(50.0, statManager.getStat("damage"));
        assertEquals(Map.of("old_removed_role", 5.0), quarantined);
    }

    @Test
    void quarantinedDataSurvivesARoundTripOnTheProfile() {
        ValmoraProfile profile = new ValmoraProfile("Test");
        Map<String, Double> quarantined = profile.getStatManager().loadDataAndQuarantineUnrecognized(
                Map.of("legacy_stat", 42.0));
        profile.getQuarantinedStats().putAll(quarantined);

        // Simulates SQLDataStore.savePlayer()'s merge step.
        Map<String, Double> saveData = new java.util.HashMap<>(profile.getStatManager().getSaveData());
        saveData.putAll(profile.getQuarantinedStats());

        assertEquals(42.0, saveData.get("legacy_stat"));
    }

    @Test
    void nullSavedDataIsSafeAndReturnsNoQuarantine() {
        StatManager statManager = new StatManager();
        assertTrue(statManager.loadDataAndQuarantineUnrecognized(null).isEmpty());
    }
}
