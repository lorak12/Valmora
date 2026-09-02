package org.nakii.valmora.module.mob;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.nakii.valmora.api.registry.SimpleRegistry;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MobRegistry extends SimpleRegistry<MobDefinition> {

    // VANILLA_CONTROL_AUDIT.md §17 — one designated "vanilla-default" MobDefinition per EntityType,
    // used by VanillaSpawnUpgradeListener to upgrade natural/spawner/egg spawns of that type.
    private final Map<EntityType, MobDefinition> vanillaDefaults = new ConcurrentHashMap<>();

    public MobRegistry() {}

    public void registerMob(MobDefinition definition) {
        register(definition.getId(), definition);
        if (definition.isVanillaDefault()) {
            MobDefinition existing = vanillaDefaults.putIfAbsent(definition.getEntityType(), definition);
            if (existing != null && existing != definition) {
                Bukkit.getLogger().warning("[Valmora] [Mob] Both '" + existing.getId() + "' and '" + definition.getId()
                        + "' are marked natural-spawn.vanilla-default for entity type " + definition.getEntityType()
                        + " — keeping '" + existing.getId() + "'.");
            }
        }
    }

    public Optional<MobDefinition> getMob(String id) {
        return get(id);
    }

    public Optional<MobDefinition> getVanillaDefault(EntityType type) {
        return Optional.ofNullable(vanillaDefaults.get(type));
    }

    public int getMobCount(){
        return size();
    }

    public Set<String> getAllMobIds() {
        return getKeys();
    }

    @Override
    public synchronized void clear() {
        super.clear();
        vanillaDefaults.clear();
    }
}
