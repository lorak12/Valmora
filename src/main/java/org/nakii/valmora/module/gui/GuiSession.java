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
    private final Map<String, Object> props;

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

    public Map<String, ItemStack> getInputSnapshot() {
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
