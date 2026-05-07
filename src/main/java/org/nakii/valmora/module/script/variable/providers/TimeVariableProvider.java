package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.module.time.TimeManager;
import org.nakii.valmora.module.time.TimeSnapshot;

public class TimeVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "time";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        TimeManager tm = ValmoraAPI.getInstance().getTimeManager();
        if (tm == null) return null;

        TimeSnapshot snap = tm.getSnapshot();
        return switch (path[0].toLowerCase()) {
            case "hour"          -> snap.hour();
            case "minute"        -> snap.minute();
            case "day"           -> snap.dayInPhase();
            case "phase"         -> snap.phaseName();
            case "season"        -> snap.seasonName();
            case "year"          -> snap.year();
            case "total_days"    -> snap.totalDays();
            case "total_minutes" -> snap.totalDays() * 24 * 60 + snap.hour() * 60 + snap.minute();
            case "is_day"        -> snap.isDay();
            case "time_of_day"   -> snap.timeOfDayEmote() + " " + (snap.isDay() ? "Day" : "Night");
            default              -> null;
        };
    }
}
