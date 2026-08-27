package org.nakii.valmora.module.modifier.value;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;

/**
 * Arbitrary formula using the existing Script expression engine (docs/
 * Valmora_Modifier_Framework_Design.docx §5/§18), e.g. {@code "10 + $item.rarity.rank$ * 5"}.
 * Requires a non-null {@link ExecutionContext} to resolve any {@code $variable$} tokens; returns
 * {@code 0} if none is supplied.
 */
public class ExpressionValue implements ValueResolver {

    private final String expression;

    public ExpressionValue(String expression) {
        this.expression = expression;
    }

    @Override
    public double resolve(RarityDefinition rarity, int tier, ExecutionContext context) {
        if (context == null) return 0;
        Object result = ValmoraAPI.getInstance().getScriptModule().getExpressionEvaluator()
                .evaluate(expression, context);
        if (result instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(result));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
