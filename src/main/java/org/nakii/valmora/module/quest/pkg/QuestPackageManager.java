package org.nakii.valmora.module.quest.pkg;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.npc.dialogue.DialogueChoice;
import org.nakii.valmora.module.npc.dialogue.DialogueDefinition;
import org.nakii.valmora.module.npc.dialogue.DialogueNode;
import org.nakii.valmora.module.notify.NotifyManager;
import org.nakii.valmora.module.quest.QuestDefinition;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.hider.PlayerHiderEntry;
import org.nakii.valmora.module.quest.hider.PlayerHiderManager;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads the quest package tree from plugins/Valmora/quests/.
 *
 * Package detection rule: a folder is a package if it contains quest.yml.
 * Sub-folders that also contain quest.yml are separate packages.
 * Sub-folders without quest.yml belong to the parent package.
 *
 * Package loading uses two passes per package:
 *   Pass 1 — events, conditions, objectives, quests, notifications
 *   Pass 2 — conversations (can now resolve named conditions/events)
 *
 * npc_conversations: bindings must be declared in quest.yml.
 */
public class QuestPackageManager {

    private final Valmora plugin;
    private final Logger log;
    private final List<QuestPackage> packages = new ArrayList<>();

    public QuestPackageManager(Valmora plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public List<QuestPackage> getPackages() { return Collections.unmodifiableList(packages); }

    // -------------------------------------------------------------------------
    // Public load entry point
    // -------------------------------------------------------------------------

    public void loadAll() {
        packages.clear();
        File questsRoot = new File(plugin.getDataFolder(), "quests");
        File templatesRoot = new File(plugin.getDataFolder(), "templates");

        Map<String, QuestPackage> templateMap = new HashMap<>();
        if (templatesRoot.isDirectory()) {
            for (File child : safeListDirs(templatesRoot)) {
                QuestPackage tpl = loadPackage(child, "");
                if (tpl != null) templateMap.put(child.getName().toLowerCase(), tpl);
            }
        }

        if (questsRoot.isDirectory()) {
            for (File child : safeListDirs(questsRoot)) {
                scanAndLoad(child, "", templateMap);
            }
        }

        applyToManagers();
    }

    // -------------------------------------------------------------------------
    // Recursive scan
    // -------------------------------------------------------------------------

    private void scanAndLoad(File dir, String parentPath, Map<String, QuestPackage> templates) {
        if (!dir.isDirectory()) return;
        File questYml = new File(dir, "quest.yml");
        String myPath = parentPath.isEmpty() ? dir.getName() : parentPath + "-" + dir.getName();

        if (questYml.exists()) {
            QuestPackage pkg = loadPackage(dir, myPath);
            if (pkg == null || !pkg.isEnabled()) return;
            mergeTemplates(pkg, templates);
            packages.add(pkg);
            for (File sub : safeListDirs(dir)) {
                if (new File(sub, "quest.yml").exists()) {
                    scanAndLoad(sub, myPath, templates);
                }
            }
        } else {
            for (File sub : safeListDirs(dir)) {
                scanAndLoad(sub, myPath, templates);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Package loading (two-pass)
    // -------------------------------------------------------------------------

    private QuestPackage loadPackage(File dir, String path) {
        File questYml = new File(dir, "quest.yml");
        if (!questYml.exists()) return null;

        YamlConfiguration questCfg = YamlConfiguration.loadConfiguration(questYml);
        ConfigurationSection pkgSec = questCfg.getConfigurationSection("package");
        boolean enabled = pkgSec == null || pkgSec.getBoolean("enabled", true);
        List<String> templateNames = pkgSec != null ? pkgSec.getStringList("templates") : List.of();

        QuestPackage pkg = new QuestPackage(path, enabled, templateNames);

        // Parse npc_conversations from quest.yml only
        ConfigurationSection npcConvSec = questCfg.getConfigurationSection("npc_conversations");
        if (npcConvSec != null) {
            for (String npcId : npcConvSec.getKeys(false)) {
                String convId = npcConvSec.getString(npcId);
                if (convId != null && !convId.isBlank())
                    pkg.getNpcConversationBindings().put(npcId.toLowerCase(), convId);
            }
        }

        List<YamlConfiguration> configs = collectConfigs(dir);

        // Pass 1a: load events and conditions from all files first
        for (YamlConfiguration cfg : configs) parseEventsAndConditions(cfg, pkg);

        // Expand folder events after all raw events are collected
        expandFolderEvents(pkg);

        // Pass 1b: load objectives, quests, notifications, player_hider (may reference events/conditions)
        for (YamlConfiguration cfg : configs) parseRemainingFeatures(cfg, pkg);

        // Pass 2: load conversations (named refs now fully resolvable)
        for (YamlConfiguration cfg : configs) parseConversations(cfg, pkg);

        return pkg;
    }

    private List<YamlConfiguration> collectConfigs(File dir) {
        List<YamlConfiguration> result = new ArrayList<>();
        for (File f : collectYamlFiles(dir, true)) {
            result.add(YamlConfiguration.loadConfiguration(f));
        }
        return result;
    }

    /** Collects all .yml files in dir; if skipSubPackages, skips sub-dirs that have quest.yml. */
    private List<File> collectYamlFiles(File dir, boolean skipSubPackages) {
        List<File> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return result;
        for (File f : children) {
            if (f.isFile() && f.getName().endsWith(".yml")) {
                result.add(f);
            } else if (f.isDirectory()) {
                if (skipSubPackages && new File(f, "quest.yml").exists()) continue;
                result.addAll(collectYamlFiles(f, skipSubPackages));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Pass 1a: events and conditions
    // -------------------------------------------------------------------------

    private void parseEventsAndConditions(YamlConfiguration cfg, QuestPackage pkg) {
        ConfigurationSection eventsSec = cfg.getConfigurationSection("events");
        if (eventsSec != null) {
            for (String key : eventsSec.getKeys(false)) {
                Object val = eventsSec.get(key);
                List<String> eventList = new ArrayList<>();
                if (val instanceof List<?> list) list.forEach(o -> eventList.add(o.toString()));
                else if (val instanceof String s && !s.isBlank()) {
                    for (String part : splitComma(s)) eventList.add(part);
                }
                pkg.getEvents().put(key.toLowerCase(), eventList);
            }
        }

        ConfigurationSection condSec = cfg.getConfigurationSection("conditions");
        if (condSec != null) {
            for (String key : condSec.getKeys(false)) {
                String val = condSec.getString(key, "");
                if (!val.isBlank()) pkg.getConditions().put(key.toLowerCase(), val.trim());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 1b: objectives, quests, notifications, player_hider
    // -------------------------------------------------------------------------

    private void parseRemainingFeatures(YamlConfiguration cfg, QuestPackage pkg) {
        // objectives: (flat named objectives)
        ConfigurationSection objSec = cfg.getConfigurationSection("objectives");
        if (objSec != null) {
            for (String key : objSec.getKeys(false)) {
                QuestObjective obj = parseObjectiveDsl(key, objSec.getString(key, ""), pkg);
                if (obj != null) pkg.getObjectives().put(key.toLowerCase(), obj);
            }
        }

        // quests:
        ConfigurationSection questsSec = cfg.getConfigurationSection("quests");
        if (questsSec != null) {
            for (String questId : questsSec.getKeys(false)) {
                ConfigurationSection qs = questsSec.getConfigurationSection(questId);
                if (qs == null) continue;
                QuestDefinition def = parseQuestSection(questId, qs, pkg);
                pkg.getQuests().put(questId.toLowerCase(), def);
            }
        }

        // notifications:
        ConfigurationSection notifSec = cfg.getConfigurationSection("notifications");
        if (notifSec != null) {
            for (String catName : notifSec.getKeys(false)) {
                ConfigurationSection cs = notifSec.getConfigurationSection(catName);
                if (cs == null) continue;
                Map<String, String> settings = new HashMap<>();
                for (String k : cs.getKeys(false)) settings.put(k, cs.getString(k, ""));
                pkg.getNotifications().put(catName.toLowerCase(), settings);
            }
        }

        // player_hider:
        ConfigurationSection hiderSec = cfg.getConfigurationSection("player_hider");
        if (hiderSec != null) {
            for (String hiderId : hiderSec.getKeys(false)) {
                ConfigurationSection hs = hiderSec.getConfigurationSection(hiderId);
                if (hs == null) continue;
                List<String> source = hs.getStringList("source_player");
                List<String> target = hs.getStringList("target_player");
                pkg.getPlayerHiders().add(new PlayerHiderEntry(hiderId, source, target));
            }
        }
    }

    /**
     * Expands "folder" events: a single-line value starting with "folder " is replaced
     * by the concatenated action lists of the referenced named events.
     * Only one level of expansion is performed.
     */
    private void expandFolderEvents(QuestPackage pkg) {
        for (Map.Entry<String, List<String>> entry : pkg.getEvents().entrySet()) {
            List<String> val = entry.getValue();
            if (val.size() == 1 && val.get(0).startsWith("folder ")) {
                String[] refs = val.get(0).substring(7).split(",");
                List<String> expanded = new ArrayList<>();
                for (String ref : refs) {
                    String name = ref.trim().toLowerCase();
                    List<String> refList = pkg.getEvents().get(name);
                    if (refList != null) expanded.addAll(refList);
                    else log.warning("[QuestPackages] Folder event ref '" + name + "' not found in package '" + pkg.getPath() + "'");
                }
                entry.setValue(expanded);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2: conversations
    // -------------------------------------------------------------------------

    private void parseConversations(YamlConfiguration cfg, QuestPackage pkg) {
        ConfigurationSection convSec = cfg.getConfigurationSection("conversations");
        if (convSec == null) return;
        for (String convId : convSec.getKeys(false)) {
            ConfigurationSection cs = convSec.getConfigurationSection(convId);
            if (cs == null) continue;
            DialogueDefinition def = parseConversationSection(convId, cs, pkg);
            pkg.getConversations().put(convId.toLowerCase(), def);
        }
    }

    // -------------------------------------------------------------------------
    // Objective parsers
    // -------------------------------------------------------------------------

    /**
     * Parses the compact DSL format:
     *   <type> <target> <amount> [conditions:...] [events:...] [persistent] [auto-once] [notify[:<n>]]
     *
     * For DELAY type:
     *   delay <amount> [ticks] [interval:<n>] [events:...]
     *   Default unit for amount is seconds; add "ticks" flag to use ticks directly.
     */
    private QuestObjective parseObjectiveDsl(String id, String instruction, QuestPackage pkg) {
        if (instruction == null || instruction.isBlank()) return null;
        String[] parts = instruction.trim().split("\\s+");
        if (parts.length == 0) return null;

        String type = parts[0].toLowerCase();

        if (type.equals("delay")) {
            return parseDelayDsl(id, parts, pkg);
        }

        String target = parts.length > 1 ? parts[1] : "";
        int required = 1;
        if (parts.length > 2) {
            try { required = Math.abs(Integer.parseInt(parts[2])); } catch (NumberFormatException ignored) {}
        }

        List<String> conditions = new ArrayList<>();
        List<String> events = new ArrayList<>();
        boolean persistent = false;
        boolean autoOnce = false;
        int notifyInterval = 0;

        for (int i = 3; i < parts.length; i++) {
            String p = parts[i];
            if (p.startsWith("conditions:") || p.startsWith("condition:"))
                Collections.addAll(conditions, p.substring(p.indexOf(':') + 1).split(","));
            else if (p.startsWith("events:") || p.startsWith("event:"))
                Collections.addAll(events, p.substring(p.indexOf(':') + 1).split(","));
            else if (p.equalsIgnoreCase("persistent")) persistent = true;
            else if (p.equalsIgnoreCase("auto-once")) autoOnce = true;
            else if (p.startsWith("notify:")) {
                try { notifyInterval = Integer.parseInt(p.substring(7)); } catch (NumberFormatException e) { notifyInterval = 1; }
            } else if (p.equalsIgnoreCase("notify")) notifyInterval = 1;
        }

        List<String> resolvedEvents = resolveEventRefs(events, pkg);
        return new QuestObjective(id, type, target, required, conditions, resolvedEvents, persistent, autoOnce, notifyInterval);
    }

    /** Parses: delay <amount> [ticks] [interval:<n>] [events:...] */
    private QuestObjective parseDelayDsl(String id, String[] parts, QuestPackage pkg) {
        long rawAmount = 0;
        if (parts.length > 1) {
            try { rawAmount = Long.parseLong(parts[1]); } catch (NumberFormatException ignored) {}
        }

        boolean isTicks = false;
        int intervalTicks = 0;
        List<String> events = new ArrayList<>();

        for (int i = 2; i < parts.length; i++) {
            String p = parts[i];
            if (p.equalsIgnoreCase("ticks")) isTicks = true;
            else if (p.startsWith("interval:")) {
                try { intervalTicks = Integer.parseInt(p.substring(9)); } catch (NumberFormatException ignored) {}
            } else if (p.startsWith("events:") || p.startsWith("event:")) {
                Collections.addAll(events, p.substring(p.indexOf(':') + 1).split(","));
            }
        }

        long delayTicks = isTicks ? rawAmount : rawAmount * 20L;
        int required = (intervalTicks > 0 && delayTicks > 0) ? (int)(delayTicks / intervalTicks) : 1;
        List<String> resolvedEvents = resolveEventRefs(events, pkg);
        return new QuestObjective(id, "delay", id, required,
                List.of(), resolvedEvents, false, false, 0, delayTicks, intervalTicks);
    }

    /**
     * Parses the structured YAML format inside a quests: block.
     * Objectives can be either a DSL string or a full ConfigurationSection.
     */
    private QuestObjective parseStructuredObjective(String id, ConfigurationSection sec, QuestPackage pkg) {
        String type = sec.getString("type", "").toLowerCase();
        if (type.isBlank()) return null;

        if (type.equals("delay")) {
            long rawAmount = sec.getLong("delay", 0L);
            boolean isTicks = sec.getBoolean("ticks", false);
            int intervalTicks = sec.getInt("interval", 0);
            long delayTicks = isTicks ? rawAmount : rawAmount * 20L;
            int required = (intervalTicks > 0 && delayTicks > 0) ? (int)(delayTicks / intervalTicks) : 1;
            List<String> events = resolveEventRefs(parseStringOrList(sec, "events"), pkg);
            return new QuestObjective(id, "delay", id, required,
                    List.of(), events, false, false, 0, delayTicks, intervalTicks);
        }

        String target = sec.getString("target", "");
        int required = sec.getInt("amount", 1);
        boolean persistent = sec.getBoolean("persistent", false);
        boolean autoOnce = sec.getBoolean("auto-once", false);

        int notifyInterval = 0;
        if (sec.contains("notify")) {
            Object notifyVal = sec.get("notify");
            if (notifyVal instanceof Number n) notifyInterval = n.intValue();
            else notifyInterval = 1;
        }

        List<String> conditions = parseStringOrList(sec, "conditions");
        List<String> events = resolveEventRefs(parseStringOrList(sec, "events"), pkg);

        return new QuestObjective(id, type, target, required, conditions, events, persistent, autoOnce, notifyInterval);
    }

    // -------------------------------------------------------------------------
    // Quest section parser
    // -------------------------------------------------------------------------

    private QuestDefinition parseQuestSection(String questId, ConfigurationSection sec, QuestPackage pkg) {
        String name = sec.getString("name", questId);
        List<QuestObjective> objectives = new ArrayList<>();
        ConfigurationSection objSec = sec.getConfigurationSection("objectives");
        if (objSec != null) {
            for (String key : objSec.getKeys(false)) {
                QuestObjective obj;
                if (objSec.isConfigurationSection(key)) {
                    ConfigurationSection os = objSec.getConfigurationSection(key);
                    obj = parseStructuredObjective(key, os, pkg);
                } else {
                    obj = parseObjectiveDsl(key, objSec.getString(key, ""), pkg);
                }
                if (obj != null) objectives.add(obj);
            }
        }

        return new QuestDefinition(questId, name, objectives);
    }

    // -------------------------------------------------------------------------
    // Conversation section parser
    // -------------------------------------------------------------------------

    private DialogueDefinition parseConversationSection(String convId, ConfigurationSection sec, QuestPackage pkg) {
        String quester = sec.getString("quester", convId);
        boolean stop = sec.getBoolean("stop", false);
        List<String> finalEvents = resolveEventRefs(parseStringOrList(sec, "final_events"), pkg);

        List<String> firstList = parseStringOrList(sec, "first");
        String startNode = firstList.isEmpty() ? sec.getString("first", "start") : firstList.get(0);
        if (startNode != null && !startNode.isEmpty() && firstList.isEmpty()) firstList = List.of(startNode);

        // Collect player option keys first so NPC pointers can auto-resolve to player.* IDs
        ConfigurationSection playerOpts = sec.getConfigurationSection("player_options");
        Set<String> playerOptionKeys = new HashSet<>();
        if (playerOpts != null) {
            for (String key : playerOpts.getKeys(false)) playerOptionKeys.add(key.toLowerCase());
        }

        Map<String, DialogueNode> nodes = new LinkedHashMap<>();

        // Parse NPC_options
        ConfigurationSection npcOpts = sec.getConfigurationSection("NPC_options");
        if (npcOpts != null) {
            for (String nodeId : npcOpts.getKeys(false)) {
                ConfigurationSection ns = npcOpts.getConfigurationSection(nodeId);
                if (ns == null) continue;
                List<String> events = resolveEventRefs(parseStringOrList(ns, "events"), pkg);
                List<String> conditions = resolveConditionRefs(parseStringOrList(ns, "conditions"), pkg, convId + "." + nodeId);
                List<String> pointers = parseStringOrList(ns, "pointers");

                List<DialogueChoice> choices = new ArrayList<>();
                for (String ptr : pointers) {
                    String resolved = resolvePointerTarget(ptr, playerOptionKeys);
                    choices.add(new DialogueChoice("__ptr__", resolved, List.of()));
                }
                nodes.put(nodeId, new DialogueNode(nodeId, ns.getString("text", ""),
                        events, conditions, choices, DialogueNode.NodeType.NPC));
            }
        }

        // Parse player_options
        if (playerOpts != null) {
            for (String nodeId : playerOpts.getKeys(false)) {
                ConfigurationSection ns = playerOpts.getConfigurationSection(nodeId);
                if (ns == null) continue;
                List<String> events = resolveEventRefs(parseStringOrList(ns, "events"), pkg);
                List<String> conditions = resolveConditionRefs(parseStringOrList(ns, "conditions"), pkg, convId + ".player." + nodeId);
                List<String> pointers = parseStringOrList(ns, "pointers");

                List<DialogueChoice> pointerChoices = new ArrayList<>();
                for (String ptr : pointers) {
                    String resolved = resolvePointerTarget(ptr, playerOptionKeys);
                    pointerChoices.add(new DialogueChoice("__ptr__", resolved, events));
                }
                nodes.put("player." + nodeId, new DialogueNode("player." + nodeId,
                        ns.getString("text", ""), List.of(), conditions, pointerChoices,
                        DialogueNode.NodeType.PLAYER));
            }
        }

        // Adjust firstList: resolve player option references in first: list
        List<String> resolvedFirst = new ArrayList<>();
        for (String f : firstList) {
            resolvedFirst.add(resolvePointerTarget(f, playerOptionKeys));
        }

        return new DialogueDefinition(convId, quester, resolvedFirst, startNode, stop, finalEvents, nodes);
    }

    /**
     * Resolves a pointer string to its full node ID.
     * If the pointer matches a player option key (without prefix), returns "player.<ptr>".
     * If it already starts with "player.", leaves it as-is.
     * Otherwise returns as-is (NPC node or cross-conversation reference).
     */
    private String resolvePointerTarget(String ptr, Set<String> playerOptionKeys) {
        if (ptr.startsWith("player.")) return ptr;
        if (playerOptionKeys.contains(ptr.toLowerCase())) return "player." + ptr;
        return ptr;
    }

    // -------------------------------------------------------------------------
    // Named reference resolvers
    // -------------------------------------------------------------------------

    /**
     * Resolves event references against the package's named events map.
     * Each token is either a named event (expanded to its action list) or an inline DSL string.
     */
    private List<String> resolveEventRefs(List<String> refs, QuestPackage pkg) {
        if (refs.isEmpty()) return List.of();
        List<String> resolved = new ArrayList<>();
        for (String ref : refs) {
            String trimmed = ref.trim();
            if (trimmed.isEmpty()) continue;
            List<String> named = pkg.getEvents().get(trimmed.toLowerCase());
            if (named != null) resolved.addAll(named);
            else resolved.add(trimmed); // inline DSL
        }
        return resolved;
    }

    /**
     * Resolves condition references in conversations.
     * Each token MUST be a named condition from the package's conditions map (with optional ! prefix).
     * Inline DSL strings are rejected with a warning.
     */
    private List<String> resolveConditionRefs(List<String> refs, QuestPackage pkg, String location) {
        if (refs.isEmpty()) return List.of();
        List<String> resolved = new ArrayList<>();
        for (String ref : refs) {
            String trimmed = ref.trim();
            if (trimmed.isEmpty()) continue;
            boolean negate = trimmed.startsWith("!");
            String name = negate ? trimmed.substring(1).trim() : trimmed;
            String dsl = pkg.getConditions().get(name.toLowerCase());
            if (dsl != null) {
                resolved.add(negate ? "!" + dsl : dsl);
            } else {
                log.warning("[QuestPackages] Unknown condition '" + name + "' at " + location
                        + " — conditions in conversations must reference named conditions from conditions: sections");
            }
        }
        return resolved;
    }

    // -------------------------------------------------------------------------
    // Template merging
    // -------------------------------------------------------------------------

    private void mergeTemplates(QuestPackage pkg, Map<String, QuestPackage> templates) {
        for (String tplName : pkg.getTemplateNames()) {
            QuestPackage tpl = templates.get(tplName.toLowerCase());
            if (tpl == null) { log.warning("[QuestPackages] Template not found: " + tplName); continue; }
            tpl.getEvents().forEach(pkg.getEvents()::putIfAbsent);
            tpl.getConditions().forEach(pkg.getConditions()::putIfAbsent);
            tpl.getObjectives().forEach(pkg.getObjectives()::putIfAbsent);
            tpl.getQuests().forEach(pkg.getQuests()::putIfAbsent);
            tpl.getConversations().forEach(pkg.getConversations()::putIfAbsent);
            tpl.getNotifications().forEach(pkg.getNotifications()::putIfAbsent);
        }
    }

    // -------------------------------------------------------------------------
    // Apply loaded features to live managers
    // -------------------------------------------------------------------------

    private void applyToManagers() {
        QuestManager qm = plugin.getQuestManager();
        NotifyManager nm = plugin.getNotifyManager();
        var dialogueMgr = plugin.getDialogueManager();
        PlayerHiderManager hiderMgr = plugin.getQuestModule() != null
                ? plugin.getQuestModule().getPlayerHiderManager() : null;
        var npcModule = plugin.getNpcModule();

        if (hiderMgr != null) hiderMgr.clear();

        for (QuestPackage pkg : packages) {
            if (qm != null)
                pkg.getQuests().forEach((id, def) -> qm.getRegistry().register(id, def));

            if (dialogueMgr != null)
                pkg.getConversations().forEach((id, def) -> dialogueMgr.getDialogueRegistry().register(id, def));

            if (nm != null)
                pkg.getNotifications().forEach(nm::loadCategory);

            if (hiderMgr != null)
                pkg.getPlayerHiders().forEach(hiderMgr::addEntry);

            // Apply npc_conversations bindings
            if (npcModule != null && !pkg.getNpcConversationBindings().isEmpty()) {
                var npcRegistry = npcModule.getNpcRegistry();
                pkg.getNpcConversationBindings().forEach((npcId, convId) ->
                    npcRegistry.get(npcId).ifPresent(existing ->
                        npcRegistry.register(existing.getId(), existing.withConversation(convId))));
            }
        }

        log.info("[QuestPackages] Loaded " + packages.size() + " package(s).");
    }

    // -------------------------------------------------------------------------
    // Cross-package reference resolver
    // -------------------------------------------------------------------------

    public List<String> resolveEvent(String ref, String sourcePackagePath) {
        String[] parts = ref.split(">", 2);
        if (parts.length < 2) return null;
        String pkgPath = resolvePackagePath(parts[0], sourcePackagePath);
        String featureName = parts[1].toLowerCase();
        for (QuestPackage pkg : packages) {
            if (pkg.getPath().equalsIgnoreCase(pkgPath)) {
                return pkg.getEvents().get(featureName);
            }
        }
        return null;
    }

    private String resolvePackagePath(String raw, String sourcePath) {
        if (!raw.startsWith("_") && !raw.startsWith("-")) return raw;
        String[] sourceSegments = sourcePath.split("-");
        Deque<String> stack = new ArrayDeque<>(Arrays.asList(sourceSegments));
        for (String token : raw.split("(?=[-_])")) {
            if (token.equals("_")) { if (!stack.isEmpty()) stack.pollLast(); }
            else if (token.startsWith("-")) stack.addLast(token.substring(1));
        }
        return String.join("-", stack);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Parses a YAML field that can be either a string list or a comma-separated string. */
    private static List<String> parseStringOrList(ConfigurationSection sec, String key) {
        Object val = sec.get(key);
        if (val instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            list.forEach(o -> { if (o != null) result.add(o.toString()); });
            return result;
        } else if (val instanceof String s && !s.isBlank()) {
            return splitComma(s);
        }
        return List.of();
    }

    private static List<String> splitComma(String s) {
        String[] parts = s.split(",");
        List<String> result = new ArrayList<>(parts.length);
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private List<File> safeListDirs(File dir) {
        File[] children = dir.listFiles(File::isDirectory);
        return children != null ? Arrays.asList(children) : List.of();
    }
}
