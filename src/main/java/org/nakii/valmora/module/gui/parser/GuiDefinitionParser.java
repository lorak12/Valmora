package org.nakii.valmora.module.gui.parser;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.ClickType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
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
            String titleStr = section.getString("title", "Inventory");
            int updateInterval = section.getInt("update-interval", 0);
            List<String> layoutRows = section.getStringList("layout");
            int rows = layoutRows.size();
            String machine = section.getString("machine", id);

            List<List<Character>> layout = new ArrayList<>();
            for (String rowStr : layoutRows) {
                List<Character> rowChars = new ArrayList<>();
                for (char c : rowStr.toCharArray()) {
                    rowChars.add(c);
                }
                while (rowChars.size() < 9) rowChars.add(' '); // Pad to 9 columns
                layout.add(rowChars);
            }

            Map<Character, GuiComponent> components = new HashMap<>();
            ConfigurationSection compSection = section.getConfigurationSection("components");
            if (compSection != null) {
                for (String key : compSection.getKeys(false)) {
                    // REMOVED: if (key.length() != 1) continue; 
                    
                    // Pass the key to parseComponent so we know the path string
                    GuiComponent component = parseComponent(compSection.getConfigurationSection(key), key); 
                    if (component != null) {
                        for (char c : key.toCharArray()) {
                            components.put(c, component); // Map every character in the string
                        }
                    }
                }
            }

            GuiEventBlock onOpen = parseEventBlock(section.getConfigurationSection("on-open"));
            GuiEventBlock onClose = parseEventBlock(section.getConfigurationSection("on-close"));
            GuiEventBlock onSlotUpdate = parseEventBlock(section.getConfigurationSection("on-slot-update"));
            GuiEventBlock onUpdate = parseEventBlock(section.getConfigurationSection("on-update"));

            GuiDefinition def = new GuiDefinition(id, titleStr, updateInterval, rows, machine, layout, components, onOpen, onClose, onSlotUpdate, onUpdate);
            return LoadResult.success(def);
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Error parsing GUI " + id + ": " + e.getMessage());
        }
    }

    private GuiComponent parseComponent(ConfigurationSection section, String key) {
        String type = section.getString("type", "DISPLAY").toUpperCase();
        
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
            case "PAGINATED" -> {
                String list = section.getString("list");
                String iterator = section.getString("iterator", "loop_item");
                boolean destructure = section.getBoolean("destructure", false);
                List<PaginatedState> states = new ArrayList<>();
                String path = section.getString("path", key.length() > 1 ? key : null);

                ConfigurationSection statesSection = section.getConfigurationSection("states");
                if (statesSection != null) {
                    for (String key1 : statesSection.getKeys(false)) {
                        ConfigurationSection stateSec = statesSection.getConfigurationSection(key1);
                        if (stateSec == null) continue;

                        String condition = stateSec.getString("condition", "default");
                        ConfigurationSection itemSec = stateSec.getConfigurationSection("display-item");
                        if (itemSec == null) {
                            itemSec = stateSec;
                        }
                        GuiItemStack displayItem = parseItemStack(itemSec);
                        Map<ClickType, ClickHandler> actions = parseActions(stateSec.getConfigurationSection("actions"));
                        states.add(new PaginatedState(condition, displayItem, actions));
                    }
                }
                yield new PaginatedComponent(list, iterator, destructure, states, path);
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
        int cmd = section.getInt("custom-model-data", 0);
        int amount = section.getInt("amount", 1);
        return new GuiItemStack(matStr, name, lore, cmd, amount);
    }

    private Map<ClickType, ClickHandler> parseActions(ConfigurationSection section) {
        Map<ClickType, ClickHandler> actions = new HashMap<>();
        if (section == null) return actions;

        for (String key : section.getKeys(false)) {
            try {
                ClickType type = ClickType.valueOf(key.toUpperCase());
                ClickHandler handler = clickHandlerParser.parse(section.getConfigurationSection(key));
                if (handler != null) {
                    actions.put(type, handler);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return actions;
    }

    private GuiEventBlock parseEventBlock(ConfigurationSection section) {
        if (section == null) return new GuiEventBlock(null, null, null);
        Condition conditions = plugin.getScriptModule().getConditionParser().parseList(section.getStringList("conditions"));
        CompiledEvent actions = plugin.getScriptModule().getEventParser().parseList(section.getStringList("actions"));
        CompiledEvent failActions = plugin.getScriptModule().getEventParser().parseList(section.getStringList("fail-actions"));
        return new GuiEventBlock(conditions, actions, failActions);
    }
}
