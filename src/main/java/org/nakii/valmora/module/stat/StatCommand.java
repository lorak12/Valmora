package org.nakii.valmora.module.stat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.Optional;

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

        if (args.length == 0) {
            sender.sendMessage(Formatter.format("<white>Usage: /stat <list|add|remove> [statId] [value]"));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        ValmoraPlayer player = playerManager.getSession(bukkitPlayer.getUniqueId());

        if (player == null) {
            sender.sendMessage(Formatter.format("<red>Your player data is not loaded yet."));
            return true;
        }

        ValmoraProfile profile = player.getActiveProfile();
        if (profile == null) {
            sender.sendMessage(Formatter.format("<red>You do not have an active profile."));
            return true;
        }

        StatManager statManager = profile.getStatManager();
        StatRegistry registry = ValmoraAPI.getInstance().getStatRegistry();

        switch (subCommand) {
            case "list" -> {
                sender.sendMessage(Formatter.format("<gold><bold>Stats for profile: <white>" + profile.getName()));
                for (StatDefinition def : registry.values()) {
                    sender.sendMessage(Formatter.format(def.getFormattedName() + "<white>: " + (int) statManager.getStat(def.getId())));
                }
            }
            case "add" -> {
                if (!sender.hasPermission("valmora.admin")) {
                    sender.sendMessage(Formatter.format("<red>You do not have permission to use this command."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Formatter.format("<red>Usage: /stat add <statId> <value>"));
                    return true;
                }
                String statId = args[1].toLowerCase();
                Optional<StatDefinition> def = registry.get(statId);
                if (def.isEmpty()) {
                    sender.sendMessage(Formatter.format("<red>Unknown stat: " + statId));
                    return true;
                }
                try {
                    double value = Double.parseDouble(args[2]);
                    statManager.addStat(bukkitPlayer, statId, value);
                    sender.sendMessage(Formatter.format("<green>Added " + (int) value + " to " + def.get().getDisplayName() + "."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Formatter.format("<red>Invalid number: " + args[2]));
                }
            }
            case "remove" -> {
                if (!sender.hasPermission("valmora.admin")) {
                    sender.sendMessage(Formatter.format("<red>You do not have permission to use this command."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Formatter.format("<red>Usage: /stat remove <statId> <value>"));
                    return true;
                }
                String statId = args[1].toLowerCase();
                Optional<StatDefinition> def = registry.get(statId);
                if (def.isEmpty()) {
                    sender.sendMessage(Formatter.format("<red>Unknown stat: " + statId));
                    return true;
                }
                try {
                    double value = Double.parseDouble(args[2]);
                    statManager.reduceStat(bukkitPlayer, statId, value);
                    sender.sendMessage(Formatter.format("<green>Removed " + (int) value + " from " + def.get().getDisplayName() + "."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Formatter.format("<red>Invalid number: " + args[2]));
                }
            }
            default -> sender.sendMessage(Formatter.format("<red>Unknown subcommand. Usage: /stat <list|add|remove>"));
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            StatRegistry registry = ValmoraAPI.getInstance().getStatRegistry();
            return registry.getKeys().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
