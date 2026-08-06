package org.nakii.valmora.api;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.ModuleManager;
import org.nakii.valmora.module.stat.StatModule;
import org.nakii.valmora.module.stat.StatRegistry;
import org.nakii.valmora.module.stat.SystemStats;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers Phase 1.1 of the refactor: {@link ValmoraAPIImpl} must resolve every accessor through
 * {@link ModuleManager#getModule(String, Class)} rather than holding stale field references, and
 * must return {@code null} — not throw — when a module isn't registered/enabled yet.
 */
public class ValmoraAPIImplTest {

    @Test
    void resolvesDirectModuleAccessorThroughModuleManager() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        StatModule statModule = mock(StatModule.class);
        StatRegistry registry = mock(StatRegistry.class);
        SystemStats stats = mock(SystemStats.class);
        when(moduleManager.getModule("stats", StatModule.class)).thenReturn(java.util.Optional.of(statModule));
        when(statModule.getStatRegistry()).thenReturn(registry);
        when(statModule.getSystemStats()).thenReturn(stats);

        ValmoraAPIImpl api = new ValmoraAPIImpl(moduleManager);

        assertSame(statModule, api.getStatModule());
        assertSame(registry, api.getStatRegistry());
        assertSame(stats, api.getSystemStats());
    }

    @Test
    void unregisteredModuleResolvesToNullInsteadOfThrowing() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.getModule("stats", StatModule.class)).thenReturn(java.util.Optional.empty());

        ValmoraAPIImpl api = new ValmoraAPIImpl(moduleManager);

        assertNull(api.getStatModule());
        // Dependent accessors that reach into the (absent) module must also degrade to null.
        assertNull(api.getStatRegistry());
        assertNull(api.getSystemStats());
    }

    @Test
    void getModuleManagerReturnsTheBackingManager() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        ValmoraAPIImpl api = new ValmoraAPIImpl(moduleManager);
        assertSame(moduleManager, api.getModuleManager());
    }

    @Test
    void economyServiceOverrideTakesPrecedenceOverTheBuiltInModule() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        ValmoraAPIImpl api = new ValmoraAPIImpl(moduleManager);
        org.nakii.valmora.api.economy.EconomyService override = mock(org.nakii.valmora.api.economy.EconomyService.class);

        api.setEconomyService(override);

        assertSame(override, api.getEconomy());
    }
}
