package org.nakii.valmora.module.enchant;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Structured PDC-backed store for enchant instances, replacing {@code EnchantmentHelper}'s
 * historical flat CSV string ({@link Keys#ENCHANTS_CONTAINER_KEY} = {@code "id1:level1,id2:level2"}).
 *
 * <p>Storage shape mirrors (independently — no shared code, per the enchant/modifier separation
 * decision) {@code module.modifier.ModifierComponentStore}: one {@link Keys#ENCHANTS_STATE_CONTAINER_KEY}
 * holding a {@code TAG_CONTAINER_ARRAY}, one nested container per enchant instance with fields
 * {@code id} (STRING), {@code level} (INTEGER), and an optional {@code state} field — a single
 * delimited STRING blob of persistent counters (e.g. {@code "kills=1542,combo=3"}), one entry per
 * declared {@code state.persistent} key (see {@code EnchantModule}'s YAML schema). Unlike
 * {@code ModifierComponentStore}'s one-NamespacedKey-per-state-key nested container, this uses a
 * single fixed field so no plugin instance is needed to construct per-key NamespacedKeys.
 *
 * <h2>Migration</h2>
 * {@link #load} prefers the structured key; if absent, it falls back to parsing the legacy CSV
 * format read-only (no write-back). {@link #save} always writes the structured format and deletes
 * the legacy key. Consequently, any code path that <em>mutates</em> an item's enchants (apply /
 * remove / anvil merge) transparently upgrades that item to the structured format as a side
 * effect — there is no batch migration script, and items nobody touches keep working through the
 * legacy fallback indefinitely.
 */
public class EnchantStateStore {

    private EnchantStateStore() {
    }

    /** One enchant instance stored on an item: id, level, and its persistent state map (empty for
     *  enchants with no {@code state.persistent} block, or for instances still on the legacy
     *  read-only CSV fallback path). Mutable so callers (Phase 3's state engine) can increment
     *  persistent state in place before a single {@link #save}. */
    public static class EnchantInstance {
        private final String id;
        private int level;
        private final Map<String, Integer> state;

        public EnchantInstance(String id, int level, Map<String, Integer> state) {
            this.id = id.toLowerCase(Locale.ROOT);
            this.level = level;
            this.state = new HashMap<>(state);
        }

        public String getId() {
            return id;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public Map<String, Integer> getState() {
            return state;
        }

        public int getStateValue(String key, int def) {
            return state.getOrDefault(key, def);
        }

        public void setStateValue(String key, int value) {
            state.put(key, value);
        }
    }

    public static Map<String, EnchantInstance> load(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return new LinkedHashMap<>();
        return load(item.getItemMeta());
    }

    public static Map<String, EnchantInstance> load(ItemMeta meta) {
        if (meta == null) return new LinkedHashMap<>();
        return load(meta.getPersistentDataContainer());
    }

    public static Map<String, EnchantInstance> load(PersistentDataContainer pdc) {
        Map<String, EnchantInstance> result = new LinkedHashMap<>();
        if (pdc == null) return result;

        if (pdc.has(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY)) {
            PersistentDataContainer[] entries = pdc.get(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY);
            if (entries != null) {
                for (PersistentDataContainer entry : entries) {
                    String id = entry.get(Keys.ENCHANT_INSTANCE_ID_KEY, PersistentDataType.STRING);
                    if (id == null) continue;
                    int level = entry.getOrDefault(Keys.ENCHANT_INSTANCE_LEVEL_KEY, PersistentDataType.INTEGER, 1);
                    String stateBlob = entry.get(Keys.ENCHANT_INSTANCE_STATE_KEY, PersistentDataType.STRING);
                    result.put(id.toLowerCase(Locale.ROOT), new EnchantInstance(id, level, deserializeState(stateBlob)));
                }
            }
            return result;
        }

        // Legacy CSV fallback — read-only, never written back here (see class doc).
        if (pdc.has(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING)) {
            String serialized = pdc.get(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING);
            if (serialized != null && !serialized.isEmpty()) {
                for (String pair : serialized.split(",")) {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        try {
                            String id = parts[0];
                            int level = Integer.parseInt(parts[1]);
                            result.put(id.toLowerCase(Locale.ROOT), new EnchantInstance(id, level, Map.of()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Writes {@code instances} using the structured format and removes the legacy CSV key if
     *  present — see class doc's migration-on-write behavior. Passing an empty/null map clears
     *  both keys entirely (mirrors the old CSV path's "remove the container when empty" behavior). */
    public static void save(ItemMeta meta, Map<String, EnchantInstance> instances) {
        if (meta == null) return;
        save(meta.getPersistentDataContainer(), instances);
    }

    public static void save(PersistentDataContainer pdc, Map<String, EnchantInstance> instances) {
        if (pdc == null) return;
        pdc.remove(Keys.ENCHANTS_CONTAINER_KEY);

        if (instances == null || instances.isEmpty()) {
            pdc.remove(Keys.ENCHANTS_STATE_CONTAINER_KEY);
            return;
        }

        PersistentDataContainer[] entries = new PersistentDataContainer[instances.size()];
        int i = 0;
        for (EnchantInstance instance : instances.values()) {
            PersistentDataContainer entry = pdc.getAdapterContext().newPersistentDataContainer();
            entry.set(Keys.ENCHANT_INSTANCE_ID_KEY, PersistentDataType.STRING, instance.getId());
            entry.set(Keys.ENCHANT_INSTANCE_LEVEL_KEY, PersistentDataType.INTEGER, instance.getLevel());
            if (!instance.getState().isEmpty()) {
                entry.set(Keys.ENCHANT_INSTANCE_STATE_KEY, PersistentDataType.STRING, serializeState(instance.getState()));
            }
            entries[i++] = entry;
        }
        pdc.set(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY, entries);
    }

    public static int getPersistentState(EnchantInstance instance, String key, int def) {
        return instance == null ? def : instance.getStateValue(key, def);
    }

    public static void setPersistentState(EnchantInstance instance, String key, int value) {
        if (instance != null) instance.setStateValue(key, value);
    }

    private static String serializeState(Map<String, Integer> state) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : state.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static Map<String, Integer> deserializeState(String blob) {
        Map<String, Integer> state = new HashMap<>();
        if (blob == null || blob.isEmpty()) return state;
        for (String pair : blob.split(",")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                try {
                    state.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return state;
    }
}
