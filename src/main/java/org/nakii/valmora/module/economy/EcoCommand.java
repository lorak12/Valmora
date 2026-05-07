package org.nakii.valmora.module.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.UUID;

/**
 * /eco get <player> [purse|bank]
 * /eco set <player> <purse|bank> <amount>
 * /eco add <player> <purse|bank> <amount>
 * /eco remove <player> <purse|bank> <amount>
 */
public class EcoCommand implements TabExecutor {

    private static final String PERMISSION = "valmora.admin";
    private static final String USAGE =
        "<gray>Usage: <white>/eco <get|set|add|remove> <player> [purse|bank] [amount]";

    private final EconomyModule economy;

    public EcoCommand(EconomyModule economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Formatter.format("<red>You don't have permission to use this command."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Formatter.format(USAGE));
            return true;
        }

        String sub = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player <white>" + args[1] + "<red> is not online."));
            return true;
        }
        UUID uuid = target.getUniqueId();

        switch (sub) {
            case "get" -> {
                // /eco get <player> [purse|bank]  — defaults to showing both
                String wallet = args.length >= 3 ? args[2].toLowerCase() : "both";
                switch (wallet) {
                    case "purse" -> sender.sendMessage(Formatter.format(
                        "<gold>" + target.getName() + "<gray>'s purse: <white>" + fmt(economy.getPurse(uuid)) + " coins"));
                    case "bank" -> sender.sendMessage(Formatter.format(
                        "<gold>" + target.getName() + "<gray>'s bank: <white>" + fmt(economy.getBank(uuid)) + " coins"));
                    default -> {
                        sender.sendMessage(Formatter.format(
                            "<gold>" + target.getName() + "<gray>'s purse: <white>" + fmt(economy.getPurse(uuid)) + " coins"));
                        sender.sendMessage(Formatter.format(
                            "<gold>" + target.getName() + "<gray>'s bank:  <white>" + fmt(economy.getBank(uuid)) + " coins"));
                    }
                }
            }
            case "set" -> {
                if (args.length < 4) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                String wallet = args[2].toLowerCase();
                double amount = parseAmount(args[3]);
                if (amount < 0) { sender.sendMessage(Formatter.format("<red>Amount must be ≥ 0.")); return true; }
                switch (wallet) {
                    case "purse" -> economy.getOrCreateData(uuid).setPurse(amount);
                    case "bank"  -> economy.getOrCreateData(uuid).setBank(amount);
                    default -> { sender.sendMessage(Formatter.format("<red>Specify <white>purse<red> or <white>bank<red>.")); return true; }
                }
                sender.sendMessage(Formatter.format(
                    "<green>Set <white>" + target.getName() + "<green>'s <white>" + wallet + "<green> to <white>" + fmt(amount) + " coins."));
            }
            case "add" -> {
                if (args.length < 4) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                String wallet = args[2].toLowerCase();
                double amount = parseAmount(args[3]);
                if (amount <= 0) { sender.sendMessage(Formatter.format("<red>Amount must be > 0.")); return true; }
                switch (wallet) {
                    case "purse" -> economy.addPurse(uuid, amount);
                    case "bank"  -> economy.addBank(uuid, amount);
                    default -> { sender.sendMessage(Formatter.format("<red>Specify <white>purse<red> or <white>bank<red>.")); return true; }
                }
                sender.sendMessage(Formatter.format(
                    "<green>Added <white>" + fmt(amount) + "<green> coins to <white>" + target.getName() + "<green>'s <white>" + wallet + "<green>."));
            }
            case "remove" -> {
                if (args.length < 4) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                String wallet = args[2].toLowerCase();
                double amount = parseAmount(args[3]);
                if (amount <= 0) { sender.sendMessage(Formatter.format("<red>Amount must be > 0.")); return true; }
                switch (wallet) {
                    case "purse" -> economy.removePurse(uuid, amount);
                    case "bank"  -> economy.removeBank(uuid, amount);
                    default -> { sender.sendMessage(Formatter.format("<red>Specify <white>purse<red> or <white>bank<red>.")); return true; }
                }
                sender.sendMessage(Formatter.format(
                    "<green>Removed <white>" + fmt(amount) + "<green> coins from <white>" + target.getName() + "<green>'s <white>" + wallet + "<green>."));
            }
            default -> sender.sendMessage(Formatter.format(USAGE));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        return switch (args.length) {
            case 1 -> List.of("get", "set", "add", "remove").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
            case 2 -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            case 3 -> {
                String sub = args[0].toLowerCase();
                if (sub.equals("get")) yield List.of("purse", "bank").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
                if (List.of("set", "add", "remove").contains(sub)) yield List.of("purse", "bank").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
                yield List.of();
            }
            case 4 -> List.of("1000", "1k", "10k", "100k", "1m").stream()
                .filter(s -> s.startsWith(args[3].toLowerCase())).toList();
            default -> List.of();
        };
    }

    private static double parseAmount(String raw) {
        return CoinExpressionParser.parse(raw);
    }

    private static String fmt(double amount) {
        long rounded = Math.round(amount);
        if (rounded >= 1_000_000_000) return String.format("%.2fb", amount / 1_000_000_000.0);
        if (rounded >= 1_000_000)     return String.format("%.2fm", amount / 1_000_000.0);
        if (rounded >= 1_000)         return String.format("%.1fk", amount / 1_000.0);
        return String.valueOf(rounded);
    }
}
