package org.nakii.valmora.module.death;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.zone.ZoneManager;

import java.util.List;

/**
 * VANILLA_CONTROL_AUDIT.md §9 — respawn-anchor/bed-explodes-in-the-wrong-dimension rows. This
 * Paper/Bukkit version (1.21.11) has no dedicated {@code RespawnAnchorExplodeEvent}/
 * {@code BedExplodeEvent} — both fire as a plain {@link BlockExplodeEvent} whose source block is a
 * {@code RESPAWN_ANCHOR} or a bed ({@link Tag#BEDS}), so this listens there and filters by block
 * type rather than assuming those dedicated event classes exist.
 *
 * <p>Deliberately narrow scope: filters block destruction down to zones that actually allow block
 * breaking (reusing {@code ZoneFlags.blockBreaking}, the same flag {@code ZoneListener} already
 * enforces for normal mining), rather than reimplementing the audit's separate, much larger §4
 * general explosion-control system — a TNT/creeper/etc. explosion at the same location is
 * deliberately left alone here. Player damage from the explosion is untouched:
 * {@code EntityDamageEvent} cause {@code BLOCK_EXPLOSION} already flows through
 * {@code CombatListener.onEntityDamage} -&gt; {@code mapCauseToType} -&gt; {@code DamageType.EXPLOSION}
 * unchanged.
 */
public class RespawnAnchorListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Material sourceType = event.getBlock().getType();
        if (sourceType != Material.RESPAWN_ANCHOR && !Tag.BEDS.isTagged(sourceType)) return;
        filterProtectedBlocks(event.blockList());
    }

    static void filterProtectedBlocks(List<Block> blocks) {
        ZoneManager zoneManager = ValmoraAPI.getInstance().getZoneManager();
        if (zoneManager == null) return;
        blocks.removeIf(block -> zoneManager.getZoneAt(block.getLocation())
                .map(zone -> !zone.getFlags().blockBreaking())
                .orElse(false));
    }
}
