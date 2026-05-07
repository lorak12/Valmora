package org.nakii.valmora.module.alchemy;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.alchemy.brewing.AlchemyMachineHandler;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffectLoader;
import org.nakii.valmora.module.alchemy.gui.AlchemyVariableProvider;

public class AlchemyModule implements ReloadableModule {

    private final Valmora plugin;
    private final AlchemyManager alchemyManager;

    private AlchemyListener listener;
    private int tickTaskId = -1;

    public AlchemyModule(Valmora plugin) {
        this.plugin = plugin;
        int maxEffects = plugin.getConfig().getInt("alchemy.max-active-effects", 10);
        this.alchemyManager = new AlchemyManager(maxEffects);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Initializing Alchemy System...");

        alchemyManager.clear();

        YamlLoader<AlchemyEffect> loader = new YamlLoader<>(plugin, "alchemy", "Alchemy Effect");
        loader.load(AlchemyEffectLoader.parser(), alchemyManager::registerEffect);

        plugin.getRecipeModule().getRecipeEngine()
                .registerHandler("alchemy", new AlchemyMachineHandler(plugin, alchemyManager));

        plugin.getScriptModule().registerProvider(new AlchemyVariableProvider());

        this.listener = new AlchemyListener(alchemyManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        int intervalTicks = plugin.getConfig().getInt("alchemy.tick-interval", 20);
        tickTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                alchemyManager.tick(player);
            }
        }, 20L, intervalTicks).getTaskId();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Alchemy System...");

        if (tickTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }

        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }

        alchemyManager.clear();
    }

    @Override
    public String getId() { return "alchemy"; }

    @Override
    public String getName() { return "Alchemy System"; }

    public AlchemyManager getAlchemyManager() { return alchemyManager; }
}
