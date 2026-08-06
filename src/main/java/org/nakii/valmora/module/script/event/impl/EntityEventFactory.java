package org.nakii.valmora.module.script.event.impl;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.item.TargetResolver;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.Formatter;

import java.util.Arrays;

/**
 * Sets a live property (health, max health, or display name) on a resolved entity. The DSL-level
 * counterpart to the "mob evolution" style patterns from docs/COMBAT_PIPELINE_ANALYSIS.md §7
 * (Example 4) — e.g. a pipeline stage boosting a boss's max health after N critical hits.
 *
 * <p>DSL: {@code entity set <health|max_health|name> <value> [<target-selector>]}
 * <ul>
 *     <li>{@code <value>} for {@code health}/{@code max_health} is parsed as a full expression
 *     (same engine as {@code damage_formula.yml}) — literals, {@code $variable$} tokens, and
 *     arithmetic are all supported, e.g. {@code "entity set max_health $target.max_health$ * 1.5"}.</li>
 *     <li>{@code <value>} for {@code name} is a MiniMessage string with {@code $variable$}
 *     substitution (not arithmetic) — spaces are fine since it's everything up to the trailing
 *     selector, if any.</li>
 *     <li>{@code <target-selector>} is any {@link TargetResolver} selector (default {@code @target});
 *     detected as the trailing argument if it starts with {@code @}.</li>
 * </ul>
 */
public class EntityEventFactory implements EventFactory {

    private final ScriptModule scriptModule;

    public EntityEventFactory(ScriptModule scriptModule) {
        this.scriptModule = scriptModule;
    }

    @Override
    public String getName() {
        return "entity";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) return context -> {};

        String property = args[1].toLowerCase();
        String selector = "@target";
        int valueEnd = args.length;
        if (args[args.length - 1].startsWith("@")) {
            selector = args[args.length - 1];
            valueEnd = args.length - 1;
        }
        if (valueEnd <= 2) return context -> {};
        String rawValue = String.join(" ", Arrays.copyOfRange(args, 2, valueEnd));
        final String finalSelector = selector;

        if (property.equals("name")) {
            return context -> {
                String resolved = context.getVariableResolver().resolveTemplate(rawValue, context);
                for (LivingEntity target : TargetResolver.resolve(finalSelector, context)) {
                    target.customName(Formatter.format(resolved));
                    target.setCustomNameVisible(true);
                }
            };
        }

        // health / max_health — compiled once as an expression, not re-parsed per execution.
        Expression expr = scriptModule.getExpressionParser().parse(rawValue);

        return context -> {
            Object result = expr.evaluate(context);
            if (!(result instanceof Number n)) return;
            double value = n.doubleValue();

            for (LivingEntity target : TargetResolver.resolve(finalSelector, context)) {
                if (property.equals("max_health")) {
                    AttributeInstance attr = target.getAttribute(Attribute.MAX_HEALTH);
                    if (attr != null) attr.setBaseValue(Math.max(1.0, value));
                } else if (property.equals("health")) {
                    double max = maxHealth(target);
                    target.setHealth(Math.max(0.0, Math.min(value, max)));
                }
            }
        };
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : entity.getHealth();
    }
}
