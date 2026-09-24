package org.nakii.valmora.module.time;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.time.event.ValmoraDayChangeEvent;
import org.nakii.valmora.module.time.event.ValmoraSeasonChangeEvent;
import org.nakii.valmora.module.time.event.ValmoraTimeTickEvent;
import org.nakii.valmora.util.DebugManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TimeManager {

    private final Valmora plugin;

    private String worldName;
    private List<String> seasonNames;
    private List<String> phaseNames;

    private long dayOffset;
    private long lastWorldDay = -1;
    private Phase lastPhase;
    private Season lastSeason;
    private boolean scoreboardEnabled = true;

    private BukkitTask dayCheckTask;

    public TimeManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        FileConfiguration cfg = plugin.getConfig();
        worldName = cfg.getString("time.world", "world");
        seasonNames = cfg.getStringList("time.season-names");
        phaseNames = cfg.getStringList("time.phase-names");
        scoreboardEnabled = cfg.getBoolean("time.scoreboard-enabled", true);

        File timeFile = new File(plugin.getDataFolder(), "time.yml");
        Long lastKnownWorldDay = null;
        if (timeFile.exists()) {
            try {
                YamlConfiguration tc = YamlConfiguration.loadConfiguration(timeFile);
                dayOffset = tc.getLong("day-offset", computeInitialOffset());
                // Offline/skip catch-up marker (added 2026-08-07) — see reconcileMissedTransitions().
                if (tc.contains("last-world-day")) lastKnownWorldDay = tc.getLong("last-world-day");
            } catch (Exception e) {
                // A malformed time.yml previously aborted onEnable() entirely (unguarded
                // YamlConfiguration.loadConfiguration). Degrade to the config-derived offset instead.
                plugin.getLogger().warning("Failed to read time.yml (" + e.getMessage() + ") — falling back to the configured start date.");
                dayOffset = computeInitialOffset();
            }
        } else {
            dayOffset = computeInitialOffset();
            save();
        }

        TimeSnapshot initial = getSnapshot();
        lastPhase = initial.phase();
        lastSeason = initial.season();
        World world = Bukkit.getWorld(worldName);
        long currentWorldDay = world != null ? world.getFullTime() / 24000 : 0;

        // Offline/skip catch-up: under normal operation world time never advances while the
        // server process itself is down, so this is mainly a safety net for external time
        // manipulation (another plugin/command fast-forwarding the world clock, or a
        // hand-edited time.yml) rather than something that fires on every restart.
        if (lastKnownWorldDay != null && lastKnownWorldDay < currentWorldDay) {
            // Deferred one tick: the time module enables second, so firing now would reach none of
            // the later modules' listeners (calendar, quests, ...) — they'd miss the catch-up.
            final long fromDay = lastKnownWorldDay;
            Bukkit.getScheduler().runTask(plugin, () -> reconcileMissedTransitions(fromDay));
        }
        lastWorldDay = currentWorldDay;

        dayCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("Time loaded: " + initial.phaseName() + " " + initial.seasonName()
                + ", Day " + initial.dayInPhase() + ", Year " + initial.year()
                + " (" + initial.formattedTime() + ")");
    }

    /** Whether the sidebar scoreboard's built-in fallback time display is enabled ({@code time.scoreboard-enabled}, default true). Only affects {@code ScoreboardUI}'s legacy pre-config-load fallback — the normal `ui.yml`-driven scoreboard has full admin control over which lines (if any) show time. */
    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    /**
     * Fires the day-change/season-change events once for the *net* transition between the last
     * day this module observed and now — not a full day-by-day replay (an intermediate day's
     * events, if any were skipped, are not individually replayed). Mirrors the Calendar module's
     * `reconcileMissedTransitions` (see docs/modules/design/calendar.md §3.1/§5).
     */
    private void reconcileMissedTransitions(long fromWorldDay) {
        TimeSnapshot fromSnap = snapshotForWorldDay(fromWorldDay);
        TimeSnapshot toSnap = getSnapshot();

        plugin.getServer().getPluginManager().callEvent(new ValmoraDayChangeEvent(toSnap));

        if (toSnap.phase() != fromSnap.phase() || toSnap.season() != fromSnap.season()) {
            boolean isNewSeason = toSnap.season() != fromSnap.season();
            boolean isNewYear = isNewSeason && toSnap.season() == Season.SPRING && toSnap.phase() == Phase.EARLY;
            plugin.getServer().getPluginManager().callEvent(new ValmoraSeasonChangeEvent(toSnap, isNewSeason, isNewYear));
        }
        plugin.getLogger().info("[Time] Reconciled a " + (getWorldDay() - fromWorldDay) + "-day gap on load — fired catch-up day/season events.");
    }

    private long getWorldDay() {
        World world = Bukkit.getWorld(worldName);
        return world != null ? world.getFullTime() / 24000 : 0;
    }

    /** Reconstructs a {@link TimeSnapshot} for an arbitrary world-day count (hour/minute pinned to midday — only phase/season/dayInPhase matter for the catch-up comparison). */
    private TimeSnapshot snapshotForWorldDay(long worldDay) {
        long totalDays = worldDay + dayOffset;
        int dayInPhase = (int) Math.floorMod(totalDays, 30) + 1;
        Phase phase = Phase.values()[(int) Math.floorMod(totalDays / 30, 3)];
        Season season = Season.values()[(int) Math.floorMod(totalDays / 90, 4)];
        int year = Math.max(1, (int) (totalDays / 360) + 1);
        String phaseName = phase.ordinal() < phaseNames.size() ? phaseNames.get(phase.ordinal()) : capitalize(phase.name());
        String seasonName = season.ordinal() < seasonNames.size() ? seasonNames.get(season.ordinal()) : capitalize(season.name());
        return new TimeSnapshot(12, 0, dayInPhase, phase, season, year, totalDays, phaseName, seasonName);
    }

    public void onDisable() {
        if (dayCheckTask != null) {
            dayCheckTask.cancel();
            dayCheckTask = null;
        }
        save();
    }

    private void tick() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        TimeSnapshot snap = getSnapshot();
        plugin.getServer().getPluginManager().callEvent(new ValmoraTimeTickEvent(snap));

        long currentWorldDay = world.getFullTime() / 24000;
        if (lastWorldDay < 0) {
            lastWorldDay = currentWorldDay;
            return;
        }

        if (currentWorldDay > lastWorldDay) {
            lastWorldDay = currentWorldDay;
            TimeSnapshot daySnap = getSnapshot();

            plugin.getServer().getPluginManager().callEvent(new ValmoraDayChangeEvent(daySnap));
            // Not per-tick: tick() runs once a second, but a day only rolls over here — this stays
            // usable instead of spamming one line/second like the raw ValmoraTimeTickEvent would.
            DebugManager.log("time", "day changed: " + daySnap.seasonName() + " " + daySnap.phaseName()
                    + " Day " + daySnap.dayInPhase() + ", Year " + daySnap.year());

            if (daySnap.phase() != lastPhase || daySnap.season() != lastSeason) {
                boolean isNewSeason = daySnap.season() != lastSeason;
                boolean isNewYear = isNewSeason
                        && daySnap.season() == Season.SPRING
                        && daySnap.phase() == Phase.EARLY;

                plugin.getServer().getPluginManager()
                        .callEvent(new ValmoraSeasonChangeEvent(daySnap, isNewSeason, isNewYear));
                DebugManager.log("time", "season/phase changed -> " + daySnap.seasonName() + " " + daySnap.phaseName()
                        + " (newSeason=" + isNewSeason + ", newYear=" + isNewYear + ")");

                lastPhase = daySnap.phase();
                lastSeason = daySnap.season();

                if (isNewSeason) {
                    notifySeasonChange(daySnap);
                }
            }

            // Keep the catch-up marker current so a future onEnable() only ever needs to
            // reconcile a genuine gap, not every normal day-change.
            save();
        }
    }

    private void notifySeasonChange(TimeSnapshot snap) {
        String msg = "<gold><bold>✦ A new season begins — "
                + snap.phaseName() + " " + snap.seasonName() + " ✦</bold></gold>";
        var ui = ValmoraAPI.getInstance().getUIManager();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ui.getActionBar().showTemporary(p, msg, 120, 1);
        }
    }

    public TimeSnapshot getSnapshot() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return TimeSnapshot.EPOCH;

        long mcTick = world.getTime();
        long totalDays = world.getFullTime() / 24000 + dayOffset;

        int hour = (int) ((mcTick / 1000 + 6) % 24);
        int minute = (int) ((mcTick % 1000) * 60 / 1000);
        int dayInPhase = (int) (Math.floorMod(totalDays, 30)) + 1;
        Phase phase = Phase.values()[(int) (Math.floorMod(totalDays / 30, 3))];
        Season season = Season.values()[(int) (Math.floorMod(totalDays / 90, 4))];
        int year = Math.max(1, (int) (totalDays / 360) + 1);

        String phaseName = phase.ordinal() < phaseNames.size()
                ? phaseNames.get(phase.ordinal()) : capitalize(phase.name());
        String seasonName = season.ordinal() < seasonNames.size()
                ? seasonNames.get(season.ordinal()) : capitalize(season.name());

        return new TimeSnapshot(hour, minute, dayInPhase, phase, season, year, totalDays, phaseName, seasonName);
    }

    public void resetOffset() {
        dayOffset = computeInitialOffset();
        TimeSnapshot snap = getSnapshot();
        lastPhase = snap.phase();
        lastSeason = snap.season();
        save();
    }

    /** Jumps the calendar to an explicit date — backs {@code /time set}. {@code day} is 1-indexed day-of-phase, clamped to `[1, 30]`. */
    public void setDate(int year, Season season, Phase phase, int day) {
        int clampedDay = Math.max(1, Math.min(30, day));
        long targetDays = (long) (Math.max(1, year) - 1) * 360
                + season.ordinal() * 90L
                + phase.ordinal() * 30L
                + (clampedDay - 1);
        long currentWorldDays = getWorldDay();
        dayOffset = targetDays - currentWorldDays;

        TimeSnapshot snap = getSnapshot();
        lastPhase = snap.phase();
        lastSeason = snap.season();
        lastWorldDay = currentWorldDays;
        save();
    }

    public void save() {
        File timeFile = new File(plugin.getDataFolder(), "time.yml");
        YamlConfiguration tc = new YamlConfiguration();
        tc.set("day-offset", dayOffset);
        // Catch-up marker (added 2026-08-07) — see reconcileMissedTransitions().
        tc.set("last-world-day", lastWorldDay >= 0 ? lastWorldDay : getWorldDay());
        try {
            tc.save(timeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save time.yml: " + e.getMessage());
        }
    }

    private long computeInitialOffset() {
        FileConfiguration cfg = plugin.getConfig();
        int startYear = cfg.getInt("time.start-year", 1);
        Season startSeason = parseSeason(cfg.getString("time.start-season", "SPRING"));
        Phase startPhase = parsePhase(cfg.getString("time.start-phase", "EARLY"));
        int startDay = Math.max(1, cfg.getInt("time.start-day", 1));

        long targetDays = (long) (startYear - 1) * 360
                + startSeason.ordinal() * 90L
                + startPhase.ordinal() * 30L
                + (startDay - 1);

        World world = Bukkit.getWorld(worldName);
        long currentWorldDays = world != null ? world.getFullTime() / 24000 : 0;
        return targetDays - currentWorldDays;
    }

    private static Season parseSeason(String s) {
        try { return Season.valueOf(s.toUpperCase()); } catch (Exception e) { return Season.SPRING; }
    }

    private static Phase parsePhase(String s) {
        try { return Phase.valueOf(s.toUpperCase()); } catch (Exception e) { return Phase.EARLY; }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
