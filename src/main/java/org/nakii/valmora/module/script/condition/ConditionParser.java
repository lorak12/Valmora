package org.nakii.valmora.module.script.condition;

import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw strings or lists into Condition objects.
 */
public class ConditionParser {

    private final ExpressionParser expressionParser;

    public ConditionParser(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    /**
     * Parses a single condition string.
     * @param raw condition string (e.g., "tag complete" or "$hp < 10")
     * @return compiled condition
     */
    public Condition parse(String raw) {
        if (raw == null || raw.isEmpty()) return new ConditionGroup(new ArrayList<>());

        String clean = raw.trim();
        if (clean.startsWith("tag ")) {
            return new TagCondition(clean.substring(4).trim());
        }

        // Default to expression
        return new ExpressionCondition(expressionParser.parse(clean));
    }

    /**
     * Parses a list of condition strings into a group.
     * @param list strings from YAML
     * @return compiled group
     */
    public ConditionGroup parseList(List<String> list) {
        if (list == null || list.isEmpty()) return new ConditionGroup(new ArrayList<>());
        List<Condition> conditions = new ArrayList<>();
        for (String s : list) {
            conditions.add(parse(s));
        }
        return new ConditionGroup(conditions);
    }
}
