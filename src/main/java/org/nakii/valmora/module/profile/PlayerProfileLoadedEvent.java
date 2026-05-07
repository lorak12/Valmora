package org.nakii.valmora.module.profile;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerProfileLoadedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final ValmoraPlayer valmoraPlayer;

    public PlayerProfileLoadedEvent(UUID uuid, ValmoraPlayer valmoraPlayer) {
        this.uuid = uuid;
        this.valmoraPlayer = valmoraPlayer;
    }

    public UUID getUuid() {
        return uuid;
    }

    public ValmoraPlayer getValmoraPlayer() {
        return valmoraPlayer;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
