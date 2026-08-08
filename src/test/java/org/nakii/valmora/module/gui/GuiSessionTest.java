package org.nakii.valmora.module.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.gui.components.InputComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link GuiSession} — previously untested (per docs/IMPLEMENTATION_BACKLOG.md's
 *  cross-cutting "unit tests for untested modules" item, GUI module). Focuses on the input
 *  snapshot mechanism (§8.3 of CLAUDE.md), initial-storage-contents polling, and the simple
 *  state flags — the full render pipeline around it needs a live GUI stack and is out of scope
 *  here. */
public class GuiSessionTest {

    private Player player;
    private Inventory inventory;
    private GuiDefinition definition;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        inventory = mock(Inventory.class);

        // A 1-row layout with a single INPUT component "I" at slot 0 and a plain filler at slot 1.
        Map<Character, GuiComponent> components = new HashMap<>();
        components.put('I', new InputComponent("ingredient"));
        List<List<Character>> layout = List.of(List.of('I', ' '));

        definition = new GuiDefinition("test_gui", "Test", 20, 1, null, layout, components,
                null, null, null, null, null, null);
    }

    private GuiSession session(Map<String, Object> props) {
        return new GuiSession(player, definition, inventory, props);
    }

    @Test
    void propsDefaultsToEmptyMapWhenNullPassed() {
        GuiSession s = session(null);
        assertNotNull(s.getProps());
        assertTrue(s.getProps().isEmpty());
    }

    @Test
    void propsUsesTheSuppliedMapWhenGiven() {
        Map<String, Object> props = new HashMap<>(Map.of("key", "value"));
        GuiSession s = session(props);
        assertEquals("value", s.getProps().get("key"));
    }

    @Test
    void getInputSnapshotReadsLiveFromInventoryWhenNotSnapshotted() {
        ItemStack item = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(item);

        GuiSession s = session(null);
        Map<String, ItemStack> snapshot = s.getInputSnapshot();

        assertSame(item, snapshot.get("ingredient"));
        assertSame(item, snapshot.get("0")); // also keyed by normalized grid index
    }

    @Test
    void snapshotInputsFreezesTheStateEvenIfInventoryChangesAfterward() {
        ItemStack original = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(original);

        GuiSession s = session(null);
        s.snapshotInputs();

        ItemStack changed = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(changed);

        // Still reads the frozen snapshot, not the now-changed live inventory.
        assertSame(original, s.getInputSnapshot().get("ingredient"));
    }

    @Test
    void clearInputSnapshotReturnsToLiveReads() {
        ItemStack original = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(original);

        GuiSession s = session(null);
        s.snapshotInputs();
        s.clearInputSnapshot();

        ItemStack changed = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(changed);

        assertSame(changed, s.getInputSnapshot().get("ingredient"));
    }

    @Test
    void pollInitialStorageContentsConsumesTheEntryOnce() {
        GuiSession s = session(null);
        ItemStack[] contents = new ItemStack[]{mock(ItemStack.class)};
        s.setInitialStorageContents(Map.of("backpack", contents));

        assertSame(contents, s.pollInitialStorageContents("backpack"));
        assertNull(s.pollInitialStorageContents("backpack")); // consumed — gone the 2nd time
    }

    @Test
    void pollInitialStorageContentsReturnsNullWhenNoneSet() {
        GuiSession s = session(null);
        assertNull(s.pollInitialStorageContents("backpack"));
    }

    @Test
    void craftingLockedDefaultsToFalseAndIsSettable() {
        GuiSession s = session(null);
        assertFalse(s.isCraftingLocked());
        s.setCraftingLocked(true);
        assertTrue(s.isCraftingLocked());
    }

    @Test
    void currentPageDefaultsToZeroAndIsSettable() {
        GuiSession s = session(null);
        assertEquals(0, s.getCurrentPage());
        s.setCurrentPage(3);
        assertEquals(3, s.getCurrentPage());
    }

    @Test
    void parentDefaultsToNullAndIsSettable() {
        GuiSession s = session(null);
        assertNull(s.getParent());
        GuiSession parent = session(null);
        s.setParent(parent);
        assertSame(parent, s.getParent());
    }

    @Test
    void inputPendingFlagAndPropKeyRoundTrip() {
        GuiSession s = session(null);
        assertFalse(s.isInputPending());
        assertNull(s.getInputPropKey());

        s.setInputPending(true);
        s.setInputPropKey("prop.amount");
        assertTrue(s.isInputPending());
        assertEquals("prop.amount", s.getInputPropKey());
    }
}
