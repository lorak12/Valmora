package org.nakii.valmora.module.script.condition;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

/**
 * Condition that checks if a player has a specific tag.
 */
public record TagCondition(String tag) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        return context.getPlayerCaster()
                .map(Player::getUniqueId)
                .map(uuid -> ValmoraAPI.getInstance().getPlayerManager().getSession(uuid).getActiveProfile())
                .map(profile -> profile.getTags().contains(tag))
                .orElse(false);
    }
}
