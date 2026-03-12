package org.nakii.valmora;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfileCommand implements TabExecutor {

    private PlayerManager playerManager;

    public ProfileCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /profile <create|delete|switch|list> [name]");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /profile create <name>");
                    return true;
                }
                String name = args[1];
                playerManager.createProfile(((Player) sender).getUniqueId() , name);
                sender.sendMessage("Profile '" + name + "' created.");
                break;
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /profile delete <name>");
                    return true;
                }
                name = args[1];
                playerManager.deleteProfile(((Player) sender).getUniqueId(), name);
                sender.sendMessage("Profile '" + name + "' deleted.");
                break;
            case "switch":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /profile switch <name>");
                    return true;
                }
                name = args[1];
                playerManager.switchProfile((Player) sender, name);
                sender.sendMessage("Switched to profile '" + name + "'.");
                break;
            case "list":
                Map<UUID,ValmoraProfile> profiles = playerManager.getSession(((Player) sender).getUniqueId()).getProfiles();
                for (ValmoraProfile profile : profiles.values()) {
                    String message = profile.getId() == playerManager.getSession(((Player) sender).getUniqueId()).getActiveProfile().getId() ?
                            "[ACTIVE]" + profile.getName() : profile.getName();
                    sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
                }
                break;

            default:
                sender.sendMessage("Unknown subcommand. Usage: /profile <create|delete|switch> [name]");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "delete", "switch").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
