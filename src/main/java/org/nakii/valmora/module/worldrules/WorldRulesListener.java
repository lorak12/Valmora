package org.nakii.valmora.module.worldrules;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/** Applies the configured game rules to any world that loads after the module has already enabled. */
public class WorldRulesListener implements Listener {

    private final WorldRulesModule module;

    public WorldRulesListener(WorldRulesModule module) {
        this.module = module;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        module.applyTo(event.getWorld());
    }
}
