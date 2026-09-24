package org.nakii.valmora.module.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardUI {

    private static final int MAX_LINES = 16;
    private static final String[] LINE_ENTRIES = new String[MAX_LINES];

    static {
        // HC-180 fix: each sidebar line needs a unique scoreboard "entry" (score owner) that
        // renders as nothing next to the real line text (which is set separately via
        // Team#prefix — a Component, correctly built through Formatter/MiniMessage elsewhere in
        // this class). A bare legacy color code with no following text is the only entry shape
        // that reliably renders invisible in the sidebar, so this can't be replaced with a plain
        // unique string (e.g. "line_0") without the raw entry text becoming visible. This used to
        // build that code with a raw "§" literal, violating CLAUDE.md §7.5's "never use ChatColor
        // or § codes" rule; it now goes through org.bukkit.ChatColor's own (non-deprecated)
        // COLOR_CHAR constant instead of a magic-character literal in this file.
        org.bukkit.ChatColor[] codes = org.bukkit.ChatColor.values();
        for (int i = 0; i < MAX_LINES; i++) {
            LINE_ENTRIES[i] = org.bukkit.ChatColor.COLOR_CHAR + String.valueOf(codes[i].getChar());
        }
    }

    private final Valmora plugin;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();
    private final Map<UUID, DynamicSection> dynamicSections = new HashMap<>();
    /** Per-player opt-out, toggled by `/ui toggle` (added 2026-08-07). Session-only — resets on rejoin. */
    private final java.util.Set<UUID> hidden = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private UIConfig config;

    public ScoreboardUI(Valmora plugin) {
        this.plugin = plugin;
    }

    public void setConfig(UIConfig config) {
        this.config = config;
    }

    private static class DynamicSection {
        final List<Component> lines;
        boolean locked;

        DynamicSection(List<Component> lines, boolean locked) {
            this.lines = new ArrayList<>(lines);
            this.locked = locked;
        }
    }

    public void setDynamicSection(Player player, List<Component> lines, boolean locked) {
        UUID uuid = player.getUniqueId();
        DynamicSection current = dynamicSections.get(uuid);
        if (current != null && current.locked && !lines.isEmpty()) return;

        if (lines.isEmpty()) {
            dynamicSections.remove(uuid);
        } else {
            dynamicSections.put(uuid, new DynamicSection(lines, locked));
        }
    }

    public void setDynamicSection(Player player, List<String> rawLines, boolean locked, boolean miniMessage) {
        setDynamicSection(player, rawLines.stream().map(Formatter::format).toList(), locked);
    }

    public void removePlayer(UUID uuid) {
        playerBoards.remove(uuid);
        playerObjectives.remove(uuid);
        dynamicSections.remove(uuid);
    }

    /** Toggles the sidebar scoreboard for a player. Returns the new state (true = now hidden). */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (hidden.remove(uuid)) return false;
        hidden.add(uuid);
        // Clear immediately rather than waiting for the next tick to notice.
        Scoreboard board = playerBoards.get(uuid);
        if (board != null) player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        playerBoards.remove(uuid);
        playerObjectives.remove(uuid);
        return true;
    }

    public boolean isHidden(UUID uuid) { return hidden.contains(uuid); }

    public void tick(Player player) {
        UUID uuid = player.getUniqueId();
        if (hidden.contains(uuid)) return;

        Scoreboard board = playerBoards.computeIfAbsent(uuid, k -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = b.registerNewObjective("valmora_hud", "dummy", "unused");
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

        // Update title from config every tick so reloads take effect immediately
        String titleTemplate = config != null ? config.getScoreboardTitle() : "<gold><bold>VALMORA RPG";
        obj.displayName(Formatter.format(titleTemplate));

        updateCombatLockSection(player);

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

        // Tab list header/footer
        if (config != null && (!config.getTabHeader().isEmpty() || !config.getTabFooter().isEmpty())) {
            ExecutionContext ctx = playerContext(player);
            VariableResolver resolver = resolverOrNull();
            if (resolver != null) {
                player.sendPlayerListHeaderAndFooter(
                        Formatter.format(resolver.resolveTemplate(config.getTabHeader(), ctx)),
                        Formatter.format(resolver.resolveTemplate(config.getTabFooter(), ctx)));
            }
        }
    }

    /**
     * First real consumer of the {@code "$dynamic$"} placeholder (fixed 2026-08-07 — the
     * machinery existed but nothing ever called {@link #setDynamicSection}, so it always rendered
     * as nothing). Shows a "combat lock" line for the remaining duration of
     * {@code PlayerState.isInCombat()}; other systems (dialogue state, quest tracker, …) can
     * layer in the same way by calling {@code setDynamicSection} themselves — this only owns the
     * combat-lock line specifically.
     */
    private void updateCombatLockSection(Player player) {
        try {
            var vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
            var profile = vp != null ? vp.getActiveProfile() : null;
            if (profile == null) return;
            var state = profile.getPlayerState();
            if (!state.isInCombat()) {
                setDynamicSection(player, List.of(), false);
                return;
            }
            long windowMs = plugin.getConfig().getLong("combat.combat-window-ms", 3000);
            long remainingMs = windowMs - (System.currentTimeMillis() - state.getLastCombatTime());
            long remainingSec = Math.max(1, (remainingMs + 999) / 1000);
            setDynamicSection(player, List.of(Formatter.format("<red>⚔ Combat: <white>" + remainingSec + "s")), false);
        } catch (Exception ignored) {}
    }

    private List<Component> buildLines(Player player) {
        List<Component> lines = new ArrayList<>();

        if (config == null || config.getScoreboardLines().isEmpty()) {
            return legacyLines(player);
        }

        ExecutionContext ctx = playerContext(player);
        VariableResolver resolver = resolverOrNull();
        DynamicSection dynamic = dynamicSections.get(player.getUniqueId());

        for (String template : config.getScoreboardLines()) {
            // Dynamic section placeholder
            if (template.equals("$dynamic$")) {
                if (dynamic != null && !dynamic.lines.isEmpty()) {
                    lines.addAll(dynamic.lines);
                    lines.add(Component.empty());
                }
                continue;
            }
            if (template.isEmpty()) {
                lines.add(Component.empty());
                continue;
            }
            String resolved = resolver != null ? resolver.resolveTemplate(template, ctx) : template;
            lines.add(Formatter.format(resolved));
        }

        return lines;
    }

    // Fallback when config hasn't loaded yet (e.g. very early tick before UIManager finishes onEnable)
    private List<Component> legacyLines(Player player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Formatter.format("<gold><bold>VALMORA RPG"));
        lines.add(Component.empty());

        try {
            var tm = ValmoraAPI.getInstance().getTimeManager();
            if (tm != null && tm.isScoreboardEnabled()) {
                var snap = tm.getSnapshot();
                lines.add(Formatter.format("<aqua>⏰ <white>" + snap.formattedTime()
                        + "  " + snap.timeOfDayMiniColor() + snap.timeOfDayEmote()
                        + " <gold>" + snap.phaseName() + " " + snap.seasonName()));
                lines.add(Formatter.format("<gray>Day <white>" + snap.dayInPhase()
                        + "  <dark_gray>│  <gray>Year <white>" + snap.year()));
                lines.add(Component.empty());
            }
        } catch (Exception ignored) {}

        DynamicSection dynamic = dynamicSections.get(player.getUniqueId());
        if (dynamic != null && !dynamic.lines.isEmpty()) {
            lines.addAll(dynamic.lines);
            lines.add(Component.empty());
        }

        try {
            var pm = ValmoraAPI.getInstance().getPlayerManager();
            if (pm != null) {
                var session = pm.getSession(player.getUniqueId());
                if (session != null && session.getActiveProfile() != null)
                    lines.add(Formatter.format("<gray>Profile: <yellow>" + session.getActiveProfile().getName()));
            }
        } catch (Exception ignored) {}

        try {
            var zm = ValmoraAPI.getInstance().getZoneManager();
            String zoneLine = zm != null ? zm.getCurrentZone(player)
                    .map(z -> z.getDisplayName()).orElse(org.nakii.valmora.module.zone.ZoneVariableProvider.wildernessName())
                    : org.nakii.valmora.module.zone.ZoneVariableProvider.wildernessName();
            lines.add(Formatter.format("<gray>Zone: " + zoneLine));
        } catch (Exception ignored) {}

        try {
            var eco = ValmoraAPI.getInstance().getEconomyModule();
            if (eco != null)
                lines.add(Formatter.format("<gray>Purse: <gold>" +
                        org.nakii.valmora.module.economy.EconomyModule.formatCoinsDisplay(eco.getPurse(player.getUniqueId()))));
        } catch (Exception ignored) {}

        return lines;
    }

    private ExecutionContext playerContext(Player player) {
        return new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
    }

    private VariableResolver resolverOrNull() {
        try {
            var api = ValmoraAPI.getInstance();
            if (api == null) return null;
            var script = api.getScriptModule();
            if (script == null) return null;
            return script.getVariableResolver();
        } catch (Exception e) {
            return null;
        }
    }
}
