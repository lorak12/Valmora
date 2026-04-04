package org.nakii.valmora.module.combat;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class CombatModule implements ReloadableModule {

    private final Valmora plugin;
    private final DamageIndicatorManager damageIndicatorManager;
    private final CombatListener combatListener;

    public CombatModule(Valmora plugin) {
        this.plugin = plugin;
        this.damageIndicatorManager = new DamageIndicatorManager(plugin);
        this.combatListener = new CombatListener(plugin);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Combat Module...");
        plugin.getServer().getPluginManager().registerEvents(combatListener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Combat Module...");
        org.bukkit.event.HandlerList.unregisterAll(combatListener);
        damageIndicatorManager.cleanup();
    }

    @Override
    public String getId() {
        return "combat";
    }

    @Override
    public String getName() {
        return "Combat Engine";
    }

    public DamageIndicatorManager getDamageIndicatorManager() {
        return damageIndicatorManager;
    }
}
