package org.nakii.valmora.module;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages the lifecycle of all Valmora modules.
 * Handles loading, unloading, and reloading of modules in the correct order.
 */
public class ModuleManager {

    private final Valmora plugin;
    private final Map<String, ReloadableModule> modules = new LinkedHashMap<>();

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
            }
        }
    }

    /**
     * Reloads all modules.
     */
    public void reloadModules() {
        plugin.getLogger().info("Reloading all modules...");
        disableModules();
        // Here we'd ideally re-initialize things if needed, but for now we just call enable again
        enableModules();
        plugin.getLogger().info("Reload complete.");
    }

    public Map<String, ReloadableModule> getModules() {
        return Collections.unmodifiableMap(modules);
    }

    public ReloadableModule getModule(String id) {
        return modules.get(id.toLowerCase());
    }

    /**
     * Reloads an individual module by ID.
     * @param id the id of the module to reload
     */
    public void reloadModule(String id) {
        ReloadableModule module = modules.get(id.toLowerCase());
        if (module != null) {
            plugin.getLogger().info("Reloading module: " + module.getName());
            try {
                module.onDisable();
                module.onEnable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload module: " + id, e);
            }
        }
    }
}
