package org.nakii.valmora.module.gui.parser;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.ClickType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.gui.*;
import org.nakii.valmora.module.gui.components.*;

import java.util.*;

public class GuiDefinitionParser {

    private final Valmora plugin;
    private final ClickHandlerParser clickHandlerParser;

    public GuiDefinitionParser(Valmora plugin) {
        this.plugin = plugin;
        this.clickHandlerParser = new ClickHandlerParser(plugin);
    }

    public LoadResult<GuiDefinition, String> parse(String id, ConfigurationSection section, String filePath) {
        try {
            ConfigReader reader = ConfigReader.of(section).knownKeys(KNOWN_KEYS);
            // Loop variables of PAGINATED components are names local to this GUI, not unknown
            // script namespaces — declare them before anything here gets compiled.
            ConfigurationSection compPre = section.getConfigurationSection("components");
            if (compPre != null && reader.scope() != null) {
                for (String key : compPre.getKeys(false)) {
                    ConfigurationSection c = compPre.getConfigurationSection(key);
                    if (c == null || !"PAGINATED".equalsIgnoreCase(c.getString("type", ""))) continue;
                    reader.scope().declareLocal(c.getString("iterator", "loop_item"));
                    if (c.getBoolean("destructure", false)) reader.scope().declareLocal("*");
                }
                reader.scope().declareLocal("candidate");
            }
            // HC-162: gui.defaults.* — global fallback when an individual GUI's own YAML omits
            // these fields.
            var cfg = plugin != null ? plugin.getConfig() : null;
            String titleStr = section.getString("title", cfg != null ? cfg.getString("gui.defaults.title", "Inventory") : "Inventory");
            int updateInterval = section.getInt("update-interval", cfg != null ? cfg.getInt("gui.defaults.update-interval-ticks", 0) : 0);
            List<String> layoutRows = section.getStringList("layout");
            // `rows:` can explicitly request a taller inventory than the layout lines provide
            // (e.g. to leave blank rows for padding). It can never shrink below the layout size.
            int rows = Math.max(section.getInt("rows", layoutRows.size()), layoutRows.size());
            if (rows > 6) {
                reader.warn("rows", "a chest GUI has at most 6 rows, this one needs " + rows + " — extra rows won't show");
            }
            for (int i = 0; i < layoutRows.size(); i++) {
                if (layoutRows.get(i).length() > 9) {
                    reader.warn("layout", "row " + (i + 1) + " is " + layoutRows.get(i).length() + " characters wide — only the first 9 are used");
                }
            }
            String machine = section.getString("machine", cfg != null ? cfg.getString("gui.defaults.machine", id) : id);

            List<List<Character>> layout = new ArrayList<>();
            for (String rowStr : layoutRows) {
                List<Character> rowChars = new ArrayList<>();
                for (char c : rowStr.toCharArray()) {
                    rowChars.add(c);
                }
                while (rowChars.size() < 9) rowChars.add(' '); // Pad to 9 columns
                layout.add(rowChars);
            }
            while (layout.size() < rows) {
                List<Character> blankRow = new ArrayList<>();
                for (int i = 0; i < 9; i++) blankRow.add(' ');
                layout.add(blankRow);
            }

            Map<Character, GuiComponent> components = new HashMap<>();
            ConfigurationSection compSection = section.getConfigurationSection("components");
            if (compSection != null) {
                for (String key : compSection.getKeys(false)) {
                    // REMOVED: if (key.length() != 1) continue; 
                    
                    // Pass the key to parseComponent so we know the path string
                    ConfigurationSection componentSection = compSection.getConfigurationSection(key);
                    if (componentSection == null) {
                        reader.warn("components." + key, "expected a component section — ignored");
                        continue;
                    }
                    GuiComponent component = ScriptCompile.at("components." + key, () -> parseComponent(componentSection, key));
                    if (component != null) {
                        for (char c : key.toCharArray()) {
                            components.put(c, component); // Map every character in the string
                        }
                    }
                }
            }

            // Layout ↔ components: a layout letter with no component renders nothing; a component
            // whose letters never appear in the layout is never shown.
            java.util.Set<Character> used = new java.util.HashSet<>();
            for (String rowStr : layoutRows) for (char c : rowStr.toCharArray()) if (c != ' ') used.add(c);
            for (char c : used) {
                if (!components.containsKey(c)) {
                    reader.warn("layout", "letter '" + c + "' has no component under components: — those slots stay empty");
                }
            }
            if (compSection != null) {
                for (String key : compSection.getKeys(false)) {
                    boolean anyUsed = false;
                    for (char c : key.toCharArray()) anyUsed |= used.contains(c);
                    if (!anyUsed) reader.warn("components." + key, "never used in layout: — this component is never shown");
                }
            }

            GuiEventBlock onOpen = ScriptCompile.at("on-open", () -> parseEventBlock(section.getConfigurationSection("on-open")));
            GuiEventBlock onClose = ScriptCompile.at("on-close", () -> parseEventBlock(section.getConfigurationSection("on-close")));
            GuiEventBlock onSlotUpdate = ScriptCompile.at("on-slot-update", () -> parseEventBlock(section.getConfigurationSection("on-slot-update")));
            GuiEventBlock onUpdate = ScriptCompile.at("on-update", () -> parseEventBlock(section.getConfigurationSection("on-update")));
            if (section.contains("on-update") && updateInterval <= 0) {
                reader.warn("on-update", "has actions but update-interval is 0 — they never run");
            }

            String command = section.getString("command", null);
            // HC-167: gui.defaults.command-permission — stays null (permissive) by default,
            // matching the original hardcoded behavior; a server can opt into a safer default.
            String commandPermission = section.getString("command-permission",
                    cfg != null ? cfg.getString("gui.defaults.command-permission", null) : null);

            GuiDefinition def = new GuiDefinition(id, titleStr, updateInterval, rows, machine, layout, components, onOpen, onClose, onSlotUpdate, onUpdate, command, commandPermission);
            return LoadResult.success(def);
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Error parsing GUI " + id + ": " + e.getMessage());
        }
    }

    private GuiComponent parseComponent(ConfigurationSection section, String key) {
        String type = section.getString("type", "DISPLAY").toUpperCase();
        ConfigReader reader = ConfigReader.of(section);
        if (!COMPONENT_TYPES.contains(type)) {
            reader.warn("type", "unknown component type '" + section.getString("type") + "' — component ignored",
                    Suggestions.hint(type, COMPONENT_TYPES));
            return null;
        }
        if ((type.equals("INPUT") || type.equals("OUTPUT") || type.equals("STORAGE")) && section.getString("id") == null) {
            reader.warn("id", type + " components need an id: (recipes and scripts refer to the slot by it)");
        }
        
        return switch (type) {
            case "DISPLAY" -> {
                GuiItemStack item = parseItemStack(section.getConfigurationSection("display-item"));
                Map<ClickType, ClickHandler> actions = parseActions(section.getConfigurationSection("actions"));
                
                List<PaginatedState> states = new ArrayList<>();
                ConfigurationSection statesSection = section.getConfigurationSection("states");
                if (statesSection != null) {
                    for (String key1 : statesSection.getKeys(false)) {
                        ConfigurationSection stateSec = statesSection.getConfigurationSection(key1);
                        if (stateSec == null) continue;
                        String condition = stateSec.getString("condition", "default");
                        validateStateCondition(condition, "states." + key1 + ".condition");
                        GuiItemStack displayItem = parseItemStack(stateSec.getConfigurationSection("display-item"));
                        if (displayItem == null) displayItem = parseItemStack(stateSec);
                        Map<ClickType, ClickHandler> stateActions = parseActions(stateSec.getConfigurationSection("actions"));
                        states.add(new PaginatedState(condition, displayItem, stateActions));
                    }
                }
                yield new DisplayComponent(item, actions, states);
            }
            case "INPUT" -> new InputComponent(section.getString("id"));
            case "OUTPUT" -> new OutputComponent(section.getString("id"));
            case "STORAGE" -> {
                String sid = section.getString("id");
                StorageComponent.Owner owner;
                owner = reader.enumOf("owner", StorageComponent.Owner.class, StorageComponent.Owner.PLAYER);
                String storageId = section.getString("storage-id", sid);
                String conditionStr = section.getString("condition", null);
                Condition condition = conditionStr != null
                        ? ScriptCompile.at("condition", () -> plugin.getScriptModule().getConditionParser().parse(conditionStr))
                        : null;
                boolean openContainer = section.getBoolean("open-container", true);
                yield new StorageComponent(sid, owner, storageId, condition, openContainer);
            }
            case "PAGINATED" -> {
                String list = section.getString("list");
                if (list == null || list.isBlank()) reader.warn("list", "PAGINATED components need a list: to iterate — nothing will render");
                String iterator = section.getString("iterator", "loop_item");
                boolean destructure = section.getBoolean("destructure", false);
                List<PaginatedState> states = new ArrayList<>();
                String path = section.getString("path", key.length() > 1 ? key : null);
                String sortOrder = section.getString("sort", "none");
                String sortKey = section.getString("sort-key", null);

                ConfigurationSection statesSection = section.getConfigurationSection("states");
                if (statesSection != null) {
                    for (String key1 : statesSection.getKeys(false)) {
                        ConfigurationSection stateSec = statesSection.getConfigurationSection(key1);
                        if (stateSec == null) continue;

                        String condition = stateSec.getString("condition", "default");
                        validateStateCondition(condition, "states." + key1 + ".condition");
                        ConfigurationSection itemSec = stateSec.getConfigurationSection("display-item");
                        if (itemSec == null) {
                            itemSec = stateSec;
                        }
                        GuiItemStack displayItem = parseItemStack(itemSec);
                        Map<ClickType, ClickHandler> actions = parseActions(stateSec.getConfigurationSection("actions"));
                        states.add(new PaginatedState(condition, displayItem, actions));
                    }
                }
                yield new PaginatedComponent(list, iterator, destructure, states, path, sortOrder, sortKey);
            }
            case "PREVIOUS_PAGE" -> {
                GuiItemStack item = parseItemStack(section.getConfigurationSection("display-item"));
                GuiItemStack fallback = parseItemStack(section.getConfigurationSection("fallback"));
                yield new PageButtonComponent(false, item, fallback);
            }
            case "NEXT_PAGE" -> {
                GuiItemStack item = parseItemStack(section.getConfigurationSection("display-item"));
                GuiItemStack fallback = parseItemStack(section.getConfigurationSection("fallback"));
                yield new PageButtonComponent(true, item, fallback);
            }
            default -> null;
        };
    }

    private GuiItemStack parseItemStack(ConfigurationSection section) {
        if (section == null) return null;
        String matStr = section.getString("material", section.getString("item", "AIR"));
        String name = section.getString("name", "");
        List<String> lore = section.getStringList("lore");
        // HC-164: null (not 0) means "not set" — 0 is a valid custom-model-data value.
        Integer cmd = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        int amount = section.getInt("amount", 1);
        return new GuiItemStack(matStr, name, lore, cmd, amount);
    }

    private Map<ClickType, ClickHandler> parseActions(ConfigurationSection section) {
        Map<ClickType, ClickHandler> actions = new HashMap<>();
        if (section == null) return actions;

        ConfigReader reader = ConfigReader.of(section);
        for (String key : section.getKeys(false)) {
            ClickType type = ConfigReader.parseEnum(key, ClickType.class);
            if (type == null) {
                reader.warn(key, "unknown click type '" + key + "' — these actions never run",
                        Suggestions.hint(key, ConfigReader.enumNames(ClickType.class)));
                continue;
            }
            ClickHandler handler = ScriptCompile.at("actions." + key, () -> clickHandlerParser.parse(section.getConfigurationSection(key)));
            if (handler != null) {
                actions.put(type, handler);
            }
        }
        return actions;
    }

    private GuiEventBlock parseEventBlock(ConfigurationSection section) {
        if (section == null) return new GuiEventBlock(null, null, null);
        ConfigReader.of(section).knownKeys("conditions", "actions", "fail-actions");
        Condition conditions = ScriptCompile.at("conditions", () -> plugin.getScriptModule().getConditionParser().parseList(section.getStringList("conditions")));
        CompiledEvent actions = ScriptCompile.at("actions", () -> plugin.getScriptModule().getEventParser().parseList(section.getStringList("actions")));
        CompiledEvent failActions = ScriptCompile.at("fail-actions", () -> plugin.getScriptModule().getEventParser().parseList(section.getStringList("fail-actions")));
        return new GuiEventBlock(conditions, actions, failActions);
    }

    /** States' conditions are compiled lazily by the renderer; compile once here so problems show at load. */
    private void validateStateCondition(String condition, String path) {
        if (condition == null || condition.isBlank() || condition.equalsIgnoreCase("default")) return;
        if (plugin == null || plugin.getScriptModule() == null) return;
        ScriptCompile.at(path, () -> plugin.getScriptModule().getConditionParser().parse(condition));
    }

    private static final List<String> KNOWN_KEYS = List.of(
            "title", "update-interval", "layout", "rows", "machine", "components", "on-open", "on-close",
            "on-slot-update", "on-update", "command", "command-permission");

    private static final List<String> COMPONENT_TYPES = List.of(
            "DISPLAY", "INPUT", "OUTPUT", "STORAGE", "PAGINATED", "PREVIOUS_PAGE", "NEXT_PAGE");
}
