package org.nakii.valmora;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.profile.PlayerManager;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.profile.ValmoraProfile;

import java.util.List;

public class StatCommand implements TabExecutor {

    private PlayerManager playerManager;

    public StatCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /stat <list|add|remove> [statName] [value]
        if (args.length == 0) {
            sender.sendMessage("Usage: /stat <list|add|remove> [statName] [value]");
            return true;
        }
        String subCommand = args[0];
        switch (subCommand.toLowerCase()) {
            case "list":
                // List all stats for the current profile
                ValmoraPlayer player = playerManager.getSession(((Player) sender).getUniqueId());
                ValmoraProfile profile = player.getActiveProfile();

                StatManager statManager = profile.getStatManager();
                sender.sendMessage("Stats for profile: " + profile.getName());
                for (Stat stat : Stat.values()) {
                    sender.sendMessage(stat.name() + ": " + statManager.getStat(stat));
                }
                break;
            case "add":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /stat add <statName> <value>");
                    return true;
                }
                String statName = args[1];
                Double value = Double.parseDouble(args[2]);
                // Add value to the specified stat
                player = playerManager.getSession(((Player) sender).getUniqueId());
                profile = player.getActiveProfile();
                statManager = profile.getStatManager();
                statManager.addStat((Player) sender,Stat.valueOf(statName), value);
                sender.sendMessage("Stat added");
                break;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /stat remove <statName> <value>");
                    return true;
                }
                statName = args[1];
                value = Double.parseDouble(args[2]);
                // Remove value from the specified stat
                player = playerManager.getSession(((Player) sender).getUniqueId());
                profile = player.getActiveProfile();
                statManager = profile.getStatManager();
                statManager.reduceStat((Player) sender,Stat.valueOf(statName), value);
                sender.sendMessage("Stat removed");
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
        if (args.length == 2) {
            if  (!args[0].equalsIgnoreCase("list")) {
                for (Stat stat : Stat.values()) {
                    if (stat.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                        return List.of(stat.name());
                    }
                }
            }
        }
        return List.of();
    }
}
