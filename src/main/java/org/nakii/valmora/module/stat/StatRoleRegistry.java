package org.nakii.valmora.module.stat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps semantic stat "roles" (what combat/mining/etc. Java code asks for, e.g. {@code "damage"},
 * {@code "defense"}) to the actual {@link StatDefinition} id backing them in {@link StatRegistry}
 * (Phase 3.1 of the refactor — see docs/REFACTOR/PROGRESS.md).
 *
 * <p>Replaces the 15 fixed `String` fields previously hardcoded in {@link SystemStats} — adding a
 * 16th role (e.g. a future {@code "mana_shield"}) no longer requires a new Java field, getter, and
 * recompile. It only requires a new entry under {@code stats/core.yml} → {@code stat_roles:}.
 *
 * <p><b>Backward compatibility (three-tier resolution, evaluated in this order):</b>
 * <ol>
 *   <li>Built-in defaults for the original 15 roles (identity mapping — role id == stat id),
 *       so a server with neither config section still behaves exactly as before.</li>
 *   <li>Legacy {@code config.yml} → {@code combat.<role>-stat}/{@code mining.<role>-stat} keys —
 *       this is what {@code SystemStats.load(FileConfiguration)} used to read directly.</li>
 *   <li>New {@code stats/core.yml} → {@code stat_roles:} section — the source of truth going
 *       forward, and the only way to register a role beyond the original 15 without code changes.</li>
 * </ol>
 */
public class StatRoleRegistry {

    private static final Map<String, String> BUILT_IN_DEFAULTS = Map.ofEntries(
            Map.entry("health", "health"),
            Map.entry("mana", "mana"),
            Map.entry("damage", "damage"),
            Map.entry("strength", "strength"),
            Map.entry("defense", "defense"),
            Map.entry("crit_chance", "crit_chance"),
            Map.entry("crit_damage", "crit_damage"),
            Map.entry("speed", "speed"),
            Map.entry("health_regen", "health_regen"),
            Map.entry("mana_regen", "mana_regen"),
            Map.entry("luck", "luck"),
            Map.entry("mining_fortune", "mining_fortune"),
            Map.entry("mining_speed", "mining_speed"),
            Map.entry("breaking_power", "breaking_power"),
            Map.entry("mining_spread", "mining_spread")
    );

    /** role -> legacy config.yml key, for the tier-2 lookup described above. */
    private static final Map<String, String> LEGACY_CONFIG_KEYS = Map.ofEntries(
            Map.entry("health", "combat.health-stat"),
            Map.entry("mana", "combat.mana-stat"),
            Map.entry("damage", "combat.damage-stat"),
            Map.entry("strength", "combat.strength-stat"),
            Map.entry("defense", "combat.defense-stat"),
            Map.entry("crit_chance", "combat.crit-chance-stat"),
            Map.entry("crit_damage", "combat.crit-damage-stat"),
            Map.entry("speed", "combat.speed-stat"),
            Map.entry("health_regen", "combat.health-regen-stat"),
            Map.entry("mana_regen", "combat.mana-regen-stat"),
            Map.entry("luck", "combat.luck-stat"),
            Map.entry("mining_fortune", "mining.mining-fortune-stat"),
            Map.entry("mining_speed", "mining.mining-speed-stat"),
            Map.entry("breaking_power", "mining.breaking-power-stat"),
            Map.entry("mining_spread", "mining.mining-spread-stat")
    );

    /** The literal top-level key in stats/*.yml that {@link StatLoader} must NOT treat as a stat definition. */
    public static final String SECTION_KEY = "stat_roles";

    private final Map<String, String> roles = new ConcurrentHashMap<>();

    public void load(Valmora plugin) {
        roles.clear();
        roles.putAll(BUILT_IN_DEFAULTS);

        FileConfiguration config = plugin.getConfig();
        for (Map.Entry<String, String> entry : LEGACY_CONFIG_KEYS.entrySet()) {
            String override = config.getString(entry.getValue());
            if (override != null && !override.isBlank()) {
                roles.put(entry.getKey(), override.toLowerCase(Locale.ROOT));
            }
        }

        File statsDir = new File(plugin.getDataFolder(), "stats");
        File[] files = statsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        // Stat files are read (and syntax errors reported) by StatLoader; this only adds the
        // stat_roles: mappings, checking each points at a real stat once everything has loaded.
        try (org.nakii.valmora.infrastructure.config.diag.LoadSession session =
                     org.nakii.valmora.infrastructure.config.diag.LoadSession.open(plugin, "Stat roles")) {
            if (files != null) {
                java.util.Arrays.sort(files);
                for (File file : files) {
                    YamlConfiguration statsConfig = new YamlConfiguration();
                    try {
                        statsConfig.load(file);
                    } catch (Exception e) {
                        continue; // reported by StatLoader
                    }
                    ConfigurationSection section = statsConfig.getConfigurationSection(SECTION_KEY);
                    if (section == null) continue;
                    String path = "stats/" + file.getName();
                    for (String role : section.getKeys(false)) {
                        String statId = section.getString(role);
                        if (statId == null || statId.isBlank()) {
                            session.warn(path, SECTION_KEY + "." + role, "role has no stat id — ignored");
                            continue;
                        }
                        try (var scope = session.entry(path, SECTION_KEY + "." + role)) {
                            scope.ref(org.nakii.valmora.infrastructure.config.refs.Kinds.STAT, statId);
                        }
                        roles.put(role.toLowerCase(Locale.ROOT), statId.toLowerCase(Locale.ROOT));
                    }
                }
            }
            session.loaded(roles.size());
        }
    }

    /** @return the stat id backing {@code role}, or {@code role} itself if unregistered (matches a plain stat id lookup). */
    public String get(String role) {
        return roles.getOrDefault(role.toLowerCase(Locale.ROOT), role.toLowerCase(Locale.ROOT));
    }

    public boolean has(String role) {
        return roles.containsKey(role.toLowerCase(Locale.ROOT));
    }

    public Set<String> roleNames() {
        return Collections.unmodifiableSet(roles.keySet());
    }

    public void clear() {
        roles.clear();
    }
}
