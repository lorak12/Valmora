package org.nakii.valmora.module.item.impl;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.item.AbilityMechanic;

import java.util.List;

public class ScriptMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "SCRIPT";
    }

    @Override
    public void execute(ExecutionContext context) {
        List<String> events = context.getParams().getStringList("events");
        if (events.isEmpty()) return;

        CompiledEvent compiled = ValmoraAPI.getInstance()
                .getScriptModule()
                .getEventParser()
                .parseList(events);

        compiled.execute(context);
    }
}
