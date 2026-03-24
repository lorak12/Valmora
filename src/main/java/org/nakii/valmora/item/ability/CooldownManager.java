package org.nakii.valmora.item.ability;

import java.util.HashMap;
import java.util.Map;

public class CooldownManager {
    private final Map<String, Long> cooldowns = new HashMap<>();

    public void setCooldown(String abilityId, double seconds) {
        long expirationTime = System.currentTimeMillis() + (long) (seconds * 1000);
        cooldowns.put(abilityId, expirationTime);
    }

    public boolean isOnCooldown(String abilityId) {
        return cooldowns.containsKey(abilityId) && cooldowns.get(abilityId) > System.currentTimeMillis();
    }

    public long getRemainingCooldown(String abilityId) {
        if (!isOnCooldown(abilityId)) {
            return 0;
        }
        return cooldowns.get(abilityId) - System.currentTimeMillis();
    }

    public void removeCooldown(String abilityId) {
        cooldowns.remove(abilityId);
    }
}
