package org.nakii.valmora.module.profile;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;

public class PlayerState {
    private double currentHealth;
    private double currentMana;
    private long lastCombatTime = 0;
    private String currentZoneId = null;

    public PlayerState() {
        // Use defaults from StatRegistry; fall back to 100 if registry not yet loaded
        try {
            ValmoraAPI api = ValmoraAPI.getInstance();
            SystemStats sys = api.getSystemStats();
            if (sys != null) {
                var registry = api.getStatRegistry();
                var healthDef = registry.get(sys.getHealth());
                var manaDef = registry.get(sys.getMana());
                this.currentHealth = healthDef.map(d -> d.getDefaultValue()).orElse(100.0);
                this.currentMana = manaDef.map(d -> d.getDefaultValue()).orElse(100.0);
            } else {
                this.currentHealth = 100.0;
                this.currentMana = 100.0;
            }
        } catch (Exception e) {
            this.currentHealth = 100.0;
            this.currentMana = 100.0;
        }
    }

    public String getCurrentZoneId() { return currentZoneId; }
    public void setCurrentZoneId(String id) { this.currentZoneId = id; }

    public long getLastCombatTime() { return lastCombatTime; }
    public void setInCombat() { this.lastCombatTime = System.currentTimeMillis(); }
    public boolean isInCombat() {
        long windowMs = 3000;
        org.nakii.valmora.Valmora plugin = org.nakii.valmora.Valmora.getInstance();
        if (plugin != null) {
            windowMs = plugin.getConfig().getLong("combat.combat-window-ms", 3000);
        }
        return (System.currentTimeMillis() - lastCombatTime) < windowMs;
    }

    public double getCurrentHealth() { return currentHealth; }
    public double getCurrentMana() { return currentMana; }

    public void heal(double amount, StatManager stats) {
        String healthId = ValmoraAPI.getInstance().getSystemStats().getHealth();
        double maxHealth = stats.getStat(healthId);
        this.currentHealth = Math.min(maxHealth, this.currentHealth + amount);
    }

    public void reduceHealth(double amount) {
        this.currentHealth = Math.max(0, this.currentHealth - amount);
    }

    public void restoreMana(double amount, StatManager stats) {
        String manaId = ValmoraAPI.getInstance().getSystemStats().getMana();
        double maxMana = stats.getStat(manaId);
        this.currentMana = Math.min(maxMana, this.currentMana + amount);
    }

    public void reduceMana(double amount) {
        this.currentMana = Math.max(0, this.currentMana - amount);
    }

    public void capToMax(StatManager stats) {
        SystemStats sys = ValmoraAPI.getInstance().getSystemStats();
        double maxHealth = stats.getStat(sys.getHealth());
        if (this.currentHealth > maxHealth) this.currentHealth = maxHealth;
        double maxMana = stats.getStat(sys.getMana());
        if (this.currentMana > maxMana) this.currentMana = maxMana;
    }

    /**
     * Serialized shape for the {@code player_state} DB column. Was a bare {@code double[]}
     * (health, mana only) — extended to also persist the combat timer and current zone, which
     * were previously silently reset on every {@code /valmora reload}/restart despite not being
     * marked {@code transient} for any principled reason (see docs/IMPLEMENTATION_BACKLOG.md).
     */
    public static class SaveData {
        public double health;
        public double mana;
        public long lastCombatTime;
        public String zoneId;
    }

    public SaveData getSaveData() {
        SaveData data = new SaveData();
        data.health = currentHealth;
        data.mana = currentMana;
        data.lastCombatTime = lastCombatTime;
        data.zoneId = currentZoneId;
        return data;
    }

    public void loadData(SaveData data) {
        if (data == null) return;
        this.currentHealth = data.health;
        this.currentMana = data.mana;
        this.lastCombatTime = data.lastCombatTime;
        this.currentZoneId = data.zoneId;
    }

    /** Back-compat for the pre-extension save format (a bare [health, mana] array). */
    public void loadData(double[] data) {
        if (data != null && data.length >= 2) {
            this.currentHealth = data[0];
            this.currentMana = data[1];
        }
    }
}
