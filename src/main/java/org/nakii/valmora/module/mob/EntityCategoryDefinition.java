package org.nakii.valmora.module.mob;

import org.bukkit.entity.Entity;

import java.util.List;
import java.util.function.Predicate;

/**
 * A named entity category, matched by one or more OR'd rules pre-compiled at load time.
 * Originally built for slayer target-category matching (Phase 3.4 of the refactor — see
 * docs/REFACTOR/PROGRESS.md); relocated here as a general-purpose entity classifier that any
 * system (e.g. quest KILL objectives) can reuse.
 */
public class EntityCategoryDefinition {

    private final String id;
    private final List<Predicate<Entity>> rules;

    public EntityCategoryDefinition(String id, List<Predicate<Entity>> rules) {
        this.id = id;
        this.rules = rules;
    }

    public String getId() {
        return id;
    }

    public boolean matches(Entity entity) {
        for (Predicate<Entity> rule : rules) {
            if (rule.test(entity)) return true;
        }
        return false;
    }
}
