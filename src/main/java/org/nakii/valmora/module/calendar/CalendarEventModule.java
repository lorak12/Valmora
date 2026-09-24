package org.nakii.valmora.module.calendar;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.time.Phase;
import org.nakii.valmora.module.time.Season;
import org.nakii.valmora.module.time.TimeSnapshot;

import java.io.File;
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

            // Offline/skip catch-up: reconcile against whatever day this module last saw. Under
            // normal operation totalDays only advances while the server ticks, so a gap here
            // means either the server was down across a day boundary an external time-modifying
            // plugin/command fast-forwarded time, or this is the very first run — in the latter
            // case lastProcessedDay is simply absent and nothing fires, matching the previous
            // (silent) seeding-only behavior. Only the *net* start/end transition is reconciled
            // (not a full day-by-day replay of recurring-daily) — see docs/modules/design/calendar.md §5.
            Long lastProcessedDay = loadLastProcessedDay();
            if (lastProcessedDay != null && lastProcessedDay < snapshot.totalDays()) {
                reconcileMissedTransitions(lastProcessedDay, snapshot);
            }

            for (CalendarEventDefinition def : definitions.values()) {
                if (def.isActive(snapshot)) {
                    activeEventIds.add(def.getId());
                }
            }
            saveLastProcessedDay(snapshot.totalDays());
        }
    }

    /**
     * Fires on-end/on-start for events whose active state differs between the last day this
     * module observed and the current snapshot, without replaying `recurring-daily` or requiring
     * a day-by-day walk. An event whose window recurs more than once inside the gap only gets
     * reconciled for its *net* change — a known, documented simplification (see design doc).
     */
    private void reconcileMissedTransitions(long lastTotalDays, TimeSnapshot currentSnapshot) {
        TimeSnapshot lastSnapshot = snapshotForTotalDays(lastTotalDays);
        var ctx = new SimpleExecutionContext(null, (org.bukkit.Location) null, new YamlConfiguration());
        for (CalendarEventDefinition def : definitions.values()) {
            boolean wasActive = def.isActive(lastSnapshot);
            boolean isActive = def.isActive(currentSnapshot);
            if (wasActive && !isActive) {
                plugin.getLogger().info("[Calendar] Catch-up: firing on-end for '" + def.getId() + "' (missed while offline).");
                def.getOnEnd().execute(ctx);
            } else if (!wasActive && isActive) {
                plugin.getLogger().info("[Calendar] Catch-up: firing on-start for '" + def.getId() + "' (missed while offline).");
                def.getOnStart().execute(ctx);
            }
        }
    }

    /** Reconstructs just enough of a {@link TimeSnapshot} (season/phase/dayInPhase) to evaluate
     *  {@code isActive} for an arbitrary past day — mirrors {@code TimeManager.getSnapshot()}'s
     *  math, including its {@code time.calendar.days-per-phase} (HC-253) override, so a custom
     *  calendar shape can't silently diverge between the two independent copies of this formula. */
    private TimeSnapshot snapshotForTotalDays(long totalDays) {
        int daysPerPhase = Math.max(1, plugin.getConfig().getInt("time.calendar.days-per-phase", 30));
        int daysPerSeason = daysPerPhase * Phase.values().length;
        int daysPerYear = daysPerSeason * Season.values().length;
        int dayInPhase = (int) Math.floorMod(totalDays, daysPerPhase) + 1;
        Phase phase = Phase.values()[(int) Math.floorMod(totalDays / daysPerPhase, Phase.values().length)];
        Season season = Season.values()[(int) Math.floorMod(totalDays / daysPerSeason, Season.values().length)];
        int year = Math.max(1, (int) (totalDays / daysPerYear) + 1);
        return new TimeSnapshot(6, 0, dayInPhase, phase, season, year, totalDays, phase.name(), season.name());
    }

    private File stateFile() {
        return new File(plugin.getDataFolder(), "calendar_state.yml");
    }

    private Long loadLastProcessedDay() {
        File file = stateFile();
        if (!file.exists()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return yaml.contains("last-processed-day") ? yaml.getLong("last-processed-day") : null;
    }

    /** Records the last day this module observed, so a future {@code onEnable()} can detect (and reconcile) any gap. Called on enable and after every real day-change. */
    void saveLastProcessedDay(long totalDays) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("last-processed-day", totalDays);
        try {
            yaml.save(stateFile());
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("[Calendar] Failed to save calendar_state.yml: " + e.getMessage());
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
        return id == null ? null : definitions.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public Set<String> getActiveEventIds() {
        return activeEventIds;
    }

    private void loadDefinitions() {
        YamlLoader<CalendarEventDefinition> loader = new YamlLoader<>(plugin, "calendar", "Calendar Event");
        loader.load(this::parseDefinition, def -> definitions.put(def.getId().toLowerCase(java.util.Locale.ROOT), def));
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
                // Clamped to the valid 1-30 day-of-phase range rather than silently accepting an
                // out-of-range value that would just never match (see docs/IMPLEMENTATION_BACKLOG.md,
                // Calendar module).
                dayStart = Math.max(1, Math.min(30, triggerSec.getInt("day-start", 1)));
                dayEnd = Math.max(1, Math.min(30, triggerSec.getInt("day-end", 30)));
                if (dayStart > dayEnd) {
                    return LoadResult.failure("[" + filePath + "] Calendar event '" + id
                            + "': day-start (" + dayStart + ") must be <= day-end (" + dayEnd + ").");
                }
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
