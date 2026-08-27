package org.nakii.valmora.module.modifier;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic PDC-backed component store for active modifier instances (docs/
 * Valmora_Modifier_Framework_Design.docx §3 core model: "modifiers:&lt;group_id&gt;" components).
 * One {@link NamespacedKey} per group, holding a {@code TAG_CONTAINER_ARRAY} — one nested container
 * per {@link ModifierInstance}, so {@code StorageMode.INSTANCES} groups (e.g. gemstones) keep each
 * application's tier/state independent, while {@code STACKED}/{@code SINGLE} groups simply store a
 * single-element or single-entry-per-id array.
 *
 * <p>The stored item only carries id/tier/count/state — the {@link ModifierDefinition} stays in the
 * registry (§3: "The stored item instance should contain only the data required to identify and
 * parameterize the modifier").
 */
public class ModifierComponentStore {

    private final Valmora plugin;
    private final Map<String, NamespacedKey> keyCache = new ConcurrentHashMap<>();

    private static final String FIELD_ID = "id";
    private static final String FIELD_TIER = "tier";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_STATE = "state";

    public ModifierComponentStore(Valmora plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey keyFor(String groupId) {
        return keyCache.computeIfAbsent(groupId.toLowerCase(Locale.ROOT),
                g -> new NamespacedKey(plugin, "modifiers_" + g));
    }

    public List<ModifierInstance> read(ItemMeta meta, String groupId) {
        List<ModifierInstance> result = new ArrayList<>();
        if (meta == null) return result;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = keyFor(groupId);
        if (!pdc.has(key, PersistentDataType.TAG_CONTAINER_ARRAY)) return result;

        PersistentDataContainer[] entries = pdc.get(key, PersistentDataType.TAG_CONTAINER_ARRAY);
        if (entries == null) return result;

        for (PersistentDataContainer entry : entries) {
            String id = entry.get(nk(FIELD_ID), PersistentDataType.STRING);
            if (id == null) continue;
            int tier = entry.getOrDefault(nk(FIELD_TIER), PersistentDataType.INTEGER, 1);
            int count = entry.getOrDefault(nk(FIELD_COUNT), PersistentDataType.INTEGER, 1);

            Map<String, Integer> state = new HashMap<>();
            PersistentDataContainer stateContainer = entry.get(nk(FIELD_STATE), PersistentDataType.TAG_CONTAINER);
            if (stateContainer != null) {
                for (NamespacedKey stateKey : stateContainer.getKeys()) {
                    Integer v = stateContainer.get(stateKey, PersistentDataType.INTEGER);
                    if (v != null) state.put(stateKey.getKey(), v);
                }
            }
            result.add(new ModifierInstance(groupId, id, tier, count, state));
        }
        return result;
    }

    /** Reads every group component present on the item, keyed by group id. */
    public Map<String, List<ModifierInstance>> readAll(ItemMeta meta, ModifierGroupRegistry groups) {
        Map<String, List<ModifierInstance>> all = new HashMap<>();
        if (meta == null) return all;
        for (ModifierGroupDefinition group : groups.values()) {
            List<ModifierInstance> instances = read(meta, group.getId());
            if (!instances.isEmpty()) all.put(group.getId(), instances);
        }
        return all;
    }

    public void write(ItemMeta meta, String groupId, List<ModifierInstance> instances) {
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = keyFor(groupId);

        if (instances == null || instances.isEmpty()) {
            pdc.remove(key);
            return;
        }

        PersistentDataContainer[] entries = new PersistentDataContainer[instances.size()];
        for (int i = 0; i < instances.size(); i++) {
            ModifierInstance instance = instances.get(i);
            PersistentDataContainer entry = pdc.getAdapterContext().newPersistentDataContainer();
            entry.set(nk(FIELD_ID), PersistentDataType.STRING, instance.getModifierId());
            entry.set(nk(FIELD_TIER), PersistentDataType.INTEGER, instance.getTier());
            entry.set(nk(FIELD_COUNT), PersistentDataType.INTEGER, instance.getCount());

            if (!instance.getState().isEmpty()) {
                PersistentDataContainer stateContainer = pdc.getAdapterContext().newPersistentDataContainer();
                for (Map.Entry<String, Integer> stateEntry : instance.getState().entrySet()) {
                    stateContainer.set(nk(stateEntry.getKey()), PersistentDataType.INTEGER, stateEntry.getValue());
                }
                entry.set(nk(FIELD_STATE), PersistentDataType.TAG_CONTAINER, stateContainer);
            }
            entries[i] = entry;
        }
        pdc.set(key, PersistentDataType.TAG_CONTAINER_ARRAY, entries);
    }

    public void clear(ItemMeta meta, String groupId) {
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(keyFor(groupId));
    }

    /** Field keys inside a per-instance nested container are plugin-namespaced but field-name-scoped (not group-scoped) — safe since they never leave that nested container. */
    private NamespacedKey nk(String field) {
        return new NamespacedKey(plugin, field);
    }
}
