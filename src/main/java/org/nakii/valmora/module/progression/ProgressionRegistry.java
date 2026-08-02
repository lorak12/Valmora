package org.nakii.valmora.module.progression;

import org.nakii.valmora.api.registry.SimpleRegistry;

import java.util.Optional;

public class ProgressionRegistry extends SimpleRegistry<ProgressionTreeDefinition> {

    public void registerTree(ProgressionTreeDefinition definition) {
        register(definition.getId(), definition);
    }

    public Optional<ProgressionTreeDefinition> getTree(String id) {
        return get(id);
    }
}
