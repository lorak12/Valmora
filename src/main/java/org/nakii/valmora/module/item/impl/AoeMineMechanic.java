package org.nakii.valmora.module.item.impl;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.resource.ResourceManager;
import org.nakii.valmora.module.zone.ZoneResourceConfig;

/**
 * Mines up to {@code radius} adjacent resource blocks matching the origin block's material
 * (Mining Spread). Registered as an {@link AbilityMechanic} for future ability-driven use
 * (e.g. an active "burst mine" item ability); its primary invocation path today is the static
 * {@link #mineRadius} helper, called directly from the resource-block break pipeline for every
 * point of the player's passive Mining Spread stat.
 */
public class AoeMineMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "aoe_mine";
    }

    @Override
    public void execute(ExecutionContext context) {
        context.getPlayerCaster().ifPresent(player -> {
            Location loc = context.getLocation();
            if (loc == null) return;
            org.nakii.valmora.Valmora plugin = org.nakii.valmora.Valmora.getInstance();
            ResourceManager rm = plugin.getResourceModule() != null ? plugin.getResourceModule().getResourceManager() : null;
            if (rm == null) return;
            int radius = Math.max(1, context.getInt("radius", 1));
            mineRadius(rm, player, loc.getBlock(), radius);
        });
    }

    /**
     * Mines up to {@code count} adjacent blocks (26-neighbourhood around the origin) whose
     * material matches a resource-block config in the origin's zone. Each candidate block goes
     * through the normal {@link ResourceManager#handleBlockBreak} pipeline, so required-power
     * and Mining Fortune both still apply per block — a block the player lacks power for is
     * silently skipped rather than erroring.
     */
    public static void mineRadius(ResourceManager resourceManager, Player player, Block origin, int count) {
        if (count <= 0) return;
        var originConfig = resourceManager.getResourceConfigAt(origin.getLocation());
        if (originConfig == null) return;
        var originMaterial = origin.getType();

        int mined = 0;
        for (int dx = -1; dx <= 1 && mined < count; dx++) {
            for (int dy = -1; dy <= 1 && mined < count; dy++) {
                for (int dz = -1; dz <= 1 && mined < count; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block candidate = origin.getRelative(dx, dy, dz);
                    if (candidate.getType() != originMaterial) continue;

                    ZoneResourceConfig candidateConfig = resourceManager.getResourceConfigAt(candidate.getLocation());
                    if (candidateConfig == null) continue;

                    ResourceManager.BreakResult result = resourceManager.handleBlockBreak(player, candidate);
                    if (result == ResourceManager.BreakResult.HANDLED) mined++;
                }
            }
        }
    }
}
