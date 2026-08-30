package org.nakii.valmora.module.enchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.util.Keys;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phase 1 of the enchant overhaul: {@link EnchantmentHelper} rewired onto {@link EnchantStateStore},
 * plus the two behavior fixes that came with it — etable-max-level is now enforced server-side
 * (not just by which buttons the enchanting-table GUI happens to render), and enchant lore renders
 * a Roman numeral level like every other levelled display in the codebase, instead of a raw integer.
 */
@Tag("mockbukkit")
class EnchantmentHelperTest {

    private EnchantmentRegistry registry;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        // See EnchantStateStoreTest for why Keys fields are built off a real PluginMock rather
        // than through Keys.init(mock(Valmora.class)).
        PluginMock plugin = MockBukkit.createMockPlugin("Valmora");
        Keys.ITEM_ID_KEY = new NamespacedKey(plugin, "valmora_item_id");
        Keys.ITEM_TYPE_KEY = new NamespacedKey(plugin, "item_type");
        Keys.ENCHANTS_CONTAINER_KEY = new NamespacedKey(plugin, "valmora_enchants_container");
        Keys.ENCHANTS_STATE_CONTAINER_KEY = new NamespacedKey(plugin, "valmora_enchants_state_container");
        Keys.ENCHANT_INSTANCE_ID_KEY = new NamespacedKey(plugin, "enchant_instance_id");
        Keys.ENCHANT_INSTANCE_LEVEL_KEY = new NamespacedKey(plugin, "enchant_instance_level");
        Keys.ENCHANT_INSTANCE_STATE_KEY = new NamespacedKey(plugin, "enchant_instance_state");
        Keys.GENERIC_BASE_LORE_KEY = new NamespacedKey(plugin, "valmora_generic_base_lore");

        registry = new EnchantmentRegistry();
        ValmoraAPI api = mock(ValmoraAPI.class);
        var enchantModule = mock(org.nakii.valmora.module.enchant.EnchantModule.class);
        when(api.getEnchantModule()).thenReturn(enchantModule);
        when(enchantModule.getRegistry()).thenReturn(registry);
        ValmoraAPI.setProvider(api);

        registry.register("sharpness", EnchantmentDefinition.builder("sharpness")
                .name("Sharpness")
                .target(org.nakii.valmora.module.item.ItemType.SWORD)
                .etableMaxLevel(4)
                .absoluteMaxLevel(7)
                .build());
        registry.register("smite", EnchantmentDefinition.builder("smite")
                .name("Smite")
                .target(org.nakii.valmora.module.item.ItemType.SWORD)
                .conflict("sharpness")
                .build());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack sword() {
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    @Test
    void threeArgOverloadClampsToAbsoluteMaxLevel() {
        ItemStack item = sword();
        EnchantmentHelper.applyEnchantment(item, "sharpness", 99);
        assertEquals(7, EnchantmentHelper.getEnchantLevel(item, "sharpness"));
    }

    @Test
    void fourArgOverloadWithEtableCapEnforcesTheLowerCeiling() {
        ItemStack item = sword();
        EnchantmentHelper.applyEnchantment(item, "sharpness", 99, true);
        // Previously this only clamped to absoluteMaxLevel (7) even on the etable path — the
        // etable GUI only ever offered levels up to etableMaxLevel (4) as buttons, but nothing
        // stopped a level above that from being accepted server-side if requested directly.
        assertEquals(4, EnchantmentHelper.getEnchantLevel(item, "sharpness"));
    }

    @Test
    void fourArgOverloadWithoutEtableCapStillUsesAbsoluteMaxLevel() {
        ItemStack item = sword();
        EnchantmentHelper.applyEnchantment(item, "sharpness", 99, false);
        assertEquals(7, EnchantmentHelper.getEnchantLevel(item, "sharpness"));
    }

    @Test
    void conflictingEnchantIsRejected() {
        ItemStack item = sword();
        EnchantmentHelper.applyEnchantment(item, "sharpness", 3);
        EnchantmentHelper.applyEnchantment(item, "smite", 3);

        assertEquals(3, EnchantmentHelper.getEnchantLevel(item, "sharpness"));
        assertEquals(0, EnchantmentHelper.getEnchantLevel(item, "smite"));
    }

    @Test
    void formatEnchantsRendersRomanNumeralsNotRawIntegers() {
        List<Component> lore = EnchantmentHelper.formatEnchants(Map.of("sharpness", 5));
        String rendered = MiniMessage.miniMessage().serialize(lore.get(0));
        assertTrue(rendered.contains("Sharpness V"), "expected Roman numeral 'V' in: " + rendered);
        assertFalse(rendered.contains("Sharpness 5"));
    }

    @Test
    void applyingUnknownItemTypeEnchantIsRejected() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        EnchantmentHelper.applyEnchantment(pickaxe, "sharpness", 1);
        assertEquals(0, EnchantmentHelper.getEnchantLevel(pickaxe, "sharpness"));
    }
}
