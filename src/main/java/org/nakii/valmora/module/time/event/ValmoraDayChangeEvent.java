package org.nakii.valmora.module.time.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.module.time.TimeSnapshot;

public class ValmoraDayChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TimeSnapshot snapshot;

    public ValmoraDayChangeEvent(TimeSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public TimeSnapshot getSnapshot() {
        return snapshot;
    }

    public int getNewDay() {
        return snapshot.dayInPhase();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
