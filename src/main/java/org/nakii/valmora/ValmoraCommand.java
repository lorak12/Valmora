package org.nakii.valmora;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.nakii.valmora.util.Formatter;

public class ValmoraCommand implements CommandExecutor {

    private final Valmora plugin;

    public ValmoraCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("valmora.admin")) {
            sender.sendMessage(Formatter.format("<red>No permission!"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Formatter.format("<aqua>Reloading Valmora Engine..."));
            plugin.getModuleManager().reloadModules();
            sender.sendMessage(Formatter.format("<green>Valmora Engine reloaded successfully!"));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold>--- Valmora Engine ---"));
        sender.sendMessage(Formatter.format("<yellow>/valmora reload <gray>- Reload all modules"));
    }
}
