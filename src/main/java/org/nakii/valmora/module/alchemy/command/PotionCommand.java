package org.nakii.valmora.module.alchemy.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.alchemy.brewing.AlchemyMachineHandler;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class PotionCommand implements CommandExecutor, TabCompleter {

    private final Valmora plugin;
    private final AlchemyManager alchemyManager;
    private final AlchemyMachineHandler handler;

    public PotionCommand(Valmora plugin, AlchemyManager alchemyManager) {
        this.plugin = plugin;
        this.alchemyManager = alchemyManager;
        this.handler = new AlchemyMachineHandler(plugin, alchemyManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "alchemy")) {
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

        ItemStack potion = handler.buildPotion(effect, clampedLevel, duration, false, false, false);
        target.getInventory().addItem(potion);

        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "Gave " + effect.getName() + " level " + clampedLevel + " to " + target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "alchemy")) return List.of();

        if (args.length == 1) {
            return filter(List.of("give"), args[0]);
        }
        if (args.length == 2) {
            return filter(alchemyManager.getAllEffects().keySet(), args[1]);
        }
        if (args.length == 3) {
            return filter(List.of("1", "2", "3", "4", "5"), args[2]);
        }
        if (args.length == 4) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[3]);
        }
        return List.of();
    }

    private List<String> filter(Iterable<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) result.add(option);
        }
        return result;
    }

    private int parseIntOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
