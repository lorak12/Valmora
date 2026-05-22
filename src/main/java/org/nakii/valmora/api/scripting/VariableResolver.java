package org.nakii.valmora.api.scripting;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Interface that allows resolving variable paths into objects.
 */
public interface VariableResolver {

    /**
     * Resolves a variable path into an object using the provided context.
     * @param path specific path (e.g., "$player.stat.HEALTH$")
     * @param context the context to use for resolution
     * @return the resolved object, or null if not found or invalid
     */
    Object resolve(String path, ExecutionContext context);

    /**
     * Replaces all $variable$ tokens in a template string with their resolved values.
     * Numbers with no fractional part are rendered as integers (e.g. 100.0 → "100").
     */
    default String resolveTemplate(String template, ExecutionContext context) {
        if (template == null) return "";
        if (!template.contains("$")) return template;

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf('$', i);
            if (start == -1) { result.append(template, i, template.length()); break; }
            result.append(template, i, start);
            int end = template.indexOf('$', start + 1);
            if (end == -1) { result.append(template, start, template.length()); break; }

            String varPath = template.substring(start + 1, end);
            Object resolved = resolve(varPath, context);
            if (resolved instanceof Number n) {
                double d = n.doubleValue();
                result.append(d == Math.floor(d) && !Double.isInfinite(d)
                        ? String.valueOf((long) d) : String.valueOf(d));
            } else {
                result.append(resolved != null ? resolved.toString() : "");
            }
            i = end + 1;
        }
        return result.toString();
    }
}
