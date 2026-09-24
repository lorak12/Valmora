package org.nakii.valmora.module.blockloot;

import org.bukkit.Material;

import java.util.List;

/**
 * A global, single-stage loot override for one vanilla block type — "every {@code material} block
 * anywhere drops this table instead of its vanilla loot." Deliberately has no stages/regen/
 * required-power, unlike the zone-scoped {@code module.zone.ZoneResourceConfig}: this isn't a
 * mineable node, the block just breaks once, like vanilla, and drops the configured table.
 */
public record BlockLootConfig(Material material, List<BlockLootDrop> drops) {
}
