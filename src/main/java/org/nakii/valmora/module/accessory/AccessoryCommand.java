package org.nakii.valmora.module.accessory;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.List;

/**
 * /accessory setcap <amount>              — server-wide ceiling for unlocked slots
 * /accessory setslots <player> <amount>   — set a player's unlocked slot count
 * /accessory addslots <player> <amount>   — grant a player more unlocked slots
 * /accessory info <player>                — show a player's current/cap slot counts
 */
public class AccessoryCommand implements TabExecutor {

    private static final String PERMISSION = "valmora.admin";
    private static final String USAGE =
            "<gray>Usage: <white>/accessory <setcap|setslots|addslots|info> ...";

    private final AccessoryModule module;

    public AccessoryCommand(AccessoryModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Formatter.format("<red>You don't have permission to use this command."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Formatter.format(USAGE));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "setcap" -> {
                if (args.length < 2) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                Integer cap = parseInt(args[1]);
                if (cap == null || cap < 1) {
                    sender.sendMessage(Formatter.format("<red>Amount must be a positive whole number."));
                    return true;
                }
                module.setMaxSlotsCap(cap);
                sender.sendMessage(Formatter.format(
                        "<green>Accessory slot cap set to <white>" + module.getMaxSlotsCap() + "<green> server-wide."));
            }
            case "setslots", "addslots" -> {
                if (args.length < 3) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Formatter.format("<red>Player <white>" + args[1] + "<red> is not online."));
                    return true;
                }
                Integer amount = parseInt(args[2]);
                if (amount == null) {
                    sender.sendMessage(Formatter.format("<red>Amount must be a whole number."));
                    return true;
                }
                ValmoraProfile profile = getActiveProfile(target);
                if (profile == null) {
                    sender.sendMessage(Formatter.format("<red>" + target.getName() + " has no active profile."));
                    return true;
                }
                if (sub.equals("setslots")) {
                    module.setUnlockedSlots(profile, amount);
                } else {
                    module.addUnlockedSlots(profile, amount);
                }
                int unlocked = module.getUnlockedSlots(profile);
                sender.sendMessage(Formatter.format(
                        "<green>" + target.getName() + "<green> now has <white>" + unlocked
                                + "<green>/<white>" + module.getMaxSlotsCap() + "<green> accessory slots unlocked."));
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(Formatter.format(USAGE)); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Formatter.format("<red>Player <white>" + args[1] + "<red> is not online."));
                    return true;
                }
                ValmoraProfile profile = getActiveProfile(target);
                if (profile == null) {
                    sender.sendMessage(Formatter.format("<red>" + target.getName() + " has no active profile."));
                    return true;
                }
                sender.sendMessage(Formatter.format(
                        "<gold>" + target.getName() + "<gray>'s accessory slots: <white>"
                                + module.getUnlockedSlots(profile) + "<gray> unlocked / <white>"
                                + module.getMaxSlotsCap() + "<gray> server cap."));
            }
            default -> sender.sendMessage(Formatter.format(USAGE));
        }
        return true;
    }

    private ValmoraProfile getActiveProfile(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        return session != null ? session.getActiveProfile() : null;
    }

    private Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        return switch (args.length) {
            case 1 -> List.of("setcap", "setslots", "addslots", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
            case 2 -> {
                if (args[0].equalsIgnoreCase("setcap")) yield List.of();
                yield Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            default -> List.of();
        };
    }
}
