package org.nakii.valmora.module.skill;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.nakii.valmora.Valmora;

public class SkillListener implements Listener {

    private final SkillManager skillManager;
    private final Valmora plugin;


    public SkillListener(SkillManager skillManager, Valmora plugin) {
        this.skillManager = skillManager;
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onSkillXpGain(SkillXpGainEvent event){
        String message = "<aqua>+<yellow>" + event.getXp() + " <aqua>" + event.getSkill().getName() + " XP";
        plugin.getUIManager().getActionBar().showTemporary(event.getPlayer(), message, 20);
    }
    
    @EventHandler
    public void onSkillLevelUp(SkillLevelUpEvent event){
        plugin.getUIManager().getChat().sendLevelUp(event.getPlayer(), event.getSkill().getName(), event.getNewLevel());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        String blockId = event.getBlock().getType().name();
        for (SkillDefinition skill : plugin.getSkillManager().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("BLOCK_BREAK", blockId);
            if (xp != null && xp > 0) {
                skillManager.addXp(skill.getId(), xp, event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        String mobId = event.getEntityType().name();
        for (SkillDefinition skill : plugin.getSkillManager().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("MOB_KILL", mobId);
            if (xp != null && xp > 0) {
                skillManager.addXp(skill.getId(), xp, event.getEntity().getKiller());
            }
        }
    }

    @EventHandler
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.getCaught() == null) return;

        String caughtId = "COD"; // Default
        if (event.getCaught() instanceof org.bukkit.entity.Item) {
            caughtId = ((org.bukkit.entity.Item) event.getCaught()).getItemStack().getType().name();
        }

        for (SkillDefinition skill : plugin.getSkillManager().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("FISHING", caughtId);
            if (xp != null && xp > 0) {
                skillManager.addXp(skill.getId(), xp, event.getPlayer());
            }
        }
    }
}

