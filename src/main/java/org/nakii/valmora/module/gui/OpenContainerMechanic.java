package org.nakii.valmora.module.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.gui.storage.PlayerHeldItemHandle;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.util.Keys;

/**
 * Generic "open this item's storage GUI" mechanic (id {@code OPEN_CONTAINER_GUI}), replacing
 * the old backpack-only {@code OPEN_BACKPACK} mechanic. Reads the held item's
 * {@link Keys#CONTAINER_GUI_KEY} PDC tag (set from an item's {@code container-gui:} YAML
 * field) and opens that GUI bound to the held item.
 */
public class OpenContainerMechanic implements AbilityMechanic {

    private final GuiModule guiModule;

    public OpenContainerMechanic(GuiModule guiModule) {
        this.guiModule = guiModule;
    }

    @Override
    public String getId() { return "OPEN_CONTAINER_GUI"; }

    @Override
    public void execute(ExecutionContext context) {
        Player player = context.getPlayerCaster().orElse(null);
        if (player == null) return;

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return;

        String guiId = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.CONTAINER_GUI_KEY, PersistentDataType.STRING);
        if (guiId == null) return;

        guiModule.openItemBoundGui(player, guiId, new PlayerHeldItemHandle(player, slot));
    }
}
