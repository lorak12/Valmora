package org.nakii.valmora.module.enchant;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.nakii.valmora.util.Keys;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 of the enchant overhaul (docs/MODIFIER_FRAMEWORK_BACKLOG.md-adjacent enchant rework):
 * covers the structured PDC store replacing the historical flat CSV
 * {@link Keys#ENCHANTS_CONTAINER_KEY} string, including the legacy-format read fallback and
 * migrate-on-write behavior that lets already-enchanted items keep working with no batch
 * migration step.
 */
@Tag("mockbukkit")
class EnchantStateStoreTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        // Keys.init(Valmora) can't be called directly here — a Mockito mock of the concrete
        // Valmora class doesn't satisfy whatever NamespacedKey's real Paper-API constructor
        // actually needs internally (observed empirically: even with every plausible accessor
        // stubbed, construction still fails) — so every Keys field this test touches is built
        // directly off a real MockBukkit PluginMock instead, which works reliably.
        PluginMock plugin = MockBukkit.createMockPlugin("Valmora");
        Keys.ENCHANTS_CONTAINER_KEY = key(plugin, "valmora_enchants_container");
        Keys.ENCHANTS_STATE_CONTAINER_KEY = key(plugin, "valmora_enchants_state_container");
        Keys.ENCHANT_INSTANCE_ID_KEY = key(plugin, "enchant_instance_id");
        Keys.ENCHANT_INSTANCE_LEVEL_KEY = key(plugin, "enchant_instance_level");
        Keys.ENCHANT_INSTANCE_STATE_KEY = key(plugin, "enchant_instance_state");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static NamespacedKey key(PluginMock plugin, String name) {
        return new NamespacedKey(plugin, name);
    }

    private ItemStack sword() {
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    @Test
    void emptyItemHasNoInstances() {
        assertTrue(EnchantStateStore.load(sword()).isEmpty());
    }

    @Test
    void roundTripsIdAndLevel() {
        ItemStack item = sword();
        ItemMeta meta = item.getItemMeta();
        var instances = new java.util.LinkedHashMap<String, EnchantStateStore.EnchantInstance>();
        instances.put("sharpness", new EnchantStateStore.EnchantInstance("sharpness", 3, Map.of()));
        instances.put("thorns", new EnchantStateStore.EnchantInstance("thorns", 1, Map.of()));
        EnchantStateStore.save(meta, instances);
        item.setItemMeta(meta);

        Map<String, EnchantStateStore.EnchantInstance> loaded = EnchantStateStore.load(item);
        assertEquals(2, loaded.size());
        assertEquals(3, loaded.get("sharpness").getLevel());
        assertEquals(1, loaded.get("thorns").getLevel());
    }

    @Test
    void roundTripsPersistentState() {
        ItemStack item = sword();
        ItemMeta meta = item.getItemMeta();
        var instance = new EnchantStateStore.EnchantInstance("champion", 2, Map.of());
        instance.setStateValue("kills", 1542);
        var instances = Map.of("champion", instance);
        EnchantStateStore.save(meta, instances);
        item.setItemMeta(meta);

        Map<String, EnchantStateStore.EnchantInstance> loaded = EnchantStateStore.load(item);
        assertEquals(1542, loaded.get("champion").getStateValue("kills", 0));
    }

    @Test
    void savingEmptyMapRemovesTheContainer() {
        ItemStack item = sword();
        ItemMeta meta = item.getItemMeta();
        EnchantStateStore.save(meta, Map.of("sharpness", new EnchantStateStore.EnchantInstance("sharpness", 1, Map.of())));
        item.setItemMeta(meta);
        assertFalse(EnchantStateStore.load(item).isEmpty());

        meta = item.getItemMeta();
        EnchantStateStore.save(meta, Map.of());
        item.setItemMeta(meta);
        assertTrue(EnchantStateStore.load(item).isEmpty());
        assertFalse(item.getItemMeta().getPersistentDataContainer()
                .has(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY));
    }

    @Test
    void legacyCsvIsReadOnlyFallback() {
        ItemStack item = sword();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING, "sharpness:4,thorns:2");
        item.setItemMeta(meta);

        Map<String, EnchantStateStore.EnchantInstance> loaded = EnchantStateStore.load(item);
        assertEquals(4, loaded.get("sharpness").getLevel());
        assertEquals(2, loaded.get("thorns").getLevel());

        // Read-only: the legacy key must still be present, and the new structured key absent,
        // until something actually mutates (saves) the item.
        assertTrue(item.getItemMeta().getPersistentDataContainer()
                .has(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING));
        assertFalse(item.getItemMeta().getPersistentDataContainer()
                .has(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY));
    }

    @Test
    void savingMigratesLegacyCsvToStructuredFormatAndDeletesTheOldKey() {
        ItemStack item = sword();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING, "sharpness:4");
        item.setItemMeta(meta);

        // Any mutation (here: re-saving what load() produced) upgrades the item.
        meta = item.getItemMeta();
        Map<String, EnchantStateStore.EnchantInstance> loaded = EnchantStateStore.load(meta);
        EnchantStateStore.save(meta, loaded);
        item.setItemMeta(meta);

        assertFalse(item.getItemMeta().getPersistentDataContainer()
                .has(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING));
        assertTrue(item.getItemMeta().getPersistentDataContainer()
                .has(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY));
        assertEquals(4, EnchantStateStore.load(item).get("sharpness").getLevel());
    }
}
