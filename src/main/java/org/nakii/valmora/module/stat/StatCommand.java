package org.nakii.valmora.module.stat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.List;

public class StatCommand implements TabExecutor {

    private final PlayerManager playerManager;

    public StatCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player bukkitPlayer)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        // /stat <list|add|remove> [statName] [value]
        if (args.length == 0) {
            sender.sendMessage("Usage: /stat <list|add|remove> [statName] [value]");
            return true;
        }
        String subCommand = args[0];
        ValmoraPlayer player = playerManager.getSession(bukkitPlayer.getUniqueId());
        
        if (player == null) {
            sender.sendMessage("Your player data is not loaded yet.");
            return true;
        }

        ValmoraProfile profile = player.getActiveProfile();
        
        if (profile == null) {
            sender.sendMessage("You do not have an active profile.");
            return true;
        }

        StatManager statManager = profile.getStatManager();

        switch (subCommand.toLowerCase()) {
            case "list":
                // List all stats for the current profile
                sender.sendMessage("Stats for profile: " + profile.getName());
                for (Stat stat : Stat.values()) {
                    sender.sendMessage(Formatter.format(stat.getDisplayName() + ": <white>" + statManager.getStat(stat)));
                }
                break;
            case "add":
                if (args.length < 3) {
                    sender.sendMessage(Formatter.format("<white>Usage: /stat add <statName> <value>"));
                    return true;
                }
                String statName = args[1].toUpperCase();
                double value = Double.parseDouble(args[2]);
                // Add value to the specified stat
                try {
                    statManager.addStat(bukkitPlayer, Stat.valueOf(statName), value);
                    sender.sendMessage("Stat added");
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("Unknown stat: " + statName);
                }
                break;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /stat remove <statName> <value>");
                    return true;
                }
                statName = args[1].toUpperCase();
                value = Double.parseDouble(args[2]);
                // Remove value from the specified stat
                try {
                    statManager.reduceStat(bukkitPlayer, Stat.valueOf(statName), value);
                    sender.sendMessage("Stat removed");
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("Unknown stat: " + statName);
                }
                break;
            default:
                sender.sendMessage("Unknown subcommand. Usage: /stat <list|add|remove> [statName] [value]");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "add", "remove").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}
