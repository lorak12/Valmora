package org.nakii.valmora.module.script.tag;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.TagService;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Implementation of TagService for managing and checking tags on player profiles.
 */
public class TagServiceImpl implements TagService {

    private final ExecutionContext context;

    public TagServiceImpl(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public boolean hasTag(String tag) {
        ValmoraProfile profile = getProfile();
        return profile != null && profile.getTags().contains(tag);
    }

    @Override
    public void addTag(String tag) {
        ValmoraProfile profile = getProfile();
        if (profile != null) {
            profile.getTags().add(tag);
        }
    }

    @Override
    public void removeTag(String tag) {
        ValmoraProfile profile = getProfile();
        if (profile != null) {
            profile.getTags().remove(tag);
        }
    }

    private ValmoraProfile getProfile() {
        return context.getPlayerCaster()
                .map(Player::getUniqueId)
                .map(uuid -> ValmoraAPI.getInstance().getPlayerManager().getSession(uuid).getActiveProfile())
                .orElse(null);
    }
}
