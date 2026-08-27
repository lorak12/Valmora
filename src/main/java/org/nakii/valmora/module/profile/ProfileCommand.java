package org.nakii.valmora.module.profile;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.api.ValmoraAPI;

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
            // Bare /profile (and its /profiles alias) opens the profile GUI rather than dumping usage text.
            ProfileGui.open(player, playerManager);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        ValmoraPlayer session = playerManager.getSession(player.getUniqueId());

        switch (subCommand) {
            case "gui":
                ProfileGui.open(player, playerManager);
                return true;
            case "create": {
                // No name given -> pick a random unused name from the configured pool (profiles.planet-names)
                // instead of requiring the player to type one.
                boolean randomName = args.length < 2;
                String createdName = randomName ? null : args[1];
                PlayerManager.CreateResult result;
                if (randomName) {
                    PlayerManager.CreateOutcome outcome = playerManager.createNextProfile(player.getUniqueId());
                    result = outcome.result();
                    createdName = outcome.name();
                } else {
                    result = playerManager.createProfileResult(player.getUniqueId(), createdName);
                }
                switch (result) {
                    case OK -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '" + createdName + "' created."));
                    case AT_CAP -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>You're at the profile limit ("
                            + playerManager.getMaxProfiles() + "). Delete a profile before creating another."));
                    case DUPLICATE_NAME -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>You already have a profile named '" + createdName + "'."));
                    case NO_SESSION -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Your session isn't loaded yet."));
                }
                break;
            }
            case "delete":
                if (args.length < 2) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /profile delete <name>"));
                    return true;
                }
                String name = args[1];
                PlayerManager.DeleteResult result = playerManager.deleteProfile(player.getUniqueId(), name);
                switch (result) {
                    case OK -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '" + name + "' deleted."));
                    case NOT_FOUND -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Profile '" + name + "' not found."));
                    case ONLY_PROFILE -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>You cannot delete your only profile."));
                    case IS_ACTIVE -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Switch to another profile before deleting this one."));
                    case NO_SESSION -> player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Your session isn't loaded yet."));
                }
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
                var sys = ValmoraAPI.getInstance().getSystemStats();
                double maxHealth = activeProfile.getStatManager().getStat(sys.getHealth());
                double maxMana = activeProfile.getStatManager().getStat(sys.getMana());
                
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
            return List.of("create", "delete", "switch", "list", "info", "gui").stream()
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
