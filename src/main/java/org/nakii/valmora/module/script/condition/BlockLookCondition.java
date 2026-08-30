package org.nakii.valmora.module.script.condition;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

/**
 * {@code block <material>} — true if the caster is a player currently looking directly at a block
 * of the given material (a short-range ray trace along their line of sight, ignoring transparent
 * blocks). An unrecognized material name is a silent {@code false} rather than a parse error, the
 * same permissive-on-bad-input convention every other {@link ConditionParser} keyword follows.
 */
public record BlockLookCondition(Material material) implements Condition {

    private static final int MAX_DISTANCE = 6;

    public static BlockLookCondition parse(String materialName) {
        return new BlockLookCondition(Material.matchMaterial(materialName.trim()));
    }

    @Override
    public boolean evaluate(ExecutionContext context) {
        if (material == null) return false;
        return context.getPlayerCaster()
                .map(p -> {
                    Block target = p.getTargetBlockExact(MAX_DISTANCE);
                    return target != null && target.getType() == material;
                })
                .orElse(false);
    }
}
