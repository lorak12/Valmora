package org.nakii.valmora.item.ability;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.item.ability.impl.*;

public class AbilityManager {
    public final Valmora plugin;
    public final MechanicRegistry mechanicRegistry;

    public AbilityManager(Valmora plugin) {
        this.plugin = plugin;
        this.mechanicRegistry = new MechanicRegistry();
    }

    public void initialize() {
        plugin.getLogger().info("Initializing Ability System...");

        registerMechanics();

        plugin.getLogger().info("Ability System initialized with " + mechanicRegistry.size() + " mechanics.");
    }

    private void registerMechanics() {
        mechanicRegistry.registerMechanic(new DamageMechanic());
        mechanicRegistry.registerMechanic(new HealMechanic());
        mechanicRegistry.registerMechanic(new ApplyEffectMechanic());
    }
    
    public MechanicRegistry getMechanicRegistry() {
        return mechanicRegistry;
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down Ability System...");
        mechanicRegistry.clear();
    }
}
