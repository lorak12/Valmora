package org.nakii.valmora.module.alchemy.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.alchemy.brewing.AlchemyMachineHandler;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PotionCommand implements CommandExecutor {

    private final Valmora plugin;
    private final AlchemyManager alchemyManager;

    public PotionCommand(Valmora plugin, AlchemyManager alchemyManager) {
        this.plugin = plugin;
        this.alchemyManager = alchemyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("valmora.admin")) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("No permission."));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "Usage: /potion give <effect_id> <level> [player]"));
            return true;
        }

        String effectId = args[1];
        int level = args.length >= 3 ? parseIntOrDefault(args[2], 1) : 1;

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayer(args[3]);
            if (target == null) {
                sender.sendMessage(net.kyori.adventure.text.Component.text("Player not found: " + args[3]));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Specify a player name."));
            return true;
        }

        Optional<AlchemyEffect> effectOpt = alchemyManager.getEffect(effectId);
        if (effectOpt.isEmpty()) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Unknown effect: " + effectId));
            return true;
        }

        AlchemyEffect effect = effectOpt.get();
        int clampedLevel = Math.max(1, Math.min(level, effect.getMaxLevel()));
        int duration = effect.getDuration(clampedLevel);

        AlchemyMachineHandler handler = new AlchemyMachineHandler(plugin, alchemyManager);
        ItemStack potion = handler.buildPotion(effect, clampedLevel, duration, false, false, false);
        target.getInventory().addItem(potion);

        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "Gave " + effect.getName() + " level " + clampedLevel + " to " + target.getName()));
        return true;
    }

    private int parseIntOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
