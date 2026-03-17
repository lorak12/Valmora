package org.nakii.valmora.mob;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Keys;
import org.nakii.valmora.Valmora;


public class MobManager {

    private final Valmora plugin;
    private final MobRegistry mobRegistry;
    private final MobFactory mobFactory;
    private final MobLoader mobLoader;

    public MobManager(Valmora plugin) {
        this.plugin = plugin;
        this.mobFactory = new MobFactory(plugin);
        this.mobRegistry = MobRegistry.getInstance(mobFactory);
        this.mobLoader = new MobLoader(plugin, mobRegistry);

    }

    public void initialize(){
        plugin.getLogger().info("Initializing Mob System...");

        registerAllMobs();

        plugin.getLogger().info("Mob System initialized with " + mobRegistry.getMobCount() + " mobs");
    }

    private void registerAllMobs(){
        mobLoader.loadMobs();
    }

    public void reload(){
        mobRegistry.clear();
        registerAllMobs();
    }

    public void spawnMob(MobDefinition definition, Location location) {
        mobFactory.spawnMob(definition, location);
    }

    public void updateVisuals(LivingEntity entity) {
        mobFactory.applyVisuals(entity, getMobDefinition(entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING)));
    }

    public MobDefinition getMobDefinition(String id) {
        return mobRegistry.getMob(id);
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
