package org.nakii.valmora.module.skill;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SkillCommand implements TabExecutor {

    private final PlayerManager playerManager;
    private final SkillModule skillModule;

    public SkillCommand(Valmora plugin, PlayerManager playerManager) {
        this.playerManager = playerManager;
        this.skillModule = plugin.getSkillModule();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> handleList(sender);
            case "get" -> handleGet(sender, args);
            case "give" -> handleGive(sender, args);
            case "set" -> handleSet(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>VALMORA SKILLS"));
        for (SkillDefinition skill : skillModule.getSkillRegistry().values()) {
            sender.sendMessage(Formatter.format(" <gray>- <white>" + skill.getName() + " <dark_gray>(" + skill.getId() + ")"));
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Formatter.format("<red>Usage: /skill get <player> <skill>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player not found."));
            return;
        }

        String skillId = args[2].toLowerCase();
        Optional<SkillDefinition> skillOpt = skillModule.getSkillRegistry().getSkill(skillId);
        if (skillOpt.isEmpty()) {
            sender.sendMessage(Formatter.format("<red>Unknown skill: " + skillId));
            return;
        }

        SkillDefinition skill = skillOpt.get();
        ValmoraProfile profile = playerManager.getSession(target.getUniqueId()).getActiveProfile();
        if (profile == null) {
            sender.sendMessage(Formatter.format("<red>Player profile not loaded."));
            return;
        }

        double xp = profile.getSkillManager().getXp(skillId);
        SkillRegistry.ProgressData data = skillModule.getSkillRegistry().getProgressData(skill.getXpCurve(), xp);

        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>" + skill.getName().toUpperCase() + " INFO <dark_gray>(" + target.getName() + ")"));
        sender.sendMessage(Formatter.format(" <gray>Level: <yellow>" + data.currentLevel() + " <dark_gray>/ " + skill.getMaxLevel()));
        sender.sendMessage(Formatter.format(" <gray>Total XP: <aqua>" + String.format("%.1f", xp)));
        sender.sendMessage(Formatter.format(" <gray>Progress: <white>" + data.xpInLevel() + " / " + data.xpRequired() + " <yellow>(" + data.percent() + "%)"));
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valmora.admin")) {
            sender.sendMessage(Formatter.format("<red>No permission."));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(Formatter.format("<red>Usage: /skill give <player> <skill> <xp>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player not found."));
            return;
        }

        String skillId = args[2].toLowerCase();
        Optional<SkillDefinition> skillOpt = skillModule.getSkillRegistry().getSkill(skillId);
        if (skillOpt.isEmpty()) {
            sender.sendMessage(Formatter.format("<red>Unknown skill: " + skillId));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Formatter.format("<red>Invalid XP amount."));
            return;
        }

        ValmoraProfile profile = playerManager.getSession(target.getUniqueId()).getActiveProfile();
        if (profile == null) {
            sender.sendMessage(Formatter.format("<red>Player profile not loaded."));
            return;
        }

        profile.getSkillManager().addXp(skillId, amount, target);
        sender.sendMessage(Formatter.format("<green>Gave " + amount + " " + skillOpt.get().getName() + " XP to " + target.getName()));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valmora.admin")) {
            sender.sendMessage(Formatter.format("<red>No permission."));
            return;
        }

        if (args.length < 5) {
            sender.sendMessage(Formatter.format("<red>Usage: /skill set <player> <skill> <xp|level> <value>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player not found."));
            return;
        }

        String skillId = args[2].toLowerCase();
        Optional<SkillDefinition> skillOpt = skillModule.getSkillRegistry().getSkill(skillId);
        if (skillOpt.isEmpty()) {
            sender.sendMessage(Formatter.format("<red>Unknown skill: " + skillId));
            return;
        }

        String type = args[3].toLowerCase();
        double value;
        try {
            value = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Formatter.format("<red>Invalid value."));
            return;
        }

        ValmoraProfile profile = playerManager.getSession(target.getUniqueId()).getActiveProfile();
        if (profile == null) {
            sender.sendMessage(Formatter.format("<red>Player profile not loaded."));
            return;
        }

        if (type.equals("level")) {
            double xp = skillModule.getSkillRegistry().getXpForLevel(skillOpt.get().getXpCurve(), (int) value);
            profile.getSkillManager().setXp(skillId, xp);
            sender.sendMessage(Formatter.format("<green>Set " + target.getName() + "'s " + skillOpt.get().getName() + " level to " + (int) value));
        } else if (type.equals("xp")) {
            profile.getSkillManager().setXp(skillId, value);
            sender.sendMessage(Formatter.format("<green>Set " + target.getName() + "'s " + skillOpt.get().getName() + " XP to " + value));
        } else {
            sender.sendMessage(Formatter.format("<red>Type must be 'xp' or 'level'."));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>SKILL COMMANDS:"));
        sender.sendMessage(Formatter.format(" <gray>/skill list"));
        sender.sendMessage(Formatter.format(" <gray>/skill get <player> <skill>"));
        if (sender.hasPermission("valmora.admin")) {
            sender.sendMessage(Formatter.format(" <gray>/skill give <player> <skill> <xp>"));
            sender.sendMessage(Formatter.format(" <gray>/skill set <player> <skill> <xp|level> <value>"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("list", "get", "give", "set"));
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set")) {
                return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set")) {
                return filter(args[2], new ArrayList<>(skillModule.getSkillRegistry().getKeys()));
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("set")) {
                return filter(args[3], List.of("xp", "level"));
            }
        }

        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
