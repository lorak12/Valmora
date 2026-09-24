package org.nakii.valmora.module;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Manages the lifecycle of all Valmora modules.
 * Handles loading, unloading, and reloading of modules in the correct order.
 */
public class ModuleManager {

    private final Valmora plugin;
    private final Map<String, ReloadableModule> modules = new LinkedHashMap<>();

    /** Ids of modules whose last onEnable/onDisable threw — reported back to the reload caller. */
    private final List<String> lastFailures = new java.util.ArrayList<>();

    /** True while a reload is tearing modules down and bringing them back up. */
    private boolean reloading;

    public ModuleManager(Valmora plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a new module. Does not enable it yet.
     * @param module the module to register
     */
    public void registerModule(ReloadableModule module) {
        modules.put(module.getId().toLowerCase(), module);
    }

    /**
     * Enably all registered modules in order.
     */
    public void enableModules() {
        for (ReloadableModule module : modules.values()) {
            try {
                plugin.getLogger().info("Enabling module: " + module.getName());
                module.onEnable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + module.getId(), e);
                lastFailures.add(module.getId());
            }
        }
    }

    /**
     * Disable all registered modules in reverse order.
     */
    public void disableModules() {
        // We disable in reverse order of loading
        java.util.List<ReloadableModule> list = new java.util.ArrayList<>(modules.values());
        Collections.reverse(list);

        for (ReloadableModule module : list) {
            try {
                plugin.getLogger().info("Disabling module: " + module.getName());
                module.onDisable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + module.getId(), e);
                lastFailures.add(module.getId());
            }
        }
    }

    /**
     * Reloads all modules.
     */
    public List<String> reloadModules() {
        plugin.getLogger().info("Reloading all modules...");
        lastFailures.clear();
        reloading = true;
        try {
            disableModules();
            enableModules();
        } finally {
            reloading = false;
        }
        afterReload();
        plugin.getLogger().info(lastFailures.isEmpty() ? "Reload complete." : "Reload finished with failures in: " + lastFailures);
        return List.copyOf(lastFailures);
    }

    /**
     * Whether a reload is in progress. Modules come back one at a time, so anything computed
     * mid-reload (notably player stats, recalculated when profiles reload) sees an incomplete
     * picture — e.g. with enchants/modifiers/set bonuses not yet re-enabled, max HP is lower
     * than it really is, and capping current HP/mana to it would permanently cut them down.
     */
    public boolean isReloading() {
        return reloading;
    }

    /** Runs once every reloaded module is back up: recompute state that spans modules. */
    private void afterReload() {
        // Content may have changed: re-fingerprint item content and bring every online player's
        // items up to date now (other items refresh lazily as they're encountered).
        if (plugin.getItemManager() != null) {
            org.nakii.valmora.module.item.ItemRefresher.recomputeEpoch(plugin);
            for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                try {
                    org.nakii.valmora.module.item.ItemRefresher.refresh(plugin, player);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to refresh items of " + player.getName() + " after reload", e);
                }
            }
        }

        var playerManager = plugin.getPlayerManager();
        if (playerManager == null) return;
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            var session = playerManager.getSession(player.getUniqueId());
            var profile = session != null ? session.getActiveProfile() : null;
            if (profile == null) continue;
            try {
                profile.getStatManager().recalculateAttributes(player);
                profile.getStatManager().recalculateStats(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to recalculate stats for " + player.getName() + " after reload", e);
            }
        }
    }

    /**
     * Reloads only the named subset of modules, in the subsequence of registration order that
     * includes them (i.e. respecting each module's normal position relative to the others being
     * reloaded), disabling in reverse and re-enabling in forward order exactly like
     * {@link #reloadModules()} does for the full set. Unknown ids are silently skipped. Used by the
     * content pack manager to reload only the modules a pack's content actually touches, instead of
     * tearing down the whole server.
     * @param moduleIds the ids of the modules to reload
     */
    public void reloadModules(Set<String> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return;
        }
        Set<String> lower = new java.util.HashSet<>();
        for (String id : moduleIds) {
            lower.add(id.toLowerCase());
        }
        List<ReloadableModule> subset = new java.util.ArrayList<>();
        for (ReloadableModule module : modules.values()) {
            if (lower.contains(module.getId().toLowerCase())) {
                subset.add(module);
            }
        }
        if (subset.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Reloading modules: " + moduleIds);
        List<ReloadableModule> reversed = new java.util.ArrayList<>(subset);
        Collections.reverse(reversed);
        reloading = true;
        try {
            for (ReloadableModule module : reversed) {
                try {
                    module.onDisable();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + module.getId(), e);
                }
            }
            for (ReloadableModule module : subset) {
                try {
                    module.onEnable();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + module.getId(), e);
                }
            }
        } finally {
            reloading = false;
        }
        afterReload();
        plugin.getLogger().info("Reload of " + moduleIds + " complete.");
    }

    public Map<String, ReloadableModule> getModules() {
        return Collections.unmodifiableMap(modules);
    }

    public ReloadableModule getModule(String id) {
        return modules.get(id.toLowerCase());
    }

    /**
     * Case-insensitive, type-safe module lookup (Phase 1 API foundation).
     * Returns empty if the id is unregistered or registered under a different type —
     * callers never need to null-check or cast.
     */
    public <T extends ReloadableModule> Optional<T> getModule(String id, Class<T> type) {
        ReloadableModule module = modules.get(id.toLowerCase());
        return type.isInstance(module) ? Optional.of(type.cast(module)) : Optional.empty();
    }

    /**
     * Reloads an individual module by ID.
     * @param id the id of the module to reload
     */
    public void reloadModule(String id) {
        ReloadableModule module = modules.get(id.toLowerCase());
        if (module != null) {
            plugin.getLogger().info("Reloading module: " + module.getName());
            reloading = true;
            try {
                module.onDisable();
                module.onEnable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload module: " + id, e);
            } finally {
                reloading = false;
            }
            afterReload();
        }
    }
}
