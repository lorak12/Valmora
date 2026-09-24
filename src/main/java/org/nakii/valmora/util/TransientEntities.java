package org.nakii.valmora.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Short-lived entities Valmora spawns for presentation (damage indicators, summoned pets, ...).
 *
 * <p>{@link #mark} tags them and makes them non-persistent, so they aren't saved with the chunk.
 * If one still ends up on disk (a crash, or its chunk unloading before cleanup ran), the
 * {@link Sweeper} removes it the moment its chunk loads again. Untagged ones used to float or
 * wander forever, with nothing able to identify them afterwards.
 */
public final class TransientEntities {

    private TransientEntities() {}

    public static void mark(Entity entity, String owner) {
        entity.getPersistentDataContainer().set(Keys.TRANSIENT_ENTITY_KEY, PersistentDataType.STRING, owner);
        entity.setPersistent(false);
    }

    public static boolean isTransient(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.TRANSIENT_ENTITY_KEY, PersistentDataType.STRING);
    }

    /** Removes every transient entity currently loaded; returns how many. For startup. */
    public static int sweepLoadedWorlds() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isTransient(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Removes transient leftovers as chunks load. Registered once for the plugin's lifetime. */
    public static final class Sweeper implements Listener {
        @EventHandler
        public void onEntitiesLoad(EntitiesLoadEvent event) {
            for (Entity entity : event.getEntities()) {
                if (isTransient(entity)) entity.remove();
            }
        }
    }
}
