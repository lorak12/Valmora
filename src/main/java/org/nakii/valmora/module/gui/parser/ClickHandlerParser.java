package org.nakii.valmora.module.gui.parser;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.gui.ClickHandler;
import org.nakii.valmora.module.script.ScriptModule;


public class ClickHandlerParser {

    private final ScriptModule scriptModule;

    public ClickHandlerParser(Valmora plugin) {
        this.scriptModule = plugin.getScriptModule();
    }

    public ClickHandler parse(ConfigurationSection section) {
        if (section == null) return null;

        Condition conditions = scriptModule.getConditionParser().parseList(section.getStringList("conditions"));
        CompiledEvent actions = scriptModule.getEventParser().parseList(section.getStringList("actions"));
        CompiledEvent failActions = scriptModule.getEventParser().parseList(section.getStringList("fail-actions"));

        return new ClickHandler(conditions, actions, failActions);
    }
}
