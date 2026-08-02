package org.nakii.valmora.module.script.variable.providers;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.Optional;

/**
 * Exposes the current ability/combat target to scripts and conditions via the {@code target}
 * namespace:
 * <ul>
 *     <li>{@code $target.type$} — Bukkit entity type name (e.g. {@code ZOMBIE})</li>
 *     <li>{@code $target.health$} — current health</li>
 *     <li>{@code $target.max_health$} — maximum health</li>
 *     <li>{@code $target.level$} — custom mob level if tracked, otherwise 1</li>
 * </ul>
 */
public class TargetVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "target";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        Optional<LivingEntity> maybeTarget = context.getTarget();
        if (maybeTarget.isEmpty() || path.length == 0) return null;
        LivingEntity target = maybeTarget.get();

        return switch (path[0].toLowerCase()) {
            case "type" -> target.getType().name();
            case "health" -> target.getHealth();
            case "max_health" -> {
                var attr = target.getAttribute(Attribute.MAX_HEALTH);
                yield attr != null ? attr.getValue() : target.getHealth();
            }
            case "level" -> 1; // Custom mob levels are wired in a later phase.
            case "name" -> target.getName();
            default -> null;
        };
    }
}
