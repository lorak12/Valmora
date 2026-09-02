package org.nakii.valmora.module.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Keys;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

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

        // Fire ON_DEATH boss abilities and stop tracking this entity
        if (mobManager.getBossController().isTracked(entity.getUniqueId())) {
            mobManager.getBossController().onDeath(entity);
        }

        Player killer = entity.getKiller();
        double luck = 0;

        if (killer != null) {
            ValmoraPlayer vp = plugin.getPlayerManager().getSession(killer.getUniqueId());
            if (vp != null) {
                ValmoraProfile profile = vp.getActiveProfile();
                if (profile != null) {
                    StatManager statManager = profile.getStatManager();
                    // pet_luck (stats/core.yml) stacks additively on top of the general luck stat
                    // for loot roll purposes — previously defined but never consumed anywhere.
                    luck = statManager.getStat(plugin.getStatModule().getSystemStats().getLuck())
                            + statManager.getStat("pet_luck");

                    int xpReward = definition.getXpReward();
                    profile.getSkillManager().addXp("combat", (double) xpReward, killer);

                    int goldReward = definition.getGoldReward();
                    if (goldReward > 0) {
                        plugin.getEconomy().addCoins(killer, goldReward);
                    }
                }
            }
        }

        DebugManager.log("mobs", "'" + mobId + "' (uuid=" + entity.getUniqueId() + ") died, killer="
                + (killer != null ? killer.getName() : "none") + " luck=" + luck);

        LootTable lootTable = definition.getLootTable();
        if (lootTable != null) {
            List<LootEntry> entries = lootTable.getEntries();
            for (LootEntry entry : entries) {
                double effectiveChance = entry.isLuckAffected() ? entry.getEffectiveChance(luck) : entry.getChance();

                if (Math.random() < effectiveChance) {
                    ItemStack drop = entry.createDroppedItem();
                    event.getDrops().add(drop);
                    DebugManager.log("mobs", "'" + mobId + "' loot roll: " + drop.getType()
                            + " x" + drop.getAmount() + " (chance=" + effectiveChance + ")");
                }
            }
        }

        // Combat pipeline (docs/COMBAT_PIPELINE_ANALYSIS.md) — same zero-cost-when-unused guard as
        // CombatListener: skip building a context entirely if nothing is registered at this point.
        HookBus bus = ValmoraAPI.getInstance().getHookBus();
        if (bus != null && bus.hasStages("combat:on_death") && killer != null) {
            var ctx = new SimpleExecutionContext(killer, entity, entity.getLocation(), null);
            ctx.set("mob:id", mobId);
            ctx.set("mob:level", definition.getLevel());
            bus.runPoint("combat:on_death", ctx);
        }
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
