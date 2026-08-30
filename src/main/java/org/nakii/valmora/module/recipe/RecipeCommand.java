package org.nakii.valmora.module.recipe;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.Map;
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
            case "preview" -> handlePreview(sender, args);
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
                sender.sendMessage(Formatter.format(" <gray>- <white>" + recipe.getId() + " <dark_gray>(" + recipe.getType()
                        + ", " + recipe.getOutputs().size() + " output(s))"));
            }
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    /** Inspects a single recipe's full input/output shape — added per docs/IMPLEMENTATION_BACKLOG.md's
     *  "/recipe" admin tooling item (list alone didn't show enough to identify a specific recipe). */
    private void handlePreview(CommandSender sender, String[] args) {
        RecipeModule rm = plugin.getRecipeModule();
        if (args.length < 3) {
            sender.sendMessage(Formatter.format("<red>Usage: /recipe preview <machineId> <recipeId>"));
            return;
        }
        String machineId = args[1].toLowerCase();
        RecipeDefinition recipe = rm.getRecipesForMachine(machineId).stream()
                .filter(r -> r.getId().equalsIgnoreCase(args[2]))
                .findFirst().orElse(null);
        if (recipe == null) {
            sender.sendMessage(Formatter.format("<red>No recipe '" + args[2] + "' found on machine '" + machineId + "'."));
            return;
        }

        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>RECIPE — " + recipe.getId()));
        sender.sendMessage(Formatter.format(" <gray>Type: <white>" + recipe.getType()));
        if (recipe.getInputMap() != null) {
            for (Map.Entry<String, RecipeIngredient> e : recipe.getInputMap().entrySet()) {
                sender.sendMessage(Formatter.format(" <gray>Input [" + e.getKey() + "]: <white>"
                        + e.getValue().item() + " x" + e.getValue().amount()));
            }
        }
        if (recipe.getInputList() != null) {
            for (RecipeIngredient ing : recipe.getInputList()) {
                sender.sendMessage(Formatter.format(" <gray>Input: <white>" + ing.item() + " x" + ing.amount()));
            }
        }
        if (recipe.getOutputs() != null) {
            for (RecipeOutput out : recipe.getOutputs()) {
                String slotLabel = out.slot() != null ? out.slot() : "default";
                sender.sendMessage(Formatter.format(" <gray>Output [" + slotLabel + "]: <white>"
                        + out.ingredient().item() + " x" + out.ingredient().amount()));
            }
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>RECIPE COMMANDS:"));
        sender.sendMessage(Formatter.format(" <gray>/recipe list <machineId>"));
        sender.sendMessage(Formatter.format(" <gray>/recipe preview <machineId> <recipeId>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("list", "preview"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("preview")) {
            return plugin.getRecipeModule().getRecipesForMachine(args[1].toLowerCase()).stream()
                    .map(RecipeDefinition::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
