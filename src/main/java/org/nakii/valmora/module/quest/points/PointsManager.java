package org.nakii.valmora.module.quest.points;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.util.UUID;

public class PointsManager {

    private static final String KEY_PREFIX = "point.";

    public int getPoints(UUID playerUuid, String category) {
        ValmoraProfile profile = getProfile(playerUuid);
        if (profile == null) return 0;
        Object v = profile.getVariables().get(KEY_PREFIX + category.toLowerCase());
        return v instanceof Number n ? n.intValue() : 0;
    }

    public void setPoints(UUID playerUuid, String category, int amount) {
        ValmoraProfile profile = getProfile(playerUuid);
        if (profile == null) return;
        profile.getVariables().put(KEY_PREFIX + category.toLowerCase(), amount);
    }

    public void addPoints(UUID playerUuid, String category, int amount) {
        setPoints(playerUuid, category, getPoints(playerUuid, category) + amount);
    }

    public void takePoints(UUID playerUuid, String category, int amount) {
        setPoints(playerUuid, category, Math.max(0, getPoints(playerUuid, category) - amount));
    }

    private ValmoraProfile getProfile(UUID uuid) {
        ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(uuid);
        return vp != null ? vp.getActiveProfile() : null;
    }
}
