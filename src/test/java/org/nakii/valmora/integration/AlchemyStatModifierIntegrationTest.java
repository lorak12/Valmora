package org.nakii.valmora.integration;

import org.bukkit.Color;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffectType;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("alchemy")
@Tag("integration")
class AlchemyStatModifierIntegrationTest {

    private AlchemyManager alchemyManager;
    private UUID entityUuid;
    private Player player;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);

        alchemyManager = new AlchemyManager(10);

        AlchemyEffect speedEffect = new AlchemyEffect(
                "speed", "Speed", AlchemyEffectType.BUFF, "COMMON",
                Color.WHITE, List.of(),
                List.of(new AlchemyEffect.Tier("nether_wart", 1)),
                2, List.of(60, 120),
                Map.of("speed", List.of(5.0, 10.0))
        );
        alchemyManager.registerEffect(speedEffect);

        entityUuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(entityUuid);
    }

    private LivingEntity nonPlayerEntity() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(entityUuid);
        return entity;
    }

    @Test
    void testApplyEffect_thenApplyEffectsToStats_increasesStat() {
        alchemyManager.applyEffect(nonPlayerEntity(), "speed", 1, 3600);

        StatManager statManager = new StatManager();
        alchemyManager.applyEffectsToStats(player, statManager);

        assertEquals(5.0, statManager.getStat("speed"), 1e-9);
    }

    @Test
    void testApplyEffect_level2_appliesHigherStatValue() {
        alchemyManager.applyEffect(nonPlayerEntity(), "speed", 2, 3600);

        StatManager statManager = new StatManager();
        alchemyManager.applyEffectsToStats(player, statManager);

        assertEquals(10.0, statManager.getStat("speed"), 1e-9);
    }

    @Test
    void testExpiredEffect_isSkipped() {
        // durationSeconds = -1 → expiresAt already in the past
        alchemyManager.applyEffect(nonPlayerEntity(), "speed", 1, -1);

        StatManager statManager = new StatManager();
        alchemyManager.applyEffectsToStats(player, statManager);

        assertEquals(0.0, statManager.getStat("speed"), 1e-9);
    }

    @Test
    void testMultipleEffects_allApplied() {
        AlchemyEffect strengthEffect = new AlchemyEffect(
                "strength", "Strength", AlchemyEffectType.BUFF, "COMMON",
                Color.RED, List.of(),
                List.of(new AlchemyEffect.Tier("blaze_powder", 1)),
                1, List.of(60),
                Map.of("strength", List.of(8.0))
        );
        alchemyManager.registerEffect(strengthEffect);

        LivingEntity entity = nonPlayerEntity();
        alchemyManager.applyEffect(entity, "speed", 1, 3600);
        alchemyManager.applyEffect(entity, "strength", 1, 3600);

        StatManager statManager = new StatManager();
        alchemyManager.applyEffectsToStats(player, statManager);

        assertEquals(5.0, statManager.getStat("speed"), 1e-9);
        assertEquals(8.0, statManager.getStat("strength"), 1e-9);
    }

    @Test
    void testNoActiveEffects_statUnchanged() {
        StatManager statManager = new StatManager();
        alchemyManager.applyEffectsToStats(player, statManager);

        assertEquals(0.0, statManager.getStat("speed"), 1e-9);
    }

    @Test
    void testApplyEffect_replacesExistingEffectOfSameId() {
        LivingEntity entity = nonPlayerEntity();
        alchemyManager.applyEffect(entity, "speed", 1, 3600);
        alchemyManager.applyEffect(entity, "speed", 2, 3600);

        StatManager statManager = new StatManager();
        alchemyManager.applyEffectsToStats(player, statManager);

        // Only the replacement (level 2 = 10.0) should apply, not 5+10=15
        assertEquals(10.0, statManager.getStat("speed"), 1e-9);
    }
}
