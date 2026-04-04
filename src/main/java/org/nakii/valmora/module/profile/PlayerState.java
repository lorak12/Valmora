package org.nakii.valmora.module.profile;

import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;

public class PlayerState {
    private double currentHealth;
    private double currentMana;
    private transient long lastCombatTime = 0;

    public PlayerState() {
        this.currentHealth = Stat.HEALTH.getDefaultValue();
        this.currentMana = Stat.MANA.getDefaultValue();
    }

    public long getLastCombatTime() { return lastCombatTime; }
    public void setInCombat() { this.lastCombatTime = System.currentTimeMillis(); }
    public boolean isInCombat() {
        // Player is considered "In Combat" if they took damage in the last 3 seconds
        return (System.currentTimeMillis() - lastCombatTime) < 3000; 
    }

    public double getCurrentHealth() {
        return currentHealth;
    }

    public double getCurrentMana() {
        return currentMana;
    }

    public void heal(double amount, StatManager stats) {
        double maxHealth = stats.getStat(Stat.HEALTH);
        this.currentHealth = Math.min(maxHealth, this.currentHealth + amount);
    }

    public void reduceHealth(double amount){
        this.currentHealth = Math.max(0, this.currentHealth - amount);
    }

    public void restoreMana(double amount, StatManager stats){
        double maxMana = stats.getStat(Stat.MANA);
        this.currentMana = Math.min(maxMana, this.currentMana + amount);
    }

    public void reduceMana(double amount){
        this.currentMana = Math.max(0, this.currentMana - amount);
    }

    public void capToMax(StatManager stats) {
        double maxHealth = stats.getStat(Stat.HEALTH);
        if (this.currentHealth > maxHealth) {
            this.currentHealth = maxHealth;
        }
        double maxMana = stats.getStat(Stat.MANA);
        if (this.currentMana > maxMana) {
            this.currentMana = maxMana;
        }
    }

    public double[] getSaveData() {
        return new double[]{currentHealth, currentMana};
    }

    public void loadData(double[] data) {
        if (data != null && data.length >= 2) {
            this.currentHealth = data[0];
            this.currentMana = data[1];
        }
    }
}
