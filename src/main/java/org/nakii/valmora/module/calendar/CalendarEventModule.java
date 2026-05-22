package org.nakii.valmora.module.calendar;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.time.Phase;
import org.nakii.valmora.module.time.Season;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CalendarEventModule implements ReloadableModule {

    private final Valmora plugin;
    private final Map<String, CalendarEventDefinition> definitions = new HashMap<>();
    private final Set<String> activeEventIds = new HashSet<>();
    private CalendarEventListener listener;

    public CalendarEventModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        definitions.clear();
        activeEventIds.clear();
        loadDefinitions();

        this.listener = new CalendarEventListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Initialize active set based on current time (no start events fired on load)
        var tm = plugin.getTimeManager();
        if (tm != null) {
            var snapshot = tm.getSnapshot();
            for (CalendarEventDefinition def : definitions.values()) {
                if (def.isActive(snapshot)) {
                    activeEventIds.add(def.getId());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        definitions.clear();
        activeEventIds.clear();
    }

    @Override
    public String getId() { return "calendar"; }

    @Override
    public String getName() { return "Calendar Events"; }

    public Collection<CalendarEventDefinition> getDefinitions() {
        return definitions.values();
    }

    public CalendarEventDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    public Set<String> getActiveEventIds() {
        return activeEventIds;
    }

    private void loadDefinitions() {
        YamlLoader<CalendarEventDefinition> loader = new YamlLoader<>(plugin, "calendar", "Calendar Event");
        loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
    }

    private LoadResult<CalendarEventDefinition, String> parseDefinition(String id, ConfigurationSection section, String filePath) {
        try {
            ConfigurationSection triggerSec = section.getConfigurationSection("trigger");
            Season season = null;
            Phase phase = null;
            int dayStart = 1;
            int dayEnd = 30;

            if (triggerSec != null) {
                if (triggerSec.contains("season")) {
                    try {
                        season = Season.valueOf(triggerSec.getString("season").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return LoadResult.failure("[" + filePath + "] Calendar event '" + id + "': invalid season.");
                    }
                }
                if (triggerSec.contains("phase")) {
                    try {
                        phase = Phase.valueOf(triggerSec.getString("phase").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return LoadResult.failure("[" + filePath + "] Calendar event '" + id + "': invalid phase.");
                    }
                }
                dayStart = triggerSec.getInt("day-start", 1);
                dayEnd = triggerSec.getInt("day-end", 30);
            }

            var parser = plugin.getScriptModule().getEventParser();
            CompiledEvent onStart = section.contains("on-start")
                    ? parser.parseList(section.getStringList("on-start"))
                    : ctx -> {};
            CompiledEvent onEnd = section.contains("on-end")
                    ? parser.parseList(section.getStringList("on-end"))
                    : ctx -> {};
            CompiledEvent recurringDaily = section.contains("recurring-daily")
                    ? parser.parseList(section.getStringList("recurring-daily"))
                    : ctx -> {};

            return LoadResult.success(new CalendarEventDefinition(id, season, phase, dayStart, dayEnd,
                    onStart, onEnd, recurringDaily));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse calendar event '" + id + "': " + e.getMessage());
        }
    }
}
