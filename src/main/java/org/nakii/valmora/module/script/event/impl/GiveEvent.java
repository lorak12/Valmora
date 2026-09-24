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
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 1;
    }

    @Override
    public String usage() {
        return "give <item>[:amount]";
    }

    @Override
    public void references(String[] args, ReferenceSink sink) {
        if (args.length > 0 && !args[0].contains("$")) { String id = args[0]; int colon = id.lastIndexOf(':'); if (colon > 0 && id.substring(colon + 1).chars().allMatch(Character::isDigit)) id = id.substring(0, colon); sink.ref(org.nakii.valmora.infrastructure.config.refs.Kinds.ITEM_OR_MATERIAL, id); }
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
