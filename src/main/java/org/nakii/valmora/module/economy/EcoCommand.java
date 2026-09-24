package org.nakii.valmora.module.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 *
 * <p>Targets may be online or offline (any player who has played before) — offline targets are
 * read/written directly against the database via {@link EconomyModule#readOffline}/{@link
 * EconomyModule#writeOffline} rather than the in-memory cache.
 */
public class EcoCommand implements TabExecutor {

    // HC-006/HC-013: resolved through PermissionResolver so `permissions.eco` can be set
    // independently of `permissions.admin` — defaults to "valmora.admin", same as before.
    private static final String USAGE =
        "<gray>Usage: <white>/eco <get|set|add|remove> <player> [purse|bank] [amount]";

    private final EconomyModule economy;

    public EcoCommand(EconomyModule economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "eco")) {
            sender.sendMessage(Formatter.format("<red>You don't have permission to use this command."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Formatter.format(USAGE));
            return true;
        }

        String sub = args[0].toLowerCase();
        OfflinePlayer target = resolveTarget(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player <white>" + args[1] + "<red> was not found (must have played on this server before)."));
            return true;
        }
        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[1];

        switch (sub) {
            case "get" -> {
                // /eco get <player> [purse|bank]  — defaults to showing both
                String wallet = args.length >= 3 ? args[2].toLowerCase() : "both";
                economy.readOffline(uuid, row -> {
                    double purse = row[0], bank = row[1];
                    // /eco get shows the exact amount (not the abbreviated 1.0k/1.0m form used
                    // elsewhere) — an admin checking a balance needs the precise number.
                    switch (wallet) {
                        case "purse" -> sender.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Eco<dark_gray>] <gold>" + name + "<gray>'s purse: <white>" + fmtExact(purse) + " coins"));
                        case "bank" -> sender.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Eco<dark_gray>] <gold>" + name + "<gray>'s bank: <white>" + fmtExact(bank) + " coins"));
                        default -> {
                            sender.sendMessage(Formatter.format(
                                "<dark_gray>[<gold>Eco<dark_gray>] <gold>" + name + "<gray>'s purse: <white>" + fmtExact(purse) + " coins"));
                            sender.sendMessage(Formatter.format(
                                "<dark_gray>[<gold>Eco<dark_gray>] <gold>" + name + "<gray>'s bank:  <white>" + fmtExact(bank) + " coins"));
                        }
                    }
                });
            }
            case "set" -> {
                if (args.length < 4) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                String wallet = args[2].toLowerCase();
                double amount = parseAmount(args[3]);
                if (amount < 0) { sender.sendMessage(Formatter.format("<red>Amount must be ≥ 0.")); return true; }
                if (!wallet.equals("purse") && !wallet.equals("bank")) {
                    sender.sendMessage(Formatter.format("<red>Specify <white>purse<red> or <white>bank<red>."));
                    return true;
                }
                economy.readOffline(uuid, row -> {
                    double purse = wallet.equals("purse") ? amount : row[0];
                    double bank = wallet.equals("bank") ? amount : row[1];
                    economy.writeOffline(uuid, purse, bank, () -> sender.sendMessage(Formatter.format(
                        "<green>Set <white>" + name + "<green>'s <white>" + wallet + "<green> to <white>" + fmt(amount) + " coins.")));
                });
            }
            case "add" -> {
                if (args.length < 4) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                String wallet = args[2].toLowerCase();
                double amount = parseAmount(args[3]);
                if (amount <= 0) { sender.sendMessage(Formatter.format("<red>Amount must be > 0.")); return true; }
                if (!wallet.equals("purse") && !wallet.equals("bank")) {
                    sender.sendMessage(Formatter.format("<red>Specify <white>purse<red> or <white>bank<red>."));
                    return true;
                }
                economy.readOffline(uuid, row -> {
                    double purse = wallet.equals("purse") ? row[0] + amount : row[0];
                    double bank = wallet.equals("bank") ? row[1] + amount : row[1];
                    economy.writeOffline(uuid, purse, bank, () -> sender.sendMessage(Formatter.format(
                        "<green>Added <white>" + fmt(amount) + "<green> coins to <white>" + name + "<green>'s <white>" + wallet + "<green>.")));
                });
            }
            case "remove" -> {
                if (args.length < 4) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                String wallet = args[2].toLowerCase();
                double amount = parseAmount(args[3]);
                if (amount <= 0) { sender.sendMessage(Formatter.format("<red>Amount must be > 0.")); return true; }
                if (!wallet.equals("purse") && !wallet.equals("bank")) {
                    sender.sendMessage(Formatter.format("<red>Specify <white>purse<red> or <white>bank<red>."));
                    return true;
                }
                economy.readOffline(uuid, row -> {
                    double purse = wallet.equals("purse") ? Math.max(0, row[0] - amount) : row[0];
                    double bank = wallet.equals("bank") ? Math.max(0, row[1] - amount) : row[1];
                    economy.writeOffline(uuid, purse, bank, () -> sender.sendMessage(Formatter.format(
                        "<green>Removed <white>" + fmt(amount) + "<green> coins from <white>" + name + "<green>'s <white>" + wallet + "<green>.")));
                });
            }
            default -> sender.sendMessage(Formatter.format(USAGE));
        }
        return true;
    }

    /** Online players resolve directly; offline targets are matched (case-insensitively) against the server's known-player cache — never a blocking network lookup. */
    private OfflinePlayer resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.hasPlayedBefore() && name.equalsIgnoreCase(op.getName())) return op;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "eco")) return List.of();
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
            case 4 -> {
                // "get" only takes <player> [purse|bank] — no amount arg, so don't suggest numbers there.
                if (!List.of("set", "add", "remove").contains(args[0].toLowerCase())) yield List.of();
                // HC-014: preset amounts differ by economy scale.
                var plugin = org.nakii.valmora.Valmora.getInstance();
                List<String> presets = plugin != null
                        ? plugin.getConfig().getStringList("economy.tab-complete-amounts")
                        : List.of();
                if (presets.isEmpty()) presets = List.of("1000", "1k", "10k", "100k", "1m");
                yield presets.stream()
                    .filter(s -> s.startsWith(args[3].toLowerCase())).toList();
            }
            default -> List.of();
        };
    }

    private static double parseAmount(String raw) {
        return CoinExpressionParser.parse(raw);
    }

    // HC-011: dedup fix — these used to keep an independent copy of EconomyModule's compact/exact
    // formatting math, which could silently drift from the real formatter. Both now delegate.
    private static String fmt(double amount) {
        return EconomyModule.formatCoins(amount);
    }

    /** Exact, thousands-separated amount — used by /eco get so admins see the real number, not an abbreviation. */
    private static String fmtExact(double amount) {
        // formatCoinsDisplay() prefixes a coin symbol (economy.format.coin-symbol) that this
        // command's own messages already supply via "<white>{amount} coins" — strip it back off.
        String withSymbol = EconomyModule.formatCoinsDisplay(amount);
        String symbol = org.nakii.valmora.Valmora.getInstance().getConfig().getString("economy.format.coin-symbol", "🪙 ");
        return withSymbol.startsWith(symbol) ? withSymbol.substring(symbol.length()) : withSymbol;
    }
}
