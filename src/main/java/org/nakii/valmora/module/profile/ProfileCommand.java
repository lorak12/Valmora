package org.nakii.valmora.module.profile;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.module.stat.Stat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfileCommand implements TabExecutor {

    private final PlayerManager playerManager;

    public ProfileCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /profile <create|delete|switch|list|info> [name]"));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        ValmoraPlayer session = playerManager.getSession(player.getUniqueId());

        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /profile create <name>"));
                    return true;
                }
                String name = args[1];
                playerManager.createProfile(player.getUniqueId() , name);
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '" + name + "' created."));
                break;
            case "delete":
                if (args.length < 2) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /profile delete <name>"));
                    return true;
                }
                name = args[1];
                playerManager.deleteProfile(player.getUniqueId(), name);
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '" + name + "' deleted."));
                break;
            case "switch":
                if (args.length < 2) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /profile switch <name>"));
                    return true;
                }
                name = args[1];
                playerManager.switchProfile(player, name);
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Switched to profile '" + name + "'."));
                break;
            case "list":
                Map<UUID,ValmoraProfile> profiles = session.getProfiles();
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                player.sendMessage(Formatter.format(" <gold><bold>YOUR PROFILES"));
                for (ValmoraProfile profile : profiles.values()) {
                    String prefix = (profile.getId().equals(session.getActiveProfile().getId())) ? "<green>[ACTIVE] " : "<gray>";
                    player.sendMessage(Formatter.format(" " + prefix + profile.getName()));
                }
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                break;

            case "info":
                ValmoraProfile activeProfile = session.getActiveProfile();
                if (activeProfile == null) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>No active profile found."));
                    return true;
                }
                
                PlayerState state = activeProfile.getPlayerState();
                double maxHealth = activeProfile.getStatManager().getStat(Stat.HEALTH);
                double maxMana = activeProfile.getStatManager().getStat(Stat.MANA);
                
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                player.sendMessage(Formatter.format(" <gold><bold>PROFILE INFO"));
                player.sendMessage(Formatter.format(" <gray>ID: <white>" + activeProfile.getId().toString()));
                player.sendMessage(Formatter.format(" <gray>Name: <yellow>" + activeProfile.getName()));
                player.sendMessage(Formatter.format(" <gray>Health: <red>" + String.format("%.1f/%.1f", state.getCurrentHealth(), maxHealth)));
                player.sendMessage(Formatter.format(" <gray>Mana: <aqua>" + String.format("%.1f/%.1f", state.getCurrentMana(), maxMana)));
                player.sendMessage(Formatter.format(" <gray>In Combat: " + (state.isInCombat() ? "<red>Yes" : "<green>No")));
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                break;

            default:
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown subcommand. Usage: /profile <create|delete|switch|list|info> [name]"));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "delete", "switch", "list", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("switch"))) {
            if (sender instanceof Player player) {
                ValmoraPlayer vp = playerManager.getSession(player.getUniqueId());
                if (vp != null) {
                    return vp.getProfiles().values().stream()
                            .map(ValmoraProfile::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
            }
        }
        return List.of();
    }
}
