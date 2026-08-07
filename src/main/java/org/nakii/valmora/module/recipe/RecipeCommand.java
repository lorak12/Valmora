package org.nakii.valmora.module.recipe;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.stream.Collectors;

/** Admin/debug command for inspecting the recipe engine — previously no command surface existed. */
public class RecipeCommand implements TabExecutor {

    private final Valmora plugin;

    public RecipeCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleList(CommandSender sender, String[] args) {
        RecipeModule rm = plugin.getRecipeModule();
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /recipe list <machineId>"));
            return;
        }
        String machineId = args[1].toLowerCase();
        List<RecipeDefinition> recipes = rm.getRecipesForMachine(machineId);

        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>RECIPES — " + machineId));
        if (recipes.isEmpty()) {
            sender.sendMessage(Formatter.format(" <gray>No YAML recipes registered for this machine."
                    + " (A dynamic handler may still be registered — see RecipeEngine.)"));
        } else {
            for (RecipeDefinition recipe : recipes) {
                sender.sendMessage(Formatter.format(" <gray>- <white>" + recipe.getType()
                        + " <dark_gray>(" + recipe.getOutputs().size() + " output(s))"));
            }
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>RECIPE COMMANDS:"));
        sender.sendMessage(Formatter.format(" <gray>/recipe list <machineId>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("list"));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
