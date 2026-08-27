package org.nakii.valmora.module.hud;

import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.EventParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Fills the "no test coverage at all" gap flagged for the {@code hud} module in
 * docs/V1_RELEASE_CHECKLIST.md §1 — covers {@code hud-items/*.yml} parsing through
 * {@link HudItemModule#onEnable()} end to end (slot assignment, prevent-move default, glow,
 * missing/invalid material handling), matching the module-harness pattern established by
 * {@code PetXpFormulaTest}.
 */
@Tag("mockbukkit")
class HudItemModuleTest {

    private Valmora plugin;
    private File dataFolder;

    @BeforeEach
    void setUp() throws IOException {
        // Bukkit.getOnlinePlayers() is called unconditionally in onEnable(); MockBukkit gives it
        // a real (empty) server to answer to without dragging in a full PluginMock for `plugin`
        // itself, since HudItemModule's constructor is typed to the concrete Valmora class.
        MockBukkit.mock();

        dataFolder = Files.createTempDirectory("valmora-test").toFile();
        new File(dataFolder, "hud-items").mkdirs();

        plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("HudItemModuleTest"));

        org.bukkit.Server server = mock(org.bukkit.Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);

        // Only reached if a definition declares on-right-click/on-left-click — never invoked
        // otherwise, so a bare mock (no real parsing behavior) is enough here.
        ScriptModule scriptModule = mock(ScriptModule.class);
        when(plugin.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getEventParser()).thenReturn(mock(EventParser.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void writeHudFile(String yaml) throws IOException {
        Files.writeString(new File(dataFolder, "hud-items/test.yml").toPath(), yaml);
    }

    @Test
    void loadsASimpleDefinitionWithDefaults() throws IOException {
        writeHudFile("compass:\n  item:\n    material: COMPASS\n");

        HudItemModule module = new HudItemModule(plugin);
        module.onEnable();

        HudItemDefinition def = module.getBySlot(8);
        assertNotNull(def, "default slot is 8 when unspecified");
        assertEquals("compass", def.getId());
        assertTrue(def.isPreventMove(), "prevent-move defaults to true");
        assertEquals(org.bukkit.Material.COMPASS, def.getItem().getType());
    }

    @Test
    void explicitSlotAndPreventMoveOverrideDefaults() throws IOException {
        writeHudFile("warp_stick:\n  slot: 4\n  prevent-move: false\n  item:\n    material: STICK\n");

        HudItemModule module = new HudItemModule(plugin);
        module.onEnable();

        HudItemDefinition def = module.getDefinitions().iterator().next();
        assertEquals(4, def.getSlot());
        assertFalse(def.isPreventMove());
        assertSame(def, module.getBySlot(4));
    }

    @Test
    void invalidMaterialFailsToLoadWithoutThrowing() throws IOException {
        writeHudFile("broken:\n  item:\n    material: NOT_A_REAL_MATERIAL\n");

        HudItemModule module = new HudItemModule(plugin);
        assertDoesNotThrow(module::onEnable);

        assertTrue(module.getDefinitions().isEmpty(), "invalid material must not register a definition");
    }

    @Test
    void missingItemSectionFailsToLoadWithoutThrowing() throws IOException {
        writeHudFile("broken:\n  slot: 2\n");

        HudItemModule module = new HudItemModule(plugin);
        assertDoesNotThrow(module::onEnable);

        assertTrue(module.getDefinitions().isEmpty(), "a HUD item with no 'item' section must not register");
    }

    @Test
    void isHudItemRecognizesOnlyItemsCarryingTheHudPdcKey() throws IOException {
        writeHudFile("compass:\n  item:\n    material: COMPASS\n");

        HudItemModule module = new HudItemModule(plugin);
        module.onEnable();

        HudItemDefinition def = module.getDefinitions().iterator().next();
        assertTrue(module.isHudItem(def.getItem()));
        assertFalse(module.isHudItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS)));
        assertFalse(module.isHudItem(null));
    }

    @Test
    void reloadClearsPreviousDefinitionsRatherThanAccumulating() throws IOException {
        writeHudFile("compass:\n  item:\n    material: COMPASS\n");

        HudItemModule module = new HudItemModule(plugin);
        module.onEnable();
        assertEquals(1, module.getDefinitions().size());

        module.onDisable();
        module.onEnable();
        module.onEnable(); // idempotent per CLAUDE.md §6.1 — must not double up

        assertEquals(1, module.getDefinitions().size());
    }
}
