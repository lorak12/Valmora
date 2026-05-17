package org.nakii.valmora.module.script.condition;

import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.quest.points.PointCondition;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.ArrayList;
import java.util.List;

public class ConditionParser {

    private final ExpressionParser expressionParser;

    public ConditionParser(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    /**
     * Parses a single condition string. Supports an optional leading '!' negation prefix.
     * Keywords: tag, health, hunger, location, zone, variable, objective, quest, point.
     * Anything else is treated as an expression.
     */
    public Condition parse(String raw) {
        if (raw == null || raw.isEmpty()) return new ConditionGroup(new ArrayList<>());

        String clean = raw.trim();

        // Negation wrapper
        if (clean.startsWith("!")) {
            Condition inner = parse(clean.substring(1));
            return ctx -> !inner.evaluate(ctx);
        }

        if (clean.startsWith("tag "))
            return new TagCondition(clean.substring(4).trim());

        if (clean.startsWith("health ")) {
            try { return new HealthCondition(Double.parseDouble(clean.substring(7).trim())); }
            catch (NumberFormatException ignored) {}
        }

        if (clean.startsWith("hunger ")) {
            try { return new HungerCondition(Integer.parseInt(clean.substring(7).trim())); }
            catch (NumberFormatException ignored) {}
        }

        if (clean.startsWith("location ")) {
            String[] parts = clean.substring(9).trim().split("\\s+");
            if (parts.length >= 2) {
                try {
                    double radius = Double.parseDouble(parts[1]);
                    LocationCondition lc = LocationCondition.parse(parts[0], radius);
                    if (lc != null) return lc;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (clean.startsWith("zone "))
            return new ZoneCondition(clean.substring(5).trim());

        if (clean.startsWith("variable ")) {
            String[] parts = clean.substring(9).trim().split("\\s+");
            if (parts.length >= 3)
                return new VariableCondition(parts[0], parts[1], parts[2]);
        }

        if (clean.startsWith("objective "))
            return new ObjectiveActiveCondition(clean.substring(10).trim());

        if (clean.startsWith("quest ")) {
            String[] parts = clean.substring(6).trim().split("\\s+");
            if (parts.length >= 2)
                return new QuestStatusCondition(parts[0], parts[1]);
        }

        if (clean.startsWith("point ")) {
            String[] parts = clean.substring(6).trim().split("\\s+");
            if (parts.length >= 2) {
                try { return new PointCondition(parts[0], Integer.parseInt(parts[1])); }
                catch (NumberFormatException ignored) {}
            }
        }

        return new ExpressionCondition(expressionParser.parse(clean));
    }

    /**
     * Parses a list of condition strings into an AND group.
     */
    public ConditionGroup parseList(List<String> list) {
        if (list == null || list.isEmpty()) return new ConditionGroup(new ArrayList<>());
        List<Condition> conditions = new ArrayList<>();
        for (String s : list) conditions.add(parse(s));
        return new ConditionGroup(conditions);
    }

    /**
     * Parses a comma-separated inline condition token (used by EventParser and objective suffixes).
     * Each name may be prefixed with '!' for negation.
     * Names are treated as inline condition strings (not package-named conditions).
     */
    public ConditionGroup parseInlineList(String raw) {
        if (raw == null || raw.isEmpty()) return new ConditionGroup(new ArrayList<>());
        List<Condition> conditions = new ArrayList<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) conditions.add(parse(t));
        }
        return new ConditionGroup(conditions);
    }
}
