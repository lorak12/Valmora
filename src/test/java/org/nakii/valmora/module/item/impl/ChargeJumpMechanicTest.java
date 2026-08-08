package org.nakii.valmora.module.item.impl;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link ChargeJumpMechanic} — the charge-start half of Spring Boots' "To the Moon!"
 *  (see docs/IMPLEMENTATION_BACKLOG.md's item-mechanic-engine CHARGE_JUMP item). The release half
 *  is covered by {@code ChargeJumpListenerTest}; the charge→force math by {@code ChargeJumpTrackerTest}. */
public class ChargeJumpMechanicTest {

    private final ChargeJumpMechanic mechanic = new ChargeJumpMechanic();

    @Test
    void nonPlayerCasterIsANoOp() {
        LivingEntity nonPlayer = mock(LivingEntity.class);
        SimpleExecutionContext context = new SimpleExecutionContext(nonPlayer, null, new MemoryConfiguration());

        assertDoesNotThrow(() -> mechanic.execute(context));
    }

    @Test
    void playerCasterStartsAChargeWithDefaultParams() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        SimpleExecutionContext context = new SimpleExecutionContext(player, null, new MemoryConfiguration());
        mechanic.execute(context);

        assertTrue(ChargeJumpTracker.isCharging(uuid));
        double force = ChargeJumpTracker.releaseCharge(uuid).orElseThrow();
        assertTrue(force >= 0.4 && force < 0.5, "expected near-default-minimum force, got " + force);
    }

    @Test
    void playerCasterHonorsCustomParams() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        MemoryConfiguration params = new MemoryConfiguration();
        params.set("max-charge-ms", 1000.0);
        params.set("min-y-force", 1.0);
        params.set("max-y-force", 3.0);
        SimpleExecutionContext context = new SimpleExecutionContext(player, null, params);
        mechanic.execute(context);

        double force = ChargeJumpTracker.releaseCharge(uuid).orElseThrow();
        assertTrue(force >= 1.0 && force < 1.1, "expected near-custom-minimum force, got " + force);
    }

    @Test
    void mechanicIdIsChargeJump() {
        assertEquals("CHARGE_JUMP", mechanic.getId());
    }
}
