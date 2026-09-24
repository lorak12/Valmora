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
            // Re-read config.yml from disk first — modules read plugin.getConfig() live at point
            // of use (no per-module caching), so without this a reload wouldn't pick up config.yml
            // edits (e.g. items.lore) at all, only YAML content under resources/*.
            plugin.reloadConfig();
            java.util.List<String> failed = plugin.getModuleManager().reloadModules();
            if (failed.isEmpty()) {
                sender.sendMessage(Formatter.format("<green>Valmora Engine reloaded successfully!"));
            } else {
                sender.sendMessage(Formatter.format("<red>Reload finished with errors in: <yellow>"
                        + String.join(", ", failed) + "<red>. Check the console — those modules may be partially loaded."));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("orphans")) {
            handleOrphans(sender, args);
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

        if (args[0].equalsIgnoreCase("pack")) {
            handlePack(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            handleDebug(sender, args);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    /**
     * {@code /valmora debug <module>} — toggles verbose logging for a module via
     * {@link org.nakii.valmora.util.DebugManager}. {@code all} is a standing virtual target that
     * turns on every module's debug logging at once (see {@code DebugManager.isEnabled}) regardless
     * of which individual modules are separately toggled. Every registered {@code ReloadableModule}
     * id is a valid target — {@link org.nakii.valmora.module.ModuleManager#getModules()} is the
     * single source of truth, so this stays complete as modules are added/removed without needing
     * its own maintained list.
     */
    private static final String DEBUG_ALL = "all";

    private java.util.List<String> debuggableModules() {
        return plugin.getModuleManager().getModules().keySet().stream().sorted().collect(Collectors.toList());
    }

    private void handleDebug(CommandSender sender, String[] args) {
        List<String> available = debuggableModules();
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<yellow>/valmora debug <module|all> <gray>- Toggle verbose debug logging (available: "
                    + DEBUG_ALL + ", " + String.join(", ", available) + ")"));
            return;
        }

        String moduleId = args[1].toLowerCase();
        if (!moduleId.equals(DEBUG_ALL) && !available.contains(moduleId)) {
            sender.sendMessage(Formatter.format("<red>No such module '" + moduleId
                    + "'. Available: " + DEBUG_ALL + ", " + String.join(", ", available)));
            return;
        }

        boolean nowEnabled = org.nakii.valmora.util.DebugManager.toggle(moduleId);
        String label = moduleId.equals(DEBUG_ALL) ? "ALL modules" : "'" + moduleId + "'";
        if (nowEnabled) {
            sender.sendMessage(Formatter.format("<green>Debug logging for " + label + " is now <bold>ON</bold>. Watch the console."));
        } else {
            sender.sendMessage(Formatter.format("<yellow>Debug logging for " + label + " is now <bold>OFF</bold>."));
        }
    }

    /**
     * {@code /valmora pack install|uninstall|list|rollback|inspect ...} — the content pack manager's
     * command surface (docs/modules/design/pack.md §8). {@code install}/{@code inspect} currently
     * take a local staged-pack directory path (containing {@code pack.yml} at its root) — fetching
     * that directory from a URL/archive is {@code PackDownloader}'s job (Phase 4, not wired up yet).
     */
    private void handlePack(CommandSender sender, String[] args) {
        var packModule = plugin.getPackModule();
        var packManager = packModule != null ? packModule.getPackManager() : null;
        if (packManager == null) {
            sender.sendMessage(Formatter.format("<red>The content pack manager isn't enabled."));
            return;
        }
        if (args.length < 2) {
            sendPackHelp(sender);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "install" -> {
                if (args.length < 3) {
                    sender.sendMessage(Formatter.format("<yellow>/valmora pack install <dir|url|github:owner/repo@tag> [--sha256 <hash>]"));
                    return;
                }
                String source = args[2];
                String sha256 = null;
                for (int i = 3; i + 1 < args.length; i++) {
                    if (args[i].equalsIgnoreCase("--sha256")) {
                        sha256 = args[i + 1];
                        break;
                    }
                }
                if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("github:")) {
                    sender.sendMessage(Formatter.format("<aqua>Downloading pack from '" + source + "'..."));
                    packManager.installFromSource(source, sha256, result -> reportPackResult(sender, result));
                } else {
                    reportPackResult(sender, packManager.install(new java.io.File(source)));
                }
            }
            case "inspect" -> {
                if (args.length < 3) {
                    sender.sendMessage(Formatter.format("<yellow>/valmora pack inspect <local-staged-pack-directory>"));
                    return;
                }
                var manifestResult = org.nakii.valmora.module.pack.manifest.PackManifestParser.parseFile(
                        new java.io.File(new java.io.File(args[2]), "pack.yml"));
                if (!manifestResult.isSuccess()) {
                    sender.sendMessage(Formatter.format("<red>" + manifestResult.getError()));
                    return;
                }
                var report = org.nakii.valmora.module.pack.validate.PackValidator.validateManifest(
                        manifestResult.getValue(), plugin.getDescription().getVersion(),
                        name -> plugin.getServer().getPluginManager().getPlugin(name) != null);
                sendValidationReport(sender, report, "Inspection of '" + manifestResult.getValue().id() + "'");
            }
            case "uninstall" -> {
                if (args.length < 3) {
                    sender.sendMessage(Formatter.format("<yellow>/valmora pack uninstall <pack-id>"));
                    return;
                }
                reportPackResult(sender, packManager.uninstall(args[2]));
            }
            case "list" -> {
                var installed = packManager.listInstalled();
                if (installed.isEmpty()) {
                    sender.sendMessage(Formatter.format("<gray>No content packs installed."));
                    return;
                }
                sender.sendMessage(Formatter.format("<gold>--- Installed packs (" + installed.size() + ") ---"));
                for (var record : installed) {
                    sender.sendMessage(Formatter.format("<yellow>" + record.packId() + " <gray>v" + record.version()));
                }
            }
            case "rollback" -> {
                if (args.length < 3) {
                    sender.sendMessage(Formatter.format("<yellow>/valmora pack rollback <pack-id> [timestamp]"));
                    return;
                }
                Long timestamp = null;
                if (args.length >= 4) {
                    try {
                        timestamp = Long.parseLong(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Formatter.format("<red>Invalid timestamp: '" + args[3] + "'"));
                        return;
                    }
                }
                reportPackResult(sender, packManager.rollback(args[2], timestamp));
            }
            default -> sendPackHelp(sender);
        }
    }

    private void reportPackResult(CommandSender sender, org.nakii.valmora.module.pack.PackManager.OperationResult result) {
        if (result.success()) {
            sender.sendMessage(Formatter.format("<green>" + result.summary()));
        } else {
            sender.sendMessage(Formatter.format("<red>Pack operation failed:"));
        }
        if (result.report().hasWarnings()) {
            for (String warning : result.report().getWarnings()) {
                sender.sendMessage(Formatter.format("<yellow>- " + warning));
            }
        }
        if (!result.success()) {
            for (String error : result.report().getErrors()) {
                sender.sendMessage(Formatter.format("<red>- " + error));
            }
        }
    }

    private void sendValidationReport(CommandSender sender, org.nakii.valmora.module.pack.validate.PackValidationReport report, String label) {
        if (report.isValid()) {
            sender.sendMessage(Formatter.format("<green>" + label + " passed with no errors."));
        } else {
            sender.sendMessage(Formatter.format("<red>" + label + " found " + report.getErrors().size() + " error(s):"));
            for (String error : report.getErrors()) sender.sendMessage(Formatter.format("<red>- " + error));
        }
        if (report.hasWarnings()) {
            sender.sendMessage(Formatter.format("<yellow>" + report.getWarnings().size() + " warning(s):"));
            for (String warning : report.getWarnings()) sender.sendMessage(Formatter.format("<yellow>- " + warning));
        }
    }

    private void sendPackHelp(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold>--- Valmora Content Packs ---"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pack install <dir|url|github:owner/repo@tag> [--sha256 <hash>] <gray>- Install a pack"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pack inspect <dir> <gray>- Validate a pack without installing it"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pack uninstall <id> <gray>- Uninstall an installed pack"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pack list <gray>- List installed packs"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pack rollback <id> [timestamp] <gray>- Restore a pack's backup snapshot"));
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
            return Stream.of("reload", "variable", "pipeline", "pack", "debug", "orphans")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return Stream.concat(Stream.of(DEBUG_ALL), debuggableModules().stream())
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("pack")) {
            return Stream.of("install", "inspect", "uninstall", "list", "rollback")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pack")
                && (args[1].equalsIgnoreCase("uninstall") || args[1].equalsIgnoreCase("rollback"))) {
            var packModule = plugin.getPackModule();
            if (packModule == null || packModule.getPackManager() == null) return new ArrayList<>();
            return packModule.getPackManager().listInstalled().stream()
                    .map(org.nakii.valmora.module.pack.PackRecord::packId)
                    .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
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

    /**
     * {@code /valmora orphans <player> [purge]} — lists (or deletes) the online player's saved
     * progress that points at content which no longer exists. See OrphanReport.
     */
    private void handleOrphans(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Formatter.format("<red>Usage: /valmora orphans <player> [purge]"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        var session = target != null ? plugin.getPlayerManager().getSession(target.getUniqueId()) : null;
        var profile = session != null ? session.getActiveProfile() : null;
        if (profile == null) {
            sender.sendMessage(Formatter.format("<red>That player isn't online or their profile isn't loaded."));
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("purge")) {
            int removed = org.nakii.valmora.module.profile.OrphanReport.purge(profile);
            plugin.getPlayerManager().save(session);
            sender.sendMessage(Formatter.format("<green>Removed " + removed + " orphaned entr" + (removed == 1 ? "y" : "ies")
                    + " from " + target.getName() + "'s profile '" + profile.getName() + "'."));
            return;
        }
        var orphans = org.nakii.valmora.module.profile.OrphanReport.find(profile);
        if (orphans.isEmpty()) {
            sender.sendMessage(Formatter.format("<green>" + target.getName() + "'s profile '" + profile.getName()
                    + "' has no progress pointing at missing content."));
            return;
        }
        sender.sendMessage(Formatter.format("<gold>Orphaned progress in " + target.getName() + "'s profile '" + profile.getName() + "':"));
        orphans.forEach((category, ids) -> sender.sendMessage(Formatter.format(
                "<yellow>" + category + ": <gray>" + String.join(", ", ids))));
        sender.sendMessage(Formatter.format("<gray>Kept so restoring the content (or listing the old id under its "
                + "<white>previous-ids:</white>) brings it back. <yellow>/valmora orphans " + target.getName()
                + " purge <gray>deletes it."));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold>--- Valmora Engine ---"));
        sender.sendMessage(Formatter.format("<yellow>/valmora reload <gray>- Reload all modules"));
        sender.sendMessage(Formatter.format("<yellow>/valmora variable get <path> <gray>- Get variable value"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pipeline list [point] <gray>- Inspect registered pipeline stages"));
        sender.sendMessage(Formatter.format("<yellow>/valmora pack ... <gray>- Manage content packs (see /valmora pack)"));
        sender.sendMessage(Formatter.format("<yellow>/valmora debug <module|all> <gray>- Toggle verbose debug logging"));
        sender.sendMessage(Formatter.format("<yellow>/valmora orphans <player> [purge] <gray>- Saved progress pointing at deleted content"));
    }
}
