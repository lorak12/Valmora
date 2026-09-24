package org.nakii.valmora.module.calendar;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.time.Phase;
import org.nakii.valmora.module.time.Season;
import org.nakii.valmora.module.time.TimeSnapshot;

public class CalendarEventDefinition {

    private final String id;
    private final Season season;  // null = any season
    private final Phase phase;    // null = any phase
    private final int dayStart;
    private final int dayEnd;
    private final CompiledEvent onStart;
    private final CompiledEvent onEnd;
    private final CompiledEvent recurringDaily;
    /** Raw on-end script lines, persisted while the event is active so its cleanup can still run
     *  if the event is edited or deleted before it ends. */
    private final java.util.List<String> onEndLines;

    public CalendarEventDefinition(String id, Season season, Phase phase, int dayStart, int dayEnd,
                                    CompiledEvent onStart, CompiledEvent onEnd, CompiledEvent recurringDaily) {
        this(id, season, phase, dayStart, dayEnd, onStart, onEnd, recurringDaily, java.util.List.of());
    }

    public CalendarEventDefinition(String id, Season season, Phase phase, int dayStart, int dayEnd,
                                    CompiledEvent onStart, CompiledEvent onEnd, CompiledEvent recurringDaily,
                                    java.util.List<String> onEndLines) {
        this.onEndLines = onEndLines == null ? java.util.List.of() : java.util.List.copyOf(onEndLines);
        this.id = id;
        this.season = season;
        this.phase = phase;
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.onStart = onStart;
        this.onEnd = onEnd;
        this.recurringDaily = recurringDaily;
    }

    public String getId() { return id; }
    public Season getSeason() { return season; }
    public Phase getPhase() { return phase; }
    public int getDayStart() { return dayStart; }
    public int getDayEnd() { return dayEnd; }
    public CompiledEvent getOnStart() { return onStart; }
    public CompiledEvent getOnEnd() { return onEnd; }
    public CompiledEvent getRecurringDaily() { return recurringDaily; }
    public java.util.List<String> getOnEndLines() { return onEndLines; }

    public boolean isActive(TimeSnapshot snapshot) {
        if (season != null && season != snapshot.season()) return false;
        if (phase != null && phase != snapshot.phase()) return false;
        return snapshot.dayInPhase() >= dayStart && snapshot.dayInPhase() <= dayEnd;
    }
}
