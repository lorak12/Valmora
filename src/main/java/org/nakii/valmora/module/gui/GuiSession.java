package org.nakii.valmora.module.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.module.gui.storage.ItemBindingHandle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiSession {
    private final Player player;
    private final GuiDefinition definition;
    private final Inventory inventory;
    private int currentPage = 0;
    private @Nullable BukkitTask updateTask;
    private @Nullable GuiSession parent;
    private final Map<String, Object> props;
    private @Nullable Map<String, ItemStack> cachedInputSnapshot;
    private boolean craftingLocked = false;
    private boolean inputPending = false;
    private @Nullable String inputPropKey = null;

    /** Set only when this GUI has an ITEM-owned StorageComponent — points at that item's origin. */
    private @Nullable ItemBindingHandle boundItemHandle;

    /**
     * Storage contents loaded (from item PDC or the async DB read) before this session's very
     * first render, keyed by storage-id. Each entry is consumed (removed) the first time
     * {@link org.nakii.valmora.module.gui.renderer.GuiRenderer} seeds that storage component's
     * slots, so later re-renders read live inventory state instead.
     */
    private @Nullable Map<String, ItemStack[]> initialStorageContents;

    public GuiSession(Player player, GuiDefinition definition, Inventory inventory, Map<String, Object> props) {
        this.player = player;
        this.definition = definition;
        this.inventory = inventory;
        this.props = props != null ? props : new HashMap<>();
    }

    public Map<String, Object> getProps() { return props; }
    public Player getPlayer() { return player; }
    public GuiDefinition getDefinition() { return definition; }
    public Inventory getInventory() { return inventory; }
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
    
    public @Nullable BukkitTask getUpdateTask() { return updateTask; }
    public void setUpdateTask(@Nullable BukkitTask updateTask) { this.updateTask = updateTask; }

    public @Nullable GuiSession getParent() { return parent; }
    public void setParent(@Nullable GuiSession parent) { this.parent = parent; }

    public boolean isCraftingLocked() { return craftingLocked; }
    public void setCraftingLocked(boolean locked) { this.craftingLocked = locked; }

    public boolean isInputPending() { return inputPending; }
    public void setInputPending(boolean pending) { this.inputPending = pending; }
    public @Nullable String getInputPropKey() { return inputPropKey; }
    public void setInputPropKey(@Nullable String key) { this.inputPropKey = key; }

    public @Nullable ItemBindingHandle getBoundItemHandle() { return boundItemHandle; }
    public void setBoundItemHandle(@Nullable ItemBindingHandle handle) { this.boundItemHandle = handle; }

    public void setInitialStorageContents(@Nullable Map<String, ItemStack[]> contents) {
        this.initialStorageContents = contents != null ? new HashMap<>(contents) : null;
    }

    /** Consumes (removes) and returns the preloaded contents for this storage-id, if any. */
    public @Nullable ItemStack[] pollInitialStorageContents(String storageId) {
        if (initialStorageContents == null) return null;
        return initialStorageContents.remove(storageId);
    }

    /**
     * Captures the current input items and caches them. Call this before
     * clearing the inventory so that variable resolution mid-render can
     * still see the items the player placed.
     */
    public void snapshotInputs() {
        this.cachedInputSnapshot = buildInputSnapshot();
    }

    /**
     * Clears the cached snapshot. Call this after input items have been
     * restored to the inventory so subsequent reads go live again.
     */
    public void clearInputSnapshot() {
        this.cachedInputSnapshot = null;
    }

    /**
     * Returns the input snapshot. If a cached snapshot exists (i.e. we are
     * mid-render), returns that; otherwise reads live from the inventory.
     */
    public Map<String, ItemStack> getInputSnapshot() {
        if (cachedInputSnapshot != null) {
            return cachedInputSnapshot;
        }
        return buildInputSnapshot();
    }

    private Map<String, ItemStack> buildInputSnapshot() {
        Map<String, ItemStack> snapshot = new HashMap<>();
        List<List<Character>> layout = definition.getLayout();

        int inputIndex = 0; // Tracks normalized grid position (0-8)

        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                char ch = row.get(c);
                GuiComponent comp = definition.getComponents().get(ch);
                if (comp instanceof org.nakii.valmora.module.gui.components.InputComponent input) {
                    int slot = r * 9 + c;
                    ItemStack item = inventory.getItem(slot);

                    snapshot.put(input.getId(), item);
                    snapshot.put(String.valueOf(inputIndex), item); // Maps to "0", "1", "2"
                    
                    inputIndex++;
                }
            }
        }
        return snapshot;
    }
}
