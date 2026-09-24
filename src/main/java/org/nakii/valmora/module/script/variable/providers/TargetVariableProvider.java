package org.nakii.valmora.module.script.variable.providers;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.util.Keys;

import java.util.Optional;

/**
 * Exposes the current ability/combat target to scripts and conditions via the {@code target}
 * namespace:
 * <ul>
 *     <li>{@code $target.type$} — Bukkit entity type name (e.g. {@code ZOMBIE})</li>
 *     <li>{@code $target.health$} — current health</li>
 *     <li>{@code $target.max_health$} — maximum health</li>
 *     <li>{@code $target.hp_percent$} — {@code (health / max_health) * 100.0} (added for the enchant
 *     overhaul's {@code execute}/{@code first_strike}-style combat conditions)</li>
 *     <li>{@code $target.missing_hp_percent$} — {@code 100.0 - hp_percent}</li>
 *     <li>{@code $target.level$} — the custom mob's configured {@code level}, or 1 for players and
 *     vanilla mobs</li>
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
            case "level" -> mobLevel(target);
            case "name" -> target.getName();
            case "hp_percent" -> hpPercent(target);
            case "missing_hp_percent" -> 100.0 - hpPercent(target);
            default -> null;
        };
    }

    private int mobLevel(LivingEntity target) {
        String mobId = target.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobId == null) return 1;
        var mobManager = ValmoraAPI.getInstance().getMobManager();
        MobDefinition definition = mobManager != null ? mobManager.getMobDefinition(mobId) : null;
        return definition != null ? definition.getLevel() : 1;
    }

    private double hpPercent(LivingEntity target) {
        var attr = target.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : target.getHealth();
        if (max <= 0) return 0.0;
        return (target.getHealth() / max) * 100.0;
    }
}
