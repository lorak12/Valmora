package org.nakii.valmora.api;

/**
 * Represents a module that can be loaded, unloaded, and reloaded.
 * Modules should be independent and handle their own lifecycle.
 */
public interface ReloadableModule {

    /**
     * Called when the module is being loaded.
     * Use this to initialize registries, load configurations, and register listeners.
     */
    void onEnable();

    /**
     * Called when the module is being unloaded.
     * Use this to unregister listeners, cancel tasks, and clear caches.
     */
    void onDisable();

    /**
     * Returns the unique identifier for this module.
     * @return module ID
     */
    String getId();

    /**
     * Returns the human-readable name of this module.
     * @return module name
     */
    default String getName() {
        return getId();
    }
}
