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
    private TrampleListener trampleListener;
    private ItemPipelineLoader pipelineLoader;

    @Override
    public void onEnable() {
        plugin.getLogger().info("Starting Ability Module...");
        registerMechanics();
        this.abilityListener = new AbilityListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(abilityListener, plugin);
        this.abilityTriggerListener = new AbilityTriggerListener();
        plugin.getServer().getPluginManager().registerEvents(abilityTriggerListener, plugin);
        this.trampleListener = new TrampleListener();
        plugin.getServer().getPluginManager().registerEvents(trampleListener, plugin);

        // Item ability pipeline (docs/VALMORA_DOCUMENTATION.md §39) — depends on scriptModule,
        // which registers/enables before this module (see module order in Valmora.onEnable()).
        if (plugin.getScriptModule() != null) {
            this.pipelineLoader = new ItemPipelineLoader(plugin, plugin.getScriptModule());
            pipelineLoader.load();
        }
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
        if (trampleListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(trampleListener);
        }
        if (plugin.getScriptModule() != null) {
            plugin.getScriptModule().getHookBus().clearYamlStages(ItemPipelineLoader.POINT_PREFIX);
        }
        pipelineLoader = null;
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
        mechanicRegistry.registerMechanic(new CancelTrampleMechanic());
    }
    
    public MechanicRegistry getMechanicRegistry() {
        return mechanicRegistry;
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down Ability System...");
        mechanicRegistry.clear();
    }
}
