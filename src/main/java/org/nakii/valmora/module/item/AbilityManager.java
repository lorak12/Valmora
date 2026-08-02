package org.nakii.valmora.module.item;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.item.impl.*;

public class AbilityManager implements ReloadableModule {
    public final Valmora plugin;
    public final MechanicRegistry mechanicRegistry;

    public AbilityManager(Valmora plugin) {
        this.plugin = plugin;
        this.mechanicRegistry = new MechanicRegistry();
    }

    private AbilityListener abilityListener;
    private AbilityTriggerListener abilityTriggerListener;

    @Override
    public void onEnable() {
        plugin.getLogger().info("Starting Ability Module...");
        registerMechanics();
        this.abilityListener = new AbilityListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(abilityListener, plugin);
        this.abilityTriggerListener = new AbilityTriggerListener();
        plugin.getServer().getPluginManager().registerEvents(abilityTriggerListener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Stopping Ability Module...");
        mechanicRegistry.clear();
        if (abilityListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(abilityListener);
        }
        if (abilityTriggerListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(abilityTriggerListener);
        }
    }

    @Override
    public String getId() {
        return "abilities";
    }

    public void initialize() {
        onEnable();
    }

    private void registerMechanics() {
        mechanicRegistry.registerMechanic(new DamageMechanic());
        mechanicRegistry.registerMechanic(new HealMechanic());
        mechanicRegistry.registerMechanic(new ApplyEffectMechanic());
        mechanicRegistry.registerMechanic(new ScriptMechanic());
        mechanicRegistry.registerMechanic(new ModifyStatMechanic());
        mechanicRegistry.registerMechanic(new TeleportMechanic());
        mechanicRegistry.registerMechanic(new PushEntitiesMechanic());
        mechanicRegistry.registerMechanic(new PullEntitiesMechanic());
        mechanicRegistry.registerMechanic(new GiveCoinsMechanic());
        mechanicRegistry.registerMechanic(new TakeCoinsMechanic());
        mechanicRegistry.registerMechanic(new IgniteMechanic());
        mechanicRegistry.registerMechanic(new LaunchPlayerMechanic());
        mechanicRegistry.registerMechanic(new LaunchProjectileMechanic());
        mechanicRegistry.registerMechanic(new AoeMineMechanic());
    }
    
    public MechanicRegistry getMechanicRegistry() {
        return mechanicRegistry;
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down Ability System...");
        mechanicRegistry.clear();
    }
}
