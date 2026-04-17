package org.nakii.valmora.module.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

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

    public GuiSession(Player player, GuiDefinition definition, Inventory inventory) {
        this.player = player;
        this.definition = definition;
        this.inventory = inventory;
    }

    public Player getPlayer() { return player; }
    public GuiDefinition getDefinition() { return definition; }
    public Inventory getInventory() { return inventory; }
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
    
    public @Nullable BukkitTask getUpdateTask() { return updateTask; }
    public void setUpdateTask(@Nullable BukkitTask updateTask) { this.updateTask = updateTask; }

    public @Nullable GuiSession getParent() { return parent; }
    public void setParent(@Nullable GuiSession parent) { this.parent = parent; }

    public Map<String, ItemStack> getInputSnapshot() {
        Map<String, ItemStack> snapshot = new HashMap<>();
        List<List<Character>> layout = definition.getLayout();
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                char ch = row.get(c);
                GuiComponent comp = definition.getComponents().get(ch);
                if (comp instanceof org.nakii.valmora.module.gui.components.InputComponent input) {
                    snapshot.put(input.getId(), inventory.getItem(r * 9 + c));
                }
            }
        }
        return snapshot;
    }
}
