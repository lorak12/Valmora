package org.nakii.valmora.module.script.event.impl;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.ConditionAbortException;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Evaluates a boolean expression. Throws ConditionAbortException when false,
 * short-circuiting the remainder of the action list and triggering fail-actions.
 * DSL: condition <expression>  e.g. "condition $prop.pending$ == deposit"
 */
public class ConditionEvent implements EventFactory {

    private final ScriptModule scriptModule;

    public ConditionEvent(ScriptModule scriptModule) {
        this.scriptModule = scriptModule;
    }

    @Override
    public String getName() {
        return "condition";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length == 0) return context -> {};
        String exprString = String.join(" ", args);
        Expression expr = scriptModule.getExpressionParser().parse(exprString);

        return context -> {
            Object result = expr.evaluate(context);
            boolean passed = result instanceof Boolean b ? b : false;
            if (!passed) throw new ConditionAbortException();
        };
    }
}
