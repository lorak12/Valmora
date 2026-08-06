package org.nakii.valmora.module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the Phase 1 {@link ModuleManager#getModule(String, Class)} API — case-insensitive,
 * type-safe lookup that {@link org.nakii.valmora.api.ValmoraAPIImpl} relies on for every accessor.
 */
public class ModuleManagerTest {

    private ModuleManager moduleManager;

    static class StubModuleA implements ReloadableModule {
        @Override public void onEnable() {}
        @Override public void onDisable() {}
        @Override public String getId() { return "stub_a"; }
    }

    static class StubModuleB implements ReloadableModule {
        @Override public void onEnable() {}
        @Override public void onDisable() {}
        @Override public String getId() { return "stub_b"; }
    }

    @BeforeEach
    void setUp() {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ModuleManagerTest"));
        moduleManager = new ModuleManager(plugin);
        moduleManager.registerModule(new StubModuleA());
        moduleManager.registerModule(new StubModuleB());
    }

    @Test
    void resolvesRegisteredModuleByExactId() {
        Optional<StubModuleA> found = moduleManager.getModule("stub_a", StubModuleA.class);
        assertTrue(found.isPresent());
    }

    @Test
    void lookupIsCaseInsensitive() {
        Optional<StubModuleA> found = moduleManager.getModule("STUB_A", StubModuleA.class);
        assertTrue(found.isPresent());
    }

    @Test
    void unregisteredIdResolvesEmpty() {
        assertTrue(moduleManager.getModule("nonexistent", StubModuleA.class).isEmpty());
    }

    @Test
    void wrongTypeForARegisteredIdResolvesEmptyRatherThanThrowing() {
        // "stub_a" exists but is not a StubModuleB — must not ClassCastException, must be empty.
        assertTrue(moduleManager.getModule("stub_a", StubModuleB.class).isEmpty());
    }
}
