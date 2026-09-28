package org.nakii.valmora.module.worldrules;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * VANILLA_CONTROL_AUDIT.md §8 high-priority gap: "GameRules wholesale — none are set anywhere in
 * Valmora — biggest environmental cluster". This module is deliberately a thin, purely config-driven
 * pass-through: it changes nothing unless {@code world.gamerules.<GAME_RULE_NAME>} is explicitly set
 * in config.yml, applied to every currently-loaded world on enable/reload and to every world loaded
 * afterward (see {@link WorldRulesListener}). No default ruleset is shipped enabled — see the
 * commented examples in config.yml — since the "right" RPG defaults (keepInventory, natural
 * regeneration, fire/fall/drowning damage, etc.) are a game-design call, not a code one.
 *
 * <p>Registered first (alongside {@code script}/{@code time}) since it has no dependencies and
 * several later modules (combat's damage gates, alchemy) may eventually want to read these same
 * values — see {@code docs/modules/design/INTEGRATION.md} for the module dependency map.
 *
 * <p>Also carries the §8 weather write-lock (a real gap: {@code doWeatherCycle=false} only stops the
 * *natural* cycle, it does nothing against a plugin/command-triggered {@code /weather} change). Same
 * philosophy as the gamerule pass-through — per-world, purely config-driven, no lock unless a world
 * is explicitly listed under {@code world.weather-lock}. Unlike the time module (deliberately
 * read-only — see docs — because the client simulates the sun itself and needs re-asserting every
 * tick), weather is fully server-authoritative, so a one-shot {@code setStorm}/{@code setThundering}
 * on enable/world-load plus cancelling any {@code WeatherChangeEvent}/{@code ThunderChangeEvent} that
 * would move a locked world away from its configured state is sufficient — no per-tick reassertion
 * needed.
 */
public class WorldRulesModule implements ReloadableModule {

    /** Valid {@code world.weather-lock.<world>} values. */
    public enum WeatherLock { CLEAR, RAIN, THUNDER }

    private final Valmora plugin;
    private final WorldRulesListener listener;

    public WorldRulesModule(Valmora plugin) {
        this.plugin = plugin;
        this.listener = new WorldRulesListener(this);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        for (World world : Bukkit.getWorlds()) {
            applyTo(world);
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(listener);
    }

    @Override
    public String getId() {
        return "world_rules";
    }

    @Override
    public String getName() {
        return "World Rules";
    }

    /**
     * Applies every configured {@code world.gamerules.*} entry and the {@code world.weather-lock}
     * entry (if any) to {@code world}; unset keys/worlds are left untouched.
     */
    public void applyTo(World world) {
        applyGameRules(world);
        applyWeatherLock(world);
    }

    private void applyGameRules(World world) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world.gamerules");
        if (section == null) return;
        Logger log = plugin.getLogger();
        for (String key : section.getKeys(false)) {
            GameRule<?> rule = resolveGameRule(key, log);
            if (rule == null) continue;
            Class<?> type = rule.getType();
            Object value;
            if (type == Boolean.class) {
                value = section.getBoolean(key);
            } else if (type == Integer.class) {
                value = section.getInt(key);
            } else {
                log.warning("[WorldRules] Game rule '" + key + "' has an unsupported value type " + type
                        + " — skipping.");
                continue;
            }
            @SuppressWarnings("unchecked")
            GameRule<Object> objectRule = (GameRule<Object>) rule;
            world.setGameRule(objectRule, value);
        }
    }

    /**
     * Minecraft 1.21.11 turned game rules into registry entries with snake_case keys and renamed
     * several of them; Paper 26.x resolves them only by that key. Pre-1.21.11 camelCase names that
     * map 1:1 onto a renamed rule (same type, same meaning) are still accepted with a warning so old
     * configs keep working. Legacy names whose rule was inverted or changed type are not translated.
     */
    private static final Map<String, String> RENAMED_LEGACY_RULES = Map.ofEntries(
            Map.entry("announceAdvancements", "show_advancement_messages"),
            Map.entry("doDaylightCycle", "advance_time"),
            Map.entry("doEntityDrops", "entity_drops"),
            Map.entry("doLimitedCrafting", "limited_crafting"),
            Map.entry("doMobLoot", "mob_drops"),
            Map.entry("doMobSpawning", "spawn_mobs"),
            Map.entry("doTileDrops", "block_drops"),
            Map.entry("doWeatherCycle", "advance_weather"),
            Map.entry("naturalRegeneration", "natural_health_regeneration"),
            Map.entry("doInsomnia", "spawn_phantoms"),
            Map.entry("doImmediateRespawn", "immediate_respawn"),
            Map.entry("doPatrolSpawning", "spawn_patrols"),
            Map.entry("doTraderSpawning", "spawn_wandering_traders"),
            Map.entry("doWardenSpawning", "spawn_wardens"),
            Map.entry("doVinesSpread", "spread_vines"),
            Map.entry("commandBlocksEnabled", "command_blocks_work"),
            Map.entry("spawnerBlocksEnabled", "spawner_blocks_work"),
            Map.entry("spawnRadius", "respawn_radius"),
            Map.entry("maxCommandChainLength", "max_command_sequence_length"),
            Map.entry("maxCommandForkCount", "max_command_forks"),
            Map.entry("commandModificationBlockLimit", "max_block_modifications"),
            Map.entry("snowAccumulationHeight", "max_snow_accumulation_height"),
            Map.entry("minecartMaxSpeed", "max_minecart_speed"));

    /** Legacy names with no 1:1 replacement — the value's meaning changed, so it must be rewritten by hand. */
    private static final Map<String, String> CHANGED_LEGACY_RULES = Map.of(
            "disableRaids", "raids (inverted: true now means raids happen)",
            "disablePlayerMovementCheck", "player_movement_check (inverted)",
            "disableElytraMovementCheck", "elytra_movement_check (inverted)",
            "doFireTick", "fire_spread_radius_around_player (now an integer radius)",
            "allowFireTicksAwayFromPlayer", "fire_spread_radius_around_player (now an integer radius)");

    private static GameRule<?> resolveGameRule(String key, Logger log) {
        GameRule<?> rule = lookupGameRule(key);
        if (rule != null) return rule;

        String changed = CHANGED_LEGACY_RULES.get(key);
        if (changed != null) {
            log.warning("[WorldRules] Game rule '" + key + "' in config.yml world.gamerules no longer exists; "
                    + "its replacement is " + changed + " — rewrite the entry. Skipping.");
            return null;
        }
        String modern = RENAMED_LEGACY_RULES.getOrDefault(key, camelToSnake(key));
        rule = lookupGameRule(modern);
        if (rule != null) {
            log.warning("[WorldRules] Game rule '" + key + "' in config.yml world.gamerules uses its pre-1.21.11 "
                    + "name — applying it as '" + modern + "'. Rename the key to silence this warning.");
            return rule;
        }
        log.warning("[WorldRules] Unknown game rule '" + key + "' in config.yml world.gamerules — skipping.");
        return null;
    }

    private static GameRule<?> lookupGameRule(String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        return namespaced != null ? Registry.GAME_RULE.get(namespaced) : null;
    }

    private static String camelToSnake(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private void applyWeatherLock(World world) {
        getWeatherLock(world).ifPresent(lock -> {
            world.setStorm(lock != WeatherLock.CLEAR);
            world.setThundering(lock == WeatherLock.THUNDER);
        });
    }

    /**
     * Resolves the configured {@code world.weather-lock.<world-name>} value, if any. Consulted both
     * here (one-shot apply on enable/world-load) and by {@link WorldRulesListener} (to cancel any
     * {@code WeatherChangeEvent}/{@code ThunderChangeEvent} that would move the world away from it).
     */
    public Optional<WeatherLock> getWeatherLock(World world) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world.weather-lock");
        if (section == null) return Optional.empty();
        String raw = section.getString(world.getName());
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(WeatherLock.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[WorldRules] Unknown weather-lock value '" + raw + "' for world '"
                    + world.getName() + "' — must be CLEAR, RAIN, or THUNDER. Skipping.");
            return Optional.empty();
        }
    }
}
