package org.nakii.valmora.module.mob;

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

    public MobManager(Valmora plugin) {
        this.plugin = plugin;
        this.mobFactory = new MobFactory(plugin);
        this.mobRegistry = new MobRegistry();
        this.mobLoader = new MobLoader(plugin, mobRegistry);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Starting Mob Module...");
        mobLoader.loadMobs();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Stopping Mob Module...");
        mobRegistry.clear();
    }

    @Override
    public String getId() {
        return "mobs";
    }

    @Override
    public String getName() {
        return "Mob Engine";
    }

    public void spawnMob(MobDefinition definition, Location location) {
        mobFactory.spawnMob(definition, location);
    }

    public void updateVisuals(LivingEntity entity) {
        mobFactory.applyVisuals(entity, getMobDefinition(entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING)));
    }

    public MobDefinition getMobDefinition(String id) {
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


}
