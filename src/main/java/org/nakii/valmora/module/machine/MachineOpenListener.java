package org.nakii.valmora.module.machine;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.zone.event.ZoneEnterEvent;

import java.util.List;

/**
 * Opens a machine's GUI the moment any one of its {@code open-triggers:} conditions is true —
 * checked on two occasions: the player right-clicking (block or air) and the player stepping into
 * a zone. A condition that only makes sense on one of those (e.g. {@code zone x} is pointless to
 * check on right-click, {@code block x} is pointless to check on zone-enter) simply evaluates
 * false on the other, so nothing needs to be wired per condition kind.
 */
public class MachineOpenListener implements Listener {

    private final Valmora plugin;
    private final List<MachineDefinition> machines;

    public MachineOpenListener(Valmora plugin, List<MachineDefinition> machines) {
        this.plugin = plugin;
        this.machines = machines;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        // Avoid double-firing on off-hand + main-hand for the same physical click (CLAUDE.md §14.8).
        if (event.getHand() != EquipmentSlot.HAND) return;

        MachineDefinition machine = matchingMachine(event.getPlayer());
        if (machine == null) return;

        event.setCancelled(true);
        plugin.getGuiModule().openGui(event.getPlayer(), machine.getGui());
    }

    @EventHandler
    public void onZoneEnter(ZoneEnterEvent event) {
        Player player = event.getPlayer();
        MachineDefinition machine = matchingMachine(player);
        if (machine == null) return;

        plugin.getGuiModule().openGui(player, machine.getGui());
    }

    /** The first machine (load order) with an open-trigger condition currently true for this player. */
    private MachineDefinition matchingMachine(Player player) {
        SimpleExecutionContext context = new SimpleExecutionContext(player, player.getLocation(), null);
        for (MachineDefinition machine : machines) {
            for (Condition trigger : machine.getOpenTriggers()) {
                if (trigger.evaluate(context)) return machine;
            }
        }
        return null;
    }
}
