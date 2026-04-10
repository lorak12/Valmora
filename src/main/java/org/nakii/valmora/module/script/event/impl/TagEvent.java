package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Event for adding or removing tags from a player.
 * DSL: tag <add/remove> <tagName>
 */
public class TagEvent implements EventFactory {

    @Override
    public String getName() {
        return "tag";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return context -> {};

        String action = args[0];
        String tagName = args[1];

        return context -> {
            context.getPlayerCaster()
                    .map(Player::getUniqueId)
                    .map(uuid -> ValmoraAPI.getInstance().getPlayerManager().getSession(uuid).getActiveProfile())
                    .ifPresent(profile -> {
                        if (action.equalsIgnoreCase("add")) {
                            profile.getTags().add(tagName);
                        } else if (action.equalsIgnoreCase("remove")) {
                            profile.getTags().remove(tagName);
                        }
                    });
        };
    }
}
