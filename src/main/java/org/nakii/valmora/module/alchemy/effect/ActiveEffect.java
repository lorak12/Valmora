package org.nakii.valmora.module.alchemy.effect;

public record ActiveEffect(String effectId, int level, long expiresAtMs) {

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMs;
    }

    public int remainingSeconds() {
        return Math.max(0, (int) ((expiresAtMs - System.currentTimeMillis()) / 1000));
    }
}
