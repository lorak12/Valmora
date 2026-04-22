package org.nakii.valmora;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ValmoraCommand implements TabExecutor {

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

        if (args[0].equalsIgnoreCase("variable") && args.length >= 3) {
            if (args[1].equalsIgnoreCase("get")) {
                handleVariableGet(sender, args[2]);
                return true;
            }
        }

        sendHelp(sender);
        return true;
    }

    private void handleVariableGet(CommandSender sender, String path) {
        String fullPath = path;
        if (!fullPath.startsWith("$")) fullPath = "$" + fullPath;
        if (!fullPath.endsWith("$")) fullPath = fullPath + "$";

        SimpleExecutionContext context = new SimpleExecutionContext(
                sender instanceof Player ? (Player) sender : null,
                sender instanceof Player ? ((Player) sender).getLocation() : null,
                null
        );

        Object result = plugin.getScriptModule().getVariableResolver().resolve(fullPath, context);

        if (result == null) {
            sender.sendMessage(Formatter.format("<red>Variable <gray>" + path + " <red>is null or not found."));
        } else {
            sender.sendMessage(Formatter.format("<green>Variable <gray>" + path + " <green>value: <white>" + result.toString()));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "variable").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("variable")) {
            return List.of("get").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("variable") && args[1].equalsIgnoreCase("get")) {
            return plugin.getScriptModule().getVariableProviderRegistry().getKeys().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold>--- Valmora Engine ---"));
        sender.sendMessage(Formatter.format("<yellow>/valmora reload <gray>- Reload all modules"));
        sender.sendMessage(Formatter.format("<yellow>/valmora variable get <path> <gray>- Get variable value"));
    }
}

