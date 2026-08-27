package org.nakii.valmora.module.gui.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.gui.GuiDefinition;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.recipe.RecipeEngine;
import org.nakii.valmora.module.recipe.RecipeModule;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * docs/V1_RELEASE_CHECKLIST.md §1 "GUI machine pipeline end-to-end" — covers the dupe-protection
 * contract described in CLAUDE.md §8.4 ("{@code GuiSession.craftingLocked} is set to true during
 * the craft pipeline... a second {@code gui_force_craft} while locked is a no-op") directly against
 * {@link GuiForceCraftEventFactory}, plus that the lock is always released even when the craft
 * pipeline throws. The success path (a real recipe match reaching {@code GuiRenderer.render()}) is
 * intentionally out of scope here — that needs a much larger render-pipeline fixture and is a
 * separate, still-open item in the checklist.
 */
class GuiForceCraftEventFactoryTest {

    private Valmora plugin;
    private RecipeModule recipeModule;
    private RecipeEngine recipeEngine;
    private GuiSession session;
    private GuiExecutionContext guiContext;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        recipeModule = mock(RecipeModule.class);
        recipeEngine = mock(RecipeEngine.class);
        when(plugin.getRecipeModule()).thenReturn(recipeModule);
        when(recipeModule.getRecipeEngine()).thenReturn(recipeEngine);

        Player player = mock(Player.class);
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);

        GuiDefinition definition = new GuiDefinition("test_machine_gui", "Test", 20, 1,
                "test_machine", List.of(List.of(' ')), Map.of(),
                null, null, null, null, null, null);
        Inventory inventory = mock(Inventory.class);

        session = spy(new GuiSession(player, definition, inventory, new HashMap<>()));
        // getInputSnapshot() normally reads live slot contents (CLAUDE.md §8.3) — stubbed to an
        // empty, deterministic map since input-snapshot fidelity is GuiSession's own concern.
        doReturn(new HashMap<String, ItemStack>()).when(session).getInputSnapshot();

        guiContext = new GuiExecutionContext(player, session);
    }

    private void invoke() {
        new GuiForceCraftEventFactory(plugin).compile(new String[0], new EventOptions(0, false))
                .execute(guiContext);
    }

    @Test
    void aSecondForceCraftWhileLockedIsANoOpAndNeverConsultsTheRecipeEngine() {
        session.setCraftingLocked(true);

        invoke();

        verifyNoInteractions(recipeEngine);
        assertTrue(session.isCraftingLocked(), "an in-progress lock held by someone else must not be touched by the no-op path");
    }

    @Test
    void theLockIsHeldDuringTheEngineCallAndReleasedAfterANoMatchResult() {
        when(recipeEngine.craft(eq("test_machine"), any(), any())).thenAnswer(inv -> {
            // Assert the lock is actually held *during* the craft call, not just before/after.
            assertTrue(session.isCraftingLocked(), "lock must be held while the engine is consulted");
            return Optional.empty();
        });

        invoke();

        verify(recipeEngine).craft(eq("test_machine"), any(), any());
        assertFalse(session.isCraftingLocked(), "lock must be released once the craft attempt resolves to no match");
    }

    @Test
    void theLockIsReleasedEvenIfTheRecipeEngineThrows() {
        when(recipeEngine.craft(eq("test_machine"), any(), any()))
                .thenThrow(new RuntimeException("simulated engine failure"));

        assertThrows(RuntimeException.class, this::invoke);

        assertFalse(session.isCraftingLocked(), "the finally block must release the lock even on an unexpected exception");
    }

    @Test
    void aFreshSessionStartsUnlockedAndCanCraftImmediately() {
        assertFalse(session.isCraftingLocked());
        when(recipeEngine.craft(eq("test_machine"), any(), any())).thenReturn(Optional.empty());

        invoke();

        verify(recipeEngine).craft(eq("test_machine"), any(), any());
    }
}
