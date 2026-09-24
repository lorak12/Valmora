package org.nakii.valmora.module.mob;

import org.nakii.valmora.api.registry.SimpleRegistry;

import java.util.Optional;
import java.util.Set;

public class MobRegistry extends SimpleRegistry<MobDefinition> {

    public MobRegistry() {
        super(org.nakii.valmora.infrastructure.versioning.IdAliases.MOBS);
    }

    public void registerMob(MobDefinition definition) {
        register(definition.getId(), definition);
    }

    public Optional<MobDefinition> getMob(String id) {
        return get(id);
    }

    public int getMobCount(){
        return size();
    }

    public Set<String> getAllMobIds() {
        return getKeys();
    }
}
