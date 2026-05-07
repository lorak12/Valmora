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

    private BukkitTask dayCheckTask;

    public TimeManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        FileConfiguration cfg = plugin.getConfig();
        worldName = cfg.getString("time.world", "world");
        seasonNames = cfg.getStringList("time.season-names");
        phaseNames = cfg.getStringList("time.phase-names");

        File timeFile = new File(plugin.getDataFolder(), "time.yml");
        if (timeFile.exists()) {
            YamlConfiguration tc = YamlConfiguration.loadConfiguration(timeFile);
            dayOffset = tc.getLong("day-offset", computeInitialOffset());
        } else {
            dayOffset = computeInitialOffset();
            save();
        }

        TimeSnapshot initial = getSnapshot();
        lastPhase = initial.phase();
        lastSeason = initial.season();
        World world = Bukkit.getWorld(worldName);
        lastWorldDay = world != null ? world.getFullTime() / 24000 : 0;

        dayCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("Time loaded: " + initial.phaseName() + " " + initial.seasonName()
                + ", Day " + initial.dayInPhase() + ", Year " + initial.year()
                + " (" + initial.formattedTime() + ")");
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

            if (daySnap.phase() != lastPhase || daySnap.season() != lastSeason) {
                boolean isNewSeason = daySnap.season() != lastSeason;
                boolean isNewYear = isNewSeason
                        && daySnap.season() == Season.SPRING
                        && daySnap.phase() == Phase.EARLY;

                plugin.getServer().getPluginManager()
                        .callEvent(new ValmoraSeasonChangeEvent(daySnap, isNewSeason, isNewYear));

                lastPhase = daySnap.phase();
                lastSeason = daySnap.season();

                if (isNewSeason) {
                    notifySeasonChange(daySnap);
                }
            }
        }
    }

    private void notifySeasonChange(TimeSnapshot snap) {
        String msg = "<gold><bold>✦ A new season begins — "
                + snap.phaseName() + " " + snap.seasonName() + " ✦</bold></gold>";
        var ui = ValmoraAPI.getInstance().getUIManager();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ui.getActionBar().showTemporary(p, msg, 120);
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

    public void save() {
        File timeFile = new File(plugin.getDataFolder(), "time.yml");
        YamlConfiguration tc = new YamlConfiguration();
        tc.set("day-offset", dayOffset);
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
