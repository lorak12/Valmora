package org.nakii.valmora.module.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Keys;

/**
 * VANILLA_CONTROL_AUDIT.md §19 critical gap: "custom mobs silently revert to vanilla" — a
 * Valmora-defined zombie that drowns into a vanilla drowned, or a zombie villager that gets cured,
 * previously lost every bit of its Valmora identity (stats, equipment, resistances, boss abilities)
 * because {@code EntityTransformEvent} replaces the entity outright and nothing re-applied
 * {@link MobDefinition} data to the new one.
 *
 * <p>Re-applies the same definition (by id, via {@link MobFactory#applyData}/{@code applyEquipment}/
 * {@code applyVisuals}) to the transformed entity when the "from" side carries a
 * {@link Keys#MOB_ID_KEY}. Equipment is intentionally NOT reapplied when the underlying entity type
 * category changed drastically enough that vanilla itself already moved gear (e.g. mooshroom
 * shearing back to cow) — that's outside this listener's scope; it only guards the common
 * drown/burn/freeze/cure family where Bukkit hands back a like-for-like mob.
 */
public class MobConversionListener implements Listener {

    private final MobManager mobManager;

    public MobConversionListener(MobManager mobManager) {
        this.mobManager = mobManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof LivingEntity from)) return;
        if (!(event.getTransformedEntity() instanceof LivingEntity to)) return;

        String mobId = from.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobId == null) return;

        MobDefinition definition = mobManager.getMobDefinition(mobId);
        if (definition == null) return;

        if (mobManager.getBossController().isTracked(from.getUniqueId())) {
            mobManager.getBossController().unregister(from.getUniqueId());
        }

        // Re-apply on the next tick: the transformed entity isn't guaranteed to be fully ticking /
        // attribute-ready the instant this event fires (Paper §14.7 spawn-consumer note applies to
        // spawning, not transforms, so we defer instead of relying on a mid-transform entity state).
        Valmora plugin = Valmora.getInstance();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!to.isValid()) return;
            mobManager.getMobFactory().applyData(to, definition);
            mobManager.getMobFactory().applyEquipment(to, definition);
            mobManager.getMobFactory().applyVisuals(to, definition);
            if (definition.isBoss()) {
                mobManager.getBossController().register(to, definition);
            }
        });
    }
}
