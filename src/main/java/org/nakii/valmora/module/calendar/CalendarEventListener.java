package org.nakii.valmora.module.calendar;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.time.TimeSnapshot;
import org.nakii.valmora.module.time.event.ValmoraDayChangeEvent;

import java.util.ArrayList;
import java.util.List;

public class CalendarEventListener implements Listener {

    private final CalendarEventModule module;

    public CalendarEventListener(CalendarEventModule module) {
        this.module = module;
    }

    @EventHandler
    public void onDayChange(ValmoraDayChangeEvent event) {
        TimeSnapshot snapshot = event.getSnapshot();
        // Null caster and location — only server-wide events (like foreach @all) should be used here
        var ctx = new SimpleExecutionContext(null, (org.bukkit.Location) null, new YamlConfiguration());

        List<String> started = new ArrayList<>();
        List<String> ended = new ArrayList<>();

        for (CalendarEventDefinition def : module.getDefinitions()) {
            boolean wasActive = module.getActiveEventIds().contains(def.getId());
            boolean isNowActive = def.isActive(snapshot);

            if (!wasActive && isNowActive) {
                started.add(def.getId());
            } else if (wasActive && !isNowActive) {
                ended.add(def.getId());
            }
        }

        // Fire on-end for events that just ended
        for (String id : ended) {
            module.getActiveEventIds().remove(id);
            CalendarEventDefinition def = getDefinition(id);
            if (def != null) def.getOnEnd().execute(ctx);
        }

        // Fire on-start for events that just started
        for (String id : started) {
            module.getActiveEventIds().add(id);
            CalendarEventDefinition def = getDefinition(id);
            if (def != null) def.getOnStart().execute(ctx);
        }

        // Fire recurring-daily for all currently active events
        for (String id : module.getActiveEventIds()) {
            CalendarEventDefinition def = getDefinition(id);
            if (def != null) def.getRecurringDaily().execute(ctx);
        }
    }

    private CalendarEventDefinition getDefinition(String id) {
        return module.getDefinition(id);
    }
}
