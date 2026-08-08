package org.nakii.valmora.module.warp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WarpDefinitionTest {

    @Test
    void shortConstructor_defaultsFeesAndGates() {
        WarpDefinition warp = new WarpDefinition("hub", "Hub", "world",
                1.0, 2.0, 3.0, 90f, 0f, "always", List.of());

        assertEquals("hub", warp.getId());
        assertEquals("Hub", warp.getDisplayName());
        assertEquals("world", warp.getWorldName());
        assertEquals(1.0, warp.getX());
        assertEquals(2.0, warp.getY());
        assertEquals(3.0, warp.getZ());
        assertEquals(90f, warp.getYaw());
        assertEquals(0f, warp.getPitch());
        assertEquals("always", warp.getUnlockCondition());
        assertTrue(warp.getPadLocations().isEmpty());

        assertEquals(0.0, warp.getCost());
        assertEquals(0, warp.getCooldownSeconds());
        assertEquals(0, warp.getWarmupSeconds());
        assertNull(warp.getPermission());
    }

    @Test
    void fullConstructor_carriesFeesAndGates() {
        WarpDefinition warp = new WarpDefinition("mine", "Mine", "world",
                10.0, 64.0, 10.0, 0f, 0f, "tag:vip", List.of(new int[]{10, 64, 10}),
                500.0, 60, 3, "valmora.warp.mine");

        assertEquals(500.0, warp.getCost());
        assertEquals(60, warp.getCooldownSeconds());
        assertEquals(3, warp.getWarmupSeconds());
        assertEquals("valmora.warp.mine", warp.getPermission());
        assertEquals(1, warp.getPadLocations().size());
    }
}
