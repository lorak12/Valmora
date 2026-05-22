package org.nakii.valmora.module.quest;

import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.module.npc.event.NpcInteractEvent;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.skill.SkillLevelUpEvent;
import org.nakii.valmora.module.skill.SkillXpGainEvent;
import org.nakii.valmora.module.zone.event.ZoneEnterEvent;
import org.nakii.valmora.util.Keys;

public class QuestListener implements Listener {

    private final QuestManager questManager;

    public QuestListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        questManager.startAutoOnceObjectivesForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() == null) return;
        String mobId = entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        String target = mobId != null ? mobId : entity.getType().name();
        questManager.progressObjective(entity.getKiller(), QuestObjectiveType.KILL, target, 1);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String itemId = event.getItem().getItemStack().getPersistentDataContainer()
                .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        String target = itemId != null ? itemId : event.getItem().getItemStack().getType().name();
        questManager.progressObjective(player, QuestObjectiveType.COLLECT, target,
                event.getItem().getItemStack().getAmount());
    }

    @EventHandler
    public void onZoneEnter(ZoneEnterEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.REACH_ZONE,
                event.getZone().getId(), 1);
    }

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.TALK_TO_NPC,
                event.getNpc().getId(), 1);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.DIE, "die", 1);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.BLOCK_BREAK,
                event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.BLOCK_PLACE,
                event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onSmelt(FurnaceSmeltEvent event) {
        String result = event.getResult().getType().name();
        // FurnaceSmeltEvent doesn't directly expose the player; skip for now
        // Full attribution requires tracking who placed the item via InventoryClickEvent
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        String itemType = event.getCaught() != null ? event.getCaught().getType().name() : "FISH";
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.FISH, itemType, 1);
    }

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.SHEAR,
                event.getEntity().getType().name(), 1);
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        questManager.progressObjective(player, QuestObjectiveType.BREED,
                event.getEntityType().name(), 1);
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        questManager.progressObjective(player, QuestObjectiveType.TAME,
                event.getEntity().getType().name(), 1);
    }

    @EventHandler
    public void onDrinkPotion(PlayerItemConsumeEvent event) {
        String mat = event.getItem().getType().name();
        if (!mat.contains("POTION")) return;
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.DRINK_POTION, mat, 1);
    }

    @EventHandler
    public void onLoginQuest(PlayerJoinEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.LOGIN, "login", 1);
        checkStatReachObjectives(event.getPlayer());
    }

    @EventHandler
    public void onSkillLevelUp(SkillLevelUpEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.LEVEL_SKILL,
                event.getSkill().getId(), 1);
    }

    @EventHandler
    public void onXpGain(SkillXpGainEvent event) {
        questManager.progressObjective(event.getPlayer(), QuestObjectiveType.EXP_GAIN,
                event.getSkill().getId(), (int) Math.ceil(event.getXp()));
    }

    private void checkStatReachObjectives(Player player) {
        var api = org.nakii.valmora.api.ValmoraAPI.getInstance();
        if (api == null) return;
        var pm = api.getPlayerManager();
        if (pm == null) return;
        ValmoraPlayer vp = pm.getSession(player.getUniqueId());
        if (vp == null || vp.getActiveProfile() == null) return;
        ValmoraProfile profile = vp.getActiveProfile();

        questManager.getRegistry().values().forEach(quest -> {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) return;
            quest.getObjectives().stream()
                    .filter(o -> o.getType() == QuestObjectiveType.STAT_REACH)
                    .forEach(o -> {
                        double statVal = profile.getStatManager().getStat(o.getTarget());
                        if (statVal >= o.getRequired()) {
                            questManager.progressObjective(player, QuestObjectiveType.STAT_REACH,
                                    o.getTarget(), o.getRequired());
                        }
                    });
        });
    }
}
