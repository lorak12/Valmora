package org.nakii.valmora.skill;

import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class SkillListener implements Listener {

    private final SkillManager skillManager;

    public SkillListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    public void onSkillXpGain(SkillXpGainEvent event){
        // call action bar manager to show xp gain
    }
    
    public void onSkillLevelUp(SkillLevelUpEvent event){
        // call action bar manager to show level up
    }

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
