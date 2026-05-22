package org.nakii.valmora.module.backpack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

public class BackpackMechanic implements AbilityMechanic {

    private final BackpackModule module;

    public BackpackMechanic(BackpackModule module) {
        this.module = module;
    }

    @Override
    public String getId() { return "OPEN_BACKPACK"; }

    @Override
    public void execute(ExecutionContext context) {
        Player player = context.getPlayerCaster().orElse(null);
        if (player == null) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!module.isBackpack(item)) return;

        int slot = player.getInventory().getHeldItemSlot();
        module.openBackpack(player, item, slot);
    }
}
