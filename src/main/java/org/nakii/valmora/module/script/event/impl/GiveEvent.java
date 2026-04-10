package org.nakii.valmora.module.script.event.impl;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Event for giving items to a player.
 * DSL: give <Material:Amount> [notify]
 */
public class GiveEvent implements EventFactory {

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};

        String itemSpec = args[0];
        String[] split = itemSpec.split(":");
        Material material = Material.matchMaterial(split[0]);
        int amount = split.length > 1 ? parseAmount(split[1]) : 1;

        if (material == null) return context -> {};

        return context -> {
            context.getPlayerCaster().ifPresent(player -> {
                player.getInventory().addItem(new ItemStack(material, amount));
                if (options.notifyPlayer()) {
                    player.sendMessage("§6§lVALMORA §7» §fYou received §a" + amount + "x " + material.name());
                }
            });
        };
    }

    private int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
