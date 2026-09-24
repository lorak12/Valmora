package org.nakii.valmora.module.mob;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.event.ValmoraReloadedEvent;
import org.nakii.valmora.util.Keys;

/**
 * Keeps live custom mobs in step with their YAML templates. When a mob is loaded (its chunk
 * loads, or the server starts) and after every reload:
 * <ul>
 *   <li>a changed template is re-applied ({@link MobFactory#reapplyTemplate});</li>
 *   <li>a mob whose id was renamed ({@code previous-ids}) is re-tagged with the current id;</li>
 *   <li>a boss gets its controller back ({@link BossController#attach});</li>
 *   <li>a mob whose template was deleted follows {@code mobs.orphan-policy} ({@code keep}, the
 *       default, leaves it as a plain mob; {@code remove} despawns it).</li>
 * </ul>
 * Bosses in unloading chunks are detached, so the controller never holds a dead entity.
 */
public class MobLifecycleListener implements Listener {

    private final Valmora plugin;
    private final MobManager mobManager;

    public MobLifecycleListener(Valmora plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) reconcile(living);
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            mobManager.getBossController().unregister(entity.getUniqueId());
        }
    }

    @EventHandler
    public void onReloaded(ValmoraReloadedEvent event) {
        reconcileLoaded();
    }

    /** Reconciles every custom mob currently loaded. */
    public void reconcileLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                reconcile(entity);
            }
        }
    }

    private void reconcile(LivingEntity entity) {
        var pdc = entity.getPersistentDataContainer();
        String storedId = pdc.get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (storedId == null || !entity.isValid()) return;

        MobDefinition def = mobManager.getMobDefinition(storedId);
        if (def == null) {
            if ("remove".equalsIgnoreCase(plugin.getConfig().getString("mobs.orphan-policy", "keep"))) {
                entity.remove();
            }
            return;
        }
        if (!def.getId().equalsIgnoreCase(storedId)) {
            pdc.set(Keys.MOB_ID_KEY, PersistentDataType.STRING, def.getId());
        }
        if (!MobFactory.fingerprint(def).equals(pdc.get(Keys.MOB_TEMPLATE_HASH_KEY, PersistentDataType.STRING))) {
            mobManager.getMobFactory().reapplyTemplate(entity, def);
            mobManager.updateVisuals(entity);
        }
        if (def.isBoss()) {
            mobManager.getBossController().attach(entity, def);
        }
    }
}
