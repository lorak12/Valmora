package org.nakii.valmora.module.skill;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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
        plugin.getUIManager().getChat().sendLevelUp(event.getPlayer(), event.getSkill(), event.getNewLevel());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event){
        if (event.getBlock().getType() == Material.STONE){
            skillManager.addXp(Skill.MINING, 1, event.getPlayer());
        }
        if(event.getBlock().getType() == Material.OAK_LOG){
            skillManager.addXp(Skill.FORAGING, 1, event.getPlayer());
        }
        if(event.getBlock().getType() == Material.WHEAT){
            skillManager.addXp(Skill.FARMING, 1, event.getPlayer());
        }
    }
}
