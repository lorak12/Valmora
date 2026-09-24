package org.nakii.valmora.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Fired on the main thread once a reload (full or partial) has finished and every reloaded module
 * is back up — the moment to reconcile live world state with the new content (re-apply mob
 * templates, re-attach boss controllers, re-summon pets, ...). Listeners registered by a module
 * that was itself reloaded are already the new instance's.
 */
public class ValmoraReloadedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Set<String> reloadedModules;

    public ValmoraReloadedEvent(Set<String> reloadedModules) {
        this.reloadedModules = Set.copyOf(reloadedModules);
    }

    /** Ids of the modules that were reloaded (every module for a full reload). */
    public Set<String> getReloadedModules() {
        return reloadedModules;
    }

    public boolean wasReloaded(String moduleId) {
        return reloadedModules.contains(moduleId.toLowerCase());
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
