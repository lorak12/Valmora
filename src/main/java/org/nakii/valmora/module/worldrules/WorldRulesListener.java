package org.nakii.valmora.module.worldrules;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Applies the configured game rules/weather-lock to any world that loads after the module has
 * already enabled, and enforces the weather-lock (§8) against any {@link WeatherChangeEvent}/
 * {@link ThunderChangeEvent} — natural cycle, {@code /weather} command, or another plugin — that
 * would move a locked world away from its configured state.
 */
public class WorldRulesListener implements Listener {

    private final WorldRulesModule module;

    public WorldRulesListener(WorldRulesModule module) {
        this.module = module;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        module.applyTo(event.getWorld());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        module.getWeatherLock(event.getWorld()).ifPresent(lock -> {
            boolean desiredStorm = lock != WorldRulesModule.WeatherLock.CLEAR;
            if (event.toWeatherState() != desiredStorm) event.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        module.getWeatherLock(event.getWorld()).ifPresent(lock -> {
            boolean desiredThunder = lock == WorldRulesModule.WeatherLock.THUNDER;
            if (event.toThunderState() != desiredThunder) event.setCancelled(true);
        });
    }
}
