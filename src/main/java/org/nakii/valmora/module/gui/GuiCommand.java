package org.nakii.valmora.module.gui;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nakii.valmora.Valmora;

public class GuiCommand implements TabExecutor {

    private final Valmora plugin;

    public GuiCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("valmora.admin.gui")) {
            sender.sendMessage("No permission.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("open")) {
            sender.sendMessage("/gui open <player> <id>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        String guiId = args[2];
        if (!plugin.getGuiModule().getGuiRegistry().containsKey(guiId)) {
            sender.sendMessage("GUI '" + guiId + "' not found.");
            return true;
        }

        plugin.getGuiModule().openGui(target, guiId);
        sender.sendMessage("Opened GUI '" + guiId + "' for " + target.getName());
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("open");
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (args.length == 3) {
            return plugin.getGuiModule().getGuiRegistry().keySet().stream().collect(Collectors.toList());
        }
        return null;
    }
    
}
