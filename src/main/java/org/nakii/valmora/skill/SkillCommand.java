package org.nakii.valmora.skill;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.profile.PlayerManager;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.util.Formatter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SkillCommand implements TabExecutor {

    private final PlayerManager playerManager;

    public SkillCommand(Valmora plugin, PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /skill <info|list|givexp|setlevel>"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
                if (args.length == 1) {
                    // Show all skills info for self
                    showAllSkills(player, player);
                } else {
                    // Show specific skill info
                    String skillStr = args[1].toUpperCase();
                    try {
                        Skill skill = Skill.valueOf(skillStr);
                        showSkillInfo(player, player, skill);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown skill: " + skillStr));
                    }
                }
                break;

            case "list":
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                player.sendMessage(Formatter.format(" <gold><bold>AVAILABLE SKILLS"));
                for (Skill skillItem : Skill.values()) {
                    player.sendMessage(Formatter.format(" <gray>- <white>" + skillItem.getName() + " <dark_gray>(Max Lvl: " + skillItem.getMaxLevel() + ")"));
                }
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                break;

            case "givexp":
                if (!player.hasPermission("valmora.admin")) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>You do not have permission to use this."));
                    return true;
                }
                if (args.length < 4) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /skill givexp <player> <skill> <amount>"));
                    return true;
                }
                handleGiveXp(player, args[1], args[2], args[3]);
                break;

            case "setlevel":
                if (!player.hasPermission("valmora.admin")) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>You do not have permission to use this."));
                    return true;
                }
                if (args.length < 4) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /skill setlevel <player> <skill> <level>"));
                    return true;
                }
                handleSetLevel(player, args[1], args[2], args[3]);
                break;

            default:
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown subcommand."));
                break;
        }

        return true;
    }

    private void showAllSkills(Player sender, Player target) {
        ValmoraPlayer vp = playerManager.getSession(target.getUniqueId());
        if (vp == null || vp.getActiveProfile() == null) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Could not load profile for " + target.getName()));
            return;
        }
        SkillManager sm = vp.getActiveProfile().getSkillManager();
        
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>SKILLS: <white>" + target.getName()));
        for (Skill s : Skill.values()) {
            double xp = sm.getXp(s);
            int level = sm.getLevel(s);
            sender.sendMessage(Formatter.format(" <gray>" + s.getName() + ": <yellow>Lvl " + level + " <dark_gray>(" + String.format("%.1f", xp) + " XP)"));
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void showSkillInfo(Player sender, Player target, Skill skill) {
        ValmoraPlayer vp = playerManager.getSession(target.getUniqueId());
        if (vp == null || vp.getActiveProfile() == null) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Could not load profile for " + target.getName()));
            return;
        }
        SkillManager sm = vp.getActiveProfile().getSkillManager();
        
        double xp = sm.getXp(skill);
        int level = sm.getLevel(skill);
        
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>" + skill.getName().toUpperCase() + " SKILL <dark_gray>(" + target.getName() + ")"));
        sender.sendMessage(Formatter.format(" <gray>Description: <white>" + skill.getDescription()));
        sender.sendMessage(Formatter.format(" <gray>Level: <yellow>" + level + " <dark_gray>/ " + skill.getMaxLevel()));
        sender.sendMessage(Formatter.format(" <gray>Total XP: <aqua>" + String.format("%.1f", xp)));
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void handleGiveXp(Player sender, String targetName, String skillName, String amountStr) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Player not found."));
            return;
        }
        Skill skill;
        try {
            skill = Skill.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown skill."));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Invalid amount."));
            return;
        }

        ValmoraPlayer vp = playerManager.getSession(target.getUniqueId());
        if (vp != null && vp.getActiveProfile() != null) {
            SkillManager sm = vp.getActiveProfile().getSkillManager();
            sm.addXp(skill, amount, target);
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Gave " + amount + " XP in " + skill.getName() + " to " + target.getName()));
        }
    }

    private void handleSetLevel(Player sender, String targetName, String skillName, String levelStr) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Player not found."));
            return;
        }
        Skill skill;
        try {
            skill = Skill.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown skill."));
            return;
        }
        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Invalid level."));
            return;
        }

        if (level < 0 || level > skill.getMaxLevel()) {
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Level must be between 0 and " + skill.getMaxLevel()));
            return;
        }

        ValmoraPlayer vp = playerManager.getSession(target.getUniqueId());
        if (vp != null && vp.getActiveProfile() != null) {
            SkillManager sm = vp.getActiveProfile().getSkillManager();
            // To set a level exactly, we give them the xp required for that level
            double requireXp = level == 0 ? 0 : Skill.xpTresholds[level - 1];
            sm.setXp(skill, requireXp);
            sender.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Set " + target.getName() + "'s " + skill.getName() + " level to " + level));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], List.of("info", "list", "givexp", "setlevel"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("setlevel")) {
                return filterMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            } else if (args[0].equalsIgnoreCase("info")) {
                return filterMatches(args[1], Arrays.stream(Skill.values()).map(Skill::name).collect(Collectors.toList()));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("setlevel")) {
                return filterMatches(args[2], Arrays.stream(Skill.values()).map(Skill::name).collect(Collectors.toList()));
            }
        }
        return List.of();
    }

    private List<String> filterMatches(String input, List<String> options) {
        String lowerInput = input.toLowerCase();
        
        return options.stream()
                .filter(option -> option.toLowerCase().contains(lowerInput))
                .sorted((a, b) -> {
                    boolean aStarts = a.toLowerCase().startsWith(lowerInput);
                    boolean bStarts = b.toLowerCase().startsWith(lowerInput);
                    if (aStarts && !bStarts) return -1;
                    if (!aStarts && bStarts) return 1;
                    return a.compareTo(b);
                })
                .collect(Collectors.toList());
    }
}
