package org.nakii.valmora.combat;

import org.nakii.valmora.Valmora;

public class DamageApplier {
    DamageResult damageResult;
    Valmora plugin;

    public DamageApplier(DamageResult damageResult, Valmora plugin) {
        this.damageResult = damageResult;
        this.plugin = plugin;
    }

    public void applyDamage() {
        // to ensure we bypass enchantments and other aspects of the vanilla game
        damageResult.getVictim().setHealth(Math.max(0, damageResult.getVictim().getHealth() - damageResult.getFinalDamage()));
        
        // update visuals
        plugin.getMobManager().updateVisuals(damageResult.getVictim());
        
        // Simulate vanilla invulnerability frames to prevent rapid overlapping DoT triggers
        damageResult.getVictim().setNoDamageTicks(20);
    }
}
