package org.nakii.valmora.module.alchemy.effect;

import org.bukkit.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@Tag("alchemy")
class AlchemyEffectTest {

    private AlchemyEffect speed;

    @BeforeEach
    void setUp() {
        List<AlchemyEffect.Tier> tiers = List.of(
                new AlchemyEffect.Tier("minecraft:sugar", 1),
                new AlchemyEffect.Tier("minecraft:blaze_powder", 3),
                new AlchemyEffect.Tier("minecraft:dragon_breath", 5)
        );
        List<Integer> durations = List.of(60, 70, 80, 90, 100, 110, 120, 130);
        Map<String, List<Double>> stats = Map.of(
                "speed", List.of(5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0)
        );
        speed = new AlchemyEffect("speed", "Speed", AlchemyEffectType.BUFF, "common",
                Color.WHITE, List.of(), tiers, 8, durations, stats);
    }

    @Test
    void testGetDuration_level1_returnsFirst() {
        assertEquals(60, speed.getDuration(1));
    }

    @Test
    void testGetDuration_level5_returnsFifth() {
        assertEquals(100, speed.getDuration(5));
    }

    @Test
    void testGetDuration_levelBeyondMax_clampsToLast() {
        assertEquals(130, speed.getDuration(999));
    }

    @Test
    void testGetDuration_level8_returnsLast() {
        assertEquals(130, speed.getDuration(8));
    }

    @Test
    void testGetStatValue_level1_returnsFirst() {
        assertEquals(5.0, speed.getStatValue("speed", 1), 1e-9);
    }

    @Test
    void testGetStatValue_level8_returnsLast() {
        assertEquals(40.0, speed.getStatValue("speed", 8), 1e-9);
    }

    @Test
    void testGetStatValue_levelBeyondMax_clampsToLast() {
        assertEquals(40.0, speed.getStatValue("speed", 100), 1e-9);
    }

    @Test
    void testGetStatValue_unknownStat_returnsZero() {
        assertEquals(0.0, speed.getStatValue("strength", 1), 1e-9);
    }

    @Test
    void testGetStatValue_caseInsensitive() {
        assertEquals(5.0, speed.getStatValue("SPEED", 1), 1e-9);
    }

    @Test
    void testGetMaxBaseLevel_returnsMaxTierLevel() {
        assertEquals(5, speed.getMaxBaseLevel());
    }

    @Test
    void testGetTierForIngredient_found() {
        Optional<AlchemyEffect.Tier> tier = speed.getTierForIngredient("minecraft:sugar");
        assertTrue(tier.isPresent());
        assertEquals(1, tier.get().level());
    }

    @Test
    void testGetTierForIngredient_foundCaseInsensitive() {
        Optional<AlchemyEffect.Tier> tier = speed.getTierForIngredient("Minecraft:SUGAR");
        assertTrue(tier.isPresent());
    }

    @Test
    void testGetTierForIngredient_notFound_returnsEmpty() {
        assertTrue(speed.getTierForIngredient("minecraft:diamond").isEmpty());
    }

    @Test
    void testGetMaxLevel_returnsConfiguredValue() {
        assertEquals(8, speed.getMaxLevel());
    }
}
