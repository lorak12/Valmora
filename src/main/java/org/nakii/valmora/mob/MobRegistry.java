package org.nakii.valmora.mob;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MobRegistry {
    
    private final Map<String, MobDefinition> registry = new HashMap<>();
    private final MobFactory factory;

    private static MobRegistry instance;

    private MobRegistry(MobFactory factory) {
        this.factory = factory;
    }

    public static MobRegistry getInstance(MobFactory factory) {
        if (instance == null) {
            synchronized (MobRegistry.class) {
                if (instance == null) {
                    instance = new MobRegistry(factory);
                }
            }
        }
        return instance;
    }

    public void registerMob(MobDefinition definition) {
        registry.put(definition.getId().toLowerCase(), definition);
    }

    public MobDefinition getMob(String id) {
        if(id == null) return null;
        return registry.get(id.toLowerCase());
    }


    public void clear() {
        registry.clear();
    }

    public int getMobCount(){
        return registry.size();
    }

    public Set<String> getAllMobIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }
}
