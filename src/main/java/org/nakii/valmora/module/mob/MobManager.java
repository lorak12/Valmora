package org.nakii.valmora.module.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.util.Keys;

public class MobManager implements ReloadableModule {

    private final Valmora plugin;
    private final MobRegistry mobRegistry;
    private final MobFactory mobFactory;
    private final MobLoader mobLoader;
    private final MobDeathListener deathListener;
    private final BossController bossController;
    private final EntityCategoryRegistry entityCategoryRegistry;
    private MobPipelineLoader pipelineLoader;
    private org.bukkit.scheduler.BukkitTask aiTask;
    private org.bukkit.scheduler.BukkitTask naturalSpawnTask;

    public MobManager(Valmora plugin) {
        this.plugin = plugin;
        this.bossController = new BossController(plugin);
        this.mobFactory = new MobFactory(plugin, bossController);
        this.mobRegistry = new MobRegistry();
        this.mobLoader = new MobLoader(plugin, mobRegistry);
        this.deathListener = new MobDeathListener(plugin);
        this.entityCategoryRegistry = new EntityCategoryRegistry();
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Starting Mob Module...");
        Bukkit.getPluginManager().registerEvents(deathListener, plugin);
        MobCategoryLoader.load(plugin);
        entityCategoryRegistry.load(plugin);
        mobLoader.loadMobs();
        bossController.start();

        // Mob ability pipeline (docs/VALMORA_DOCUMENTATION.md §39) — depends on scriptModule,
        // which registers/enables before this module (see module order in Valmora.onEnable()).
        if (plugin.getScriptModule() != null) {
            this.pipelineLoader = new MobPipelineLoader(plugin, plugin.getScriptModule());
            pipelineLoader.load();
        }

        // Basic AI (leash range) and ambient natural spawning — see MobAiTask/NaturalSpawnTask.
        if (aiTask != null) aiTask.cancel();
        aiTask = Bukkit.getScheduler().runTaskTimer(plugin, new MobAiTask(plugin, mobRegistry), 40L, 40L);
        if (naturalSpawnTask != null) naturalSpawnTask.cancel();
        naturalSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, new NaturalSpawnTask(plugin, this, mobRegistry), 200L, 200L);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Stopping Mob Module...");
        org.bukkit.event.HandlerList.unregisterAll(deathListener);
        bossController.stop();
        if (aiTask != null) { aiTask.cancel(); aiTask = null; }
        if (naturalSpawnTask != null) { naturalSpawnTask.cancel(); naturalSpawnTask = null; }
        mobRegistry.clear();
        entityCategoryRegistry.clear();
        if (plugin.getScriptModule() != null) {
            plugin.getScriptModule().getHookBus().clearYamlStages(MobPipelineLoader.POINT_PREFIX);
        }
        pipelineLoader = null;
    }

    @Override
    public String getId() {
        return "mobs";
    }

    @Override
    public String getName() {
        return "Mob Engine";
    }

    public LivingEntity spawnMob(MobDefinition definition, Location location) {
        return mobFactory.spawnMob(definition, location);
    }

    public void updateVisuals(LivingEntity entity) {
        mobFactory.applyVisuals(entity, getMobDefinition(entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING)));
    }

    public MobDefinition getMobDefinition(String id) {
        if (id == null) return null;
        return mobRegistry.getMob(id).orElse(null);
    }

    public MobRegistry getMobRegistry() {
        return mobRegistry;
    }

    public MobFactory getMobFactory() {
        return mobFactory;
    }

    public MobLoader getMobLoader() {
        return mobLoader;
    }

    public BossController getBossController() {
        return bossController;
    }

    public EntityCategoryRegistry getEntityCategoryRegistry() {
        return entityCategoryRegistry;
    }
}
