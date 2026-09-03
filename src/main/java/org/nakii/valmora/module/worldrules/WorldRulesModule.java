package org.nakii.valmora.module.worldrules;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.util.Locale;
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
            GameRule<?> rule = GameRule.getByName(key);
            if (rule == null) {
                log.warning("[WorldRules] Unknown game rule '" + key + "' in config.yml world.gamerules — skipping.");
                continue;
            }
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
