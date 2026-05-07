package org.nakii.valmora.module.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.time.TimeManager;
import org.nakii.valmora.module.time.TimeSnapshot;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardUI {

    private static final int MAX_LINES = 16;
    // Pre-built unique invisible entries (§0..§9, §a..§f)
    private static final String[] LINE_ENTRIES = new String[MAX_LINES];

    static {
        String chars = "0123456789abcdef";
        for (int i = 0; i < MAX_LINES; i++) {
            LINE_ENTRIES[i] = "§" + chars.charAt(i);
        }
    }

    private final Valmora plugin;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();
    private final Map<UUID, DynamicSection> dynamicSections = new HashMap<>();

    public ScoreboardUI(Valmora plugin) {
        this.plugin = plugin;
    }

    private static class DynamicSection {
        final List<Component> lines;
        boolean locked;

        DynamicSection(List<Component> lines, boolean locked) {
            this.lines = new ArrayList<>(lines);
            this.locked = locked;
        }
    }

    /**
     * Sets the flexible part of the scoreboard using Components.
     *
     * @param locked If true, subsequent calls with non-empty lines will be ignored until unlocked.
     */
    public void setDynamicSection(Player player, List<Component> lines, boolean locked) {
        UUID uuid = player.getUniqueId();

        DynamicSection current = dynamicSections.get(uuid);
        if (current != null && current.locked && !lines.isEmpty()) {
            return;
        }

        if (lines.isEmpty()) {
            dynamicSections.remove(uuid);
        } else {
            dynamicSections.put(uuid, new DynamicSection(lines, locked));
        }
    }

    /** Convenience overload that accepts MiniMessage strings. */
    public void setDynamicSection(Player player, List<String> rawLines, boolean locked, boolean miniMessage) {
        setDynamicSection(player, rawLines.stream().map(Formatter::format).toList(), locked);
    }

    public void removePlayer(UUID uuid) {
        playerBoards.remove(uuid);
        playerObjectives.remove(uuid);
        dynamicSections.remove(uuid);
    }

    // Called automatically by the UIManager clock
    public void tick(Player player) {
        UUID uuid = player.getUniqueId();

        Scoreboard board = playerBoards.computeIfAbsent(uuid, k -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = b.registerNewObjective("valmora_hud", "dummy", "unused");
            obj.displayName(Formatter.format("<gold><bold>VALMORA RPG"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            playerObjectives.put(uuid, obj);

            for (int i = 0; i < MAX_LINES; i++) {
                Team t = b.registerNewTeam("valmora_line" + i);
                t.addEntry(LINE_ENTRIES[i]);
            }

            player.setScoreboard(b);
            return b;
        });

        Objective obj = playerObjectives.get(uuid);
        if (obj == null) return;

        List<Component> lines = buildLines(player);
        int lineCount = Math.min(lines.size(), MAX_LINES);

        for (int i = 0; i < lineCount; i++) {
            Team team = board.getTeam("valmora_line" + i);
            if (team == null) continue;
            team.prefix(lines.get(i));
            Score score = obj.getScore(LINE_ENTRIES[i]);
            score.setScore(lineCount - i);
        }

        for (int i = lineCount; i < MAX_LINES; i++) {
            board.resetScores(LINE_ENTRIES[i]);
        }
    }

    private List<Component> buildLines(Player player) {
        List<Component> lines = new ArrayList<>();

        lines.add(Formatter.format("<yellow>pay.valmora.net"));
        lines.add(Component.empty());

        // Time display
        TimeManager tm = ValmoraAPI.getInstance().getTimeManager();
        if (tm != null) {
            TimeSnapshot snap = tm.getSnapshot();
            lines.add(Formatter.format(
                    "<aqua>⏰ <white>" + snap.formattedTime()
                    + "  " + snap.timeOfDayMiniColor() + snap.timeOfDayEmote()
                    + " <gold>" + snap.phaseName() + " " + snap.seasonName()
            ));
            lines.add(Formatter.format(
                    "<gray>Day <white>" + snap.dayInPhase()
                    + "  <dark_gray>│  <gray>Year <white>" + snap.year()
            ));
            lines.add(Component.empty());
        }

        DynamicSection dynamic = dynamicSections.get(player.getUniqueId());
        if (dynamic != null && !dynamic.lines.isEmpty()) {
            lines.addAll(dynamic.lines);
            lines.add(Component.empty());
        }

        lines.add(Formatter.format("<gray>Zone: <green>Safezone"));
        lines.add(Formatter.format("<gray>Purse: <gold>0 Coins"));

        return lines;
    }
}
