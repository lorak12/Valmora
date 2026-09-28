package org.nakii.valmora.module.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Keys;
import org.bukkit.persistence.PersistentDataType;


public class MobDeathListener implements Listener {

    private final Valmora plugin;

    public MobDeathListener(Valmora plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String mobId = entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);

        if (mobId == null) {
            return;
        }

        MobManager mobManager = plugin.getMobManager();
        MobDefinition definition = mobManager.getMobDefinition(mobId);

        if (definition == null) {
            return;
        }

        // Rewards, loot and the combat:on_death anchor — mob_pipeline.yml's `death` flow.
        MobFlows.DeathState state = new MobFlows.DeathState(plugin, event, mobId, definition);
        DebugManager.log("mobs", "'" + mobId + "' (uuid=" + entity.getUniqueId() + ") died, killer="
                + (state.killer != null ? state.killer.getName() : "none"));
        MobFlows.DEATH.run(new SimpleExecutionContext(state.killer, entity, entity.getLocation(), null), state);
    }

    /** Cancels sunlight/ambient burning for mobs flagged {@code prevent-sun-burn}. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobCombust(EntityCombustEvent event) {
        // Only suppress ambient (sunlight) combustion, not fire from blocks/entities (lava, flint & steel, etc.)
        if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String mobId = entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobId == null) {
            return;
        }
        MobDefinition definition = plugin.getMobManager().getMobDefinition(mobId);
        if (definition != null && definition.isPreventSunBurn()) {
            event.setCancelled(true);
        }
    }
}
