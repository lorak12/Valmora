package org.nakii.valmora.api.scripting;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Service for evaluating expressions.
 * Note: For best performance, use pre-compiled Expression objects during runtime.
 */
public interface ExpressionEvaluator {

    /**
     * Evaluates a raw expression string.
     * @param rawExpression the string to parse and evaluate
     * @param context the execution context
     * @return the evaluation result
     */
    Object evaluate(String rawExpression, ExecutionContext context);
}
