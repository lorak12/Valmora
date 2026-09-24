package org.nakii.valmora.module.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Keys;

/**
 * VANILLA_CONTROL_AUDIT.md §17 critical/high gap: "Spawn egg / command / plugin spawning of
 * Valmora mobs" — vanilla natural spawns, monster-spawner spawns and spawn-egg uses produced plain
 * vanilla entities with none of a {@link MobDefinition}'s stats/equipment/abilities, since only
 * {@code /mob spawn} and the zone-driven {@link NaturalSpawnTask} ever called
 * {@link MobFactory#spawnMob}.
 *
 * <p>Upgrades any freshly-spawned entity that does NOT already carry {@link Keys#MOB_ID_KEY} (i.e.
 * did not come from Valmora's own spawn path — {@code MobFactory.spawnMob} tags the entity inside
 * the spawn consumer, which runs before this event fires) to the entity type's designated
 * {@code natural-spawn.vanilla-default} definition, if one is registered. Opt-in per entity type —
 * a server with no `vanilla-default: true` mob for a given type sees no change in behavior for it.
 */
public class VanillaSpawnUpgradeListener implements Listener {

    private final MobManager mobManager;

    public VanillaSpawnUpgradeListener(MobManager mobManager) {
        this.mobManager = mobManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        Valmora plugin = Valmora.getInstance();
        if (plugin != null && !plugin.getConfig().getBoolean("mobs.natural-spawn.vanilla-upgrade-enabled", true)) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(Keys.MOB_ID_KEY, PersistentDataType.STRING)) {
            return; // already a Valmora-spawned mob
        }

        MobDefinition definition = mobManager.getMobRegistry().getVanillaDefault(event.getEntityType()).orElse(null);
        if (definition == null) return;

        var factory = mobManager.getMobFactory();
        factory.applyData(entity, definition);
        factory.applyEquipment(entity, definition);
        factory.applyVisuals(entity, definition);
        if (definition.isBoss()) {
            mobManager.getBossController().register(entity, definition);
        }
    }
}
