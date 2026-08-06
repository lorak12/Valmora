package org.nakii.valmora.module;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.util.Arrays;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the Module Teardown Test called for in the master refactor task's Testing &amp;
 * Verification Protocol #4 (see docs/REFACTOR/PROGRESS.md "Not done in Phase 5"): simulates a
 * rapid sequence of {@code /valmora reload}s against real Bukkit {@link Listener} registration
 * (via MockBukkit) and verifies exactly one listener instance survives, with no duplicate
 * registration accumulating across cycles — the core reload-safety contract every
 * {@link ReloadableModule} in this codebase is expected to uphold (CLAUDE.md §6.2 / §13).
 */
@Tag("mockbukkit")
class ModuleManagerReloadSafetyTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Minimal real Bukkit listener so HandlerList registration counts are meaningful. */
    static class JoinListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {}
    }

    /** A module shaped exactly like the reload-safety pattern mandated project-wide. */
    static class ListenerBackedModule implements ReloadableModule {
        private final Plugin plugin;
        private final String id;
        private JoinListener listener;
        int enableCount = 0;
        int disableCount = 0;

        ListenerBackedModule(Plugin plugin, String id) {
            this.plugin = plugin;
            this.id = id;
        }

        @Override
        public void onEnable() {
            listener = new JoinListener();
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            enableCount++;
        }

        @Override
        public void onDisable() {
            if (listener != null) {
                HandlerList.unregisterAll(listener);
                listener = null;
            }
            disableCount++;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private long countRegisteredFor(Plugin plugin) {
        return Arrays.stream(PlayerJoinEvent.getHandlerList().getRegisteredListeners())
                .filter(rl -> rl.getPlugin().equals(plugin))
                .count();
    }

    @Test
    void repeatedReloadsLeaveExactlyOneListenerRegistered() {
        PluginMock mockPlugin = MockBukkit.createMockPlugin("ValmoraTest");
        Valmora loggerStub = mock(Valmora.class);
        when(loggerStub.getLogger()).thenReturn(Logger.getLogger("ModuleManagerReloadSafetyTest"));

        ModuleManager manager = new ModuleManager(loggerStub);
        ListenerBackedModule module = new ListenerBackedModule(mockPlugin, "listener_module");
        manager.registerModule(module);

        manager.enableModules();
        assertEquals(1, countRegisteredFor(mockPlugin), "one enable must register exactly one listener");

        for (int i = 0; i < 5; i++) {
            manager.reloadModules();
        }

        assertEquals(1, countRegisteredFor(mockPlugin),
                "5 reload cycles must leave exactly one listener registered, not accumulate duplicates");
        assertEquals(6, module.enableCount, "1 initial enable + 5 reload-enables");
        assertEquals(5, module.disableCount, "5 reload-disables (no disable before the first enable)");
    }

    @Test
    void multipleModulesEachKeepExactlyOneListenerAcrossReloads() {
        PluginMock mockPlugin = MockBukkit.createMockPlugin("ValmoraTest");
        Valmora loggerStub = mock(Valmora.class);
        when(loggerStub.getLogger()).thenReturn(Logger.getLogger("ModuleManagerReloadSafetyTest"));

        ModuleManager manager = new ModuleManager(loggerStub);
        ListenerBackedModule moduleA = new ListenerBackedModule(mockPlugin, "module_a");
        ListenerBackedModule moduleB = new ListenerBackedModule(mockPlugin, "module_b");
        manager.registerModule(moduleA);
        manager.registerModule(moduleB);

        manager.enableModules();
        for (int i = 0; i < 3; i++) {
            manager.reloadModules();
        }

        // Both modules' listeners target the same event — total registered must be exactly 2 (one per module).
        assertEquals(2, countRegisteredFor(mockPlugin));
    }

    @Test
    void reloadingASingleModuleByIdDoesNotAffectOthers() {
        PluginMock mockPlugin = MockBukkit.createMockPlugin("ValmoraTest");
        Valmora loggerStub = mock(Valmora.class);
        when(loggerStub.getLogger()).thenReturn(Logger.getLogger("ModuleManagerReloadSafetyTest"));

        ModuleManager manager = new ModuleManager(loggerStub);
        ListenerBackedModule moduleA = new ListenerBackedModule(mockPlugin, "module_a");
        ListenerBackedModule moduleB = new ListenerBackedModule(mockPlugin, "module_b");
        manager.registerModule(moduleA);
        manager.registerModule(moduleB);
        manager.enableModules();

        manager.reloadModule("module_a");

        assertEquals(2, moduleA.enableCount);
        assertEquals(1, moduleA.disableCount);
        assertEquals(1, moduleB.enableCount, "module_b must not be touched by reloading module_a");
        assertEquals(0, moduleB.disableCount);
        assertEquals(2, countRegisteredFor(mockPlugin));
    }
}
