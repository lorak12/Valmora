package org.nakii.valmora.module.time.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.module.time.Phase;
import org.nakii.valmora.module.time.Season;
import org.nakii.valmora.module.time.TimeSnapshot;

public class ValmoraSeasonChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TimeSnapshot snapshot;
    private final boolean isNewSeason;
    private final boolean isNewYear;

    public ValmoraSeasonChangeEvent(TimeSnapshot snapshot, boolean isNewSeason, boolean isNewYear) {
        this.snapshot = snapshot;
        this.isNewSeason = isNewSeason;
        this.isNewYear = isNewYear;
    }

    public TimeSnapshot getSnapshot() {
        return snapshot;
    }

    public Season getNewSeason() {
        return snapshot.season();
    }

    public Phase getNewPhase() {
        return snapshot.phase();
    }

    public boolean isNewSeason() {
        return isNewSeason;
    }

    public boolean isNewYear() {
        return isNewYear;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
