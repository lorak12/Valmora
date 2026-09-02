package org.nakii.valmora.module.combat;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class CombatModule implements ReloadableModule {

    private final Valmora plugin;
    private final DamageIndicatorManager damageIndicatorManager;
    private final CombatListener combatListener;
    private final DamageTypeLoader damageTypeLoader;
    private DamageFormulaRegistry damageFormulaRegistry;
    private CombatPipelineLoader combatPipelineLoader;
    private BukkitTask regenTask;

    public CombatModule(Valmora plugin) {
        this.plugin = plugin;
        this.damageIndicatorManager = new DamageIndicatorManager(plugin);
        this.combatListener = new CombatListener(plugin);
        this.damageTypeLoader = new DamageTypeLoader(plugin);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Combat Module...");
        plugin.getServer().getPluginManager().registerEvents(combatListener, plugin);

        if (regenTask != null) {
            regenTask.cancel();
        }
        long regenIntervalTicks = plugin.getConfig().getLong("combat.regen-interval-ticks", 20L); // HC-020
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, new RegenTask(plugin), 0L, regenIntervalTicks);

        // Phase 2 of the generic-engine refactor — see docs/REFACTOR/PROGRESS.md.
        damageTypeLoader.load();
        this.damageFormulaRegistry = new DamageFormulaRegistry(plugin, plugin.getScriptModule().getExpressionParser());
        damageFormulaRegistry.load();

        // Combat pipeline hooks — see docs/COMBAT_PIPELINE_ANALYSIS.md. Registers onto the shared
        // HookBus (owned by ScriptModule, which loads before this module).
        this.combatPipelineLoader = new CombatPipelineLoader(plugin, plugin.getScriptModule());
        combatPipelineLoader.load();
        // Idempotent: registry key is the event name, so re-registering on reload just overwrites it.
        plugin.getScriptModule().registerEvent(new MultiplyDamageEventFactory());
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Combat Module...");
        org.bukkit.event.HandlerList.unregisterAll(combatListener);
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
        damageIndicatorManager.cleanup();
        if (damageFormulaRegistry != null) {
            damageFormulaRegistry.clear();
            damageFormulaRegistry = null;
        }
        if (plugin.getScriptModule() != null) {
            plugin.getScriptModule().getHookBus().clearYamlStages("combat:");
        }
        combatPipelineLoader = null;
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

    public DamageFormulaRegistry getDamageFormulaRegistry() {
        return damageFormulaRegistry;
    }
}
