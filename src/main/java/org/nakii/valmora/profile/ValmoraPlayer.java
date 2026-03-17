package org.nakii.valmora.profile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ValmoraPlayer {
    private UUID uuid;
    private final Map<UUID, ValmoraProfile> profiles = new HashMap<>();
    private UUID activeProfileId;

    public ValmoraPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public void addProfile(ValmoraProfile profile){
        profiles.put(profile.getId(), profile);
        if (activeProfileId == null) {
            activeProfileId = profile.getId();
        }
    }

    public void removeProfile(UUID profileId) {
        profiles.remove(profileId);
    }

    public void setActiveProfile(UUID profileId) {
        if (profiles.containsKey(profileId)) {
            activeProfileId = profileId;
        }
    }

    public ValmoraProfile getActiveProfile() {
        return profiles.get(activeProfileId);
    }

    public Map<UUID, ValmoraProfile> getProfiles() {
        return profiles;
    }
    public UUID getUuid() {
        return uuid;
    }
}
