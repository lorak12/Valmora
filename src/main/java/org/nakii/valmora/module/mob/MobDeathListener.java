package org.nakii.valmora.module.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.skill.Skill;
import org.nakii.valmora.module.skill.SkillXpGainEvent;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;
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

        Player killer = entity.getKiller();
        double luck = 0;

        if (killer != null) {
            ValmoraPlayer vp = plugin.getPlayerManager().getSession(killer.getUniqueId());
            if (vp != null) {
                ValmoraProfile profile = vp.getActiveProfile();
                if (profile != null) {
                    StatManager statManager = profile.getStatManager();
                    luck = statManager.getStat(Stat.LUCK);

                    int xpReward = definition.getXpReward();
                    SkillXpGainEvent xpEvent = new SkillXpGainEvent(killer, Skill.COMBAT, xpReward);
                    xpEvent.callEvent();

                    int goldReward = definition.getGoldReward();
                    if (goldReward > 0) {
                        // TODO: Integrate with Economy system
                        // Example: plugin.getEconomy().depositPlayer(killer, goldReward);
                        plugin.getLogger().info("Player " + killer.getName() + " earned " + goldReward + " gold (economy integration pending)");
                    }
                }
            }
        }

        LootTable lootTable = definition.getLootTable();
        if (lootTable != null) {
            List<LootEntry> entries = lootTable.getEntries();
            for (LootEntry entry : entries) {
                double effectiveChance = entry.isLuckAffected() ? entry.getEffectiveChance(luck) : entry.getChance();

                if (Math.random() < effectiveChance) {
                    ItemStack drop = entry.createDroppedItem();
                    event.getDrops().add(drop);
                }
            }
        }
    }
}
