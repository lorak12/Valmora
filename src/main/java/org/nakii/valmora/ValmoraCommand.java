package org.nakii.valmora;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValmoraCommand implements TabExecutor {

    private final Valmora plugin;

    public ValmoraCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Dialogue choice — no permission required, player-only
        if (args.length >= 2 && args[0].equalsIgnoreCase("npc-choice")) {
            if (!(sender instanceof Player player)) return true;
            try {
                int index = Integer.parseInt(args[1]);
                DialogueManager dm = plugin.getDialogueManager();
                if (dm != null) dm.handleChoice(player, index);
            } catch (NumberFormatException ignored) {}
            return true;
        }

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

        if (args[0].equalsIgnoreCase("pipeline")) {
            handlePipeline(sender, args);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    /**
     * {@code /valmora pipeline list} and {@code /valmora pipeline list <point>} — runtime
     * introspection into {@link org.nakii.valmora.api.pipeline.HookBus}, the debuggability gap
     * flagged in docs/COMBAT_PIPELINE_ANALYSIS.md §5 ("a misbehaving YAML stage can silently break
     * combat with no clear traceback").
     */
    private void handlePipeline(CommandSender sender, String[] args) {
        var bus = plugin.getHookBus();
        if (bus == null) {
            sender.sendMessage(Formatter.format("<red>Script module isn't enabled — no pipeline bus available."));
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(Formatter.format("<yellow>/valmora pipeline list <gray>[point] <gray>- Inspect registered pipeline stages"));
            return;
        }

        if (args.length == 2) {
            var points = bus.getRegisteredPoints();
            if (points.isEmpty()) {
                sender.sendMessage(Formatter.format("<gray>No pipeline points have any stage/hook registered."));
                return;
            }
            sender.sendMessage(Formatter.format("<gold>--- Registered pipeline points (" + points.size() + ") ---"));
            for (String point : points) {
                int javaCount = bus.getJavaHookIds(point).size();
                int yamlCount = bus.getYamlStageIds(point).size();
                sender.sendMessage(Formatter.format("<yellow>" + point + " <gray>- <aqua>" + javaCount + " java hook(s)<gray>, <aqua>"
                        + yamlCount + " yaml stage(s)"));
            }
            sender.sendMessage(Formatter.format("<gray>Use <yellow>/valmora pipeline list <point> <gray>for stage ids."));
            return;
        }

        String point = args[2];
        var javaIds = bus.getJavaHookIds(point);
        var yamlIds = bus.getYamlStageIds(point);
        if (javaIds.isEmpty() && yamlIds.isEmpty()) {
            sender.sendMessage(Formatter.format("<gray>No stage/hook registered at <yellow>" + point + "<gray>."));
            return;
        }
        sender.sendMessage(Formatter.format("<gold>--- " + point + " (runs in this order) ---"));
        for (String id : javaIds) sender.sendMessage(Formatter.format("  <aqua>[java] <white>" + id));
        for (String id : yamlIds) sender.sendMessage(Formatter.format("  <light_purple>[yaml] <white>" + id));
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
            return Stream.of("reload", "variable", "pipeline")
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

        if (args.length == 2 && args[0].equalsIgnoreCase("pipeline")) {
            return List.of("list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pipeline") && args[1].equalsIgnoreCase("list")) {
            var bus = plugin.getHookBus();
            if (bus == null) return new ArrayList<>();
            return bus.getRegisteredPoints().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold>--- Valmora Engine ---"));
        sender.sendMessage(Formatter.format("<yellow>/valmora reload <gray>- Reload all modules"));
        sender.sendMessage(Formatter.format("<yellow>/valmora variable get <path> <gray>- Get variable value"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pipeline list [point] <gray>- Inspect registered pipeline stages"));
    }
}
