package org.nakii.valmora.module.zone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 2-arg constructor is the pre-existing signature used by every zone predating Breaking
 * Power gating — it must keep defaulting to requiredPower 0 so existing resource-block configs
 * (no required-power key) continue to be minable by any tool, unchanged.
 */
public class ZoneResourceConfigTest {

    @Test
    public void twoArgConstructor_defaultsRequiredPowerToZero() {
        ZoneResourceConfig config = new ZoneResourceConfig(600, List.of());
        assertEquals(0.0, config.getRequiredPower());
    }

    @Test
    public void threeArgConstructor_storesRequiredPower() {
        ZoneResourceConfig config = new ZoneResourceConfig(400, List.of(), 7.0);
        assertEquals(7.0, config.getRequiredPower());
    }
}
