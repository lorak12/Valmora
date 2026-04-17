package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.module.skill.Skill;

public class GiveXpEventFactory implements EventFactory {

    private final Valmora plugin;

    public GiveXpEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "givexp";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return context -> {};
        // Syntax: givexp player <SKILL> <amount>
        if (!args[0].equalsIgnoreCase("player")) return context -> {};
        
        try {
            Skill skill = Skill.valueOf(args[1].toUpperCase());
            double amount = Double.parseDouble(args[2]);
            
            return context -> context.getPlayerCaster().ifPresent(player -> 
                plugin.getPlayerManager().getSession(player.getUniqueId())
                    .getActiveProfile().getSkillManager().addXp(skill, amount, player));
        } catch (Exception e) {
            return context -> {};
        }
    }
}
