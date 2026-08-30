package org.nakii.valmora.module.gui.event;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.enchant.EnchantModule;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.enchant.EnchantmentRegistry;
import org.nakii.valmora.module.gui.GuiDefinition;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the two Phase 4 fixes to {@code enchant_apply}: enforcing the etable-max-level cap
 * server-side (previously only clamped to the absolute cap — see {@link EnchantmentHelperTest})
 * and actually charging {@link org.nakii.valmora.module.enchant.EtableCostCalculator}'s XP cost
 * (previously the enchanting table charged nothing at all).
 *
 * <p>The success path reaches {@code new GuiRenderer(plugin).render(session)} at the end, which
 * throws against this test's minimal (mocked) GUI fixture — harmlessly, since the event's own
 * {@code catch (Exception ignored)} swallows it <em>after</em> the enchant has already been applied
 * and the XP already deducted, so those effects are still observable (same reasoning
 * {@code GuiForceCraftEventFactoryTest} documents for why it doesn't attempt a full render fixture).
 */
@Tag("mockbukkit")
class EnchantApplyEventFactoryTest {

    private ServerMock server;
    private Valmora plugin;
    private EnchantmentRegistry registry;
    private PlayerMock player;
    private GuiSession session;
    private ItemStack sword;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Valmora.class);

        PluginMock realPlugin = MockBukkit.createMockPlugin("Valmora");
        Keys.ITEM_ID_KEY = new NamespacedKey(realPlugin, "valmora_item_id");
        Keys.ITEM_TYPE_KEY = new NamespacedKey(realPlugin, "item_type");
        Keys.ENCHANTS_CONTAINER_KEY = new NamespacedKey(realPlugin, "valmora_enchants_container");
        Keys.ENCHANTS_STATE_CONTAINER_KEY = new NamespacedKey(realPlugin, "valmora_enchants_state_container");
        Keys.ENCHANT_INSTANCE_ID_KEY = new NamespacedKey(realPlugin, "enchant_instance_id");
        Keys.ENCHANT_INSTANCE_LEVEL_KEY = new NamespacedKey(realPlugin, "enchant_instance_level");
        Keys.ENCHANT_INSTANCE_STATE_KEY = new NamespacedKey(realPlugin, "enchant_instance_state");
        Keys.GENERIC_BASE_LORE_KEY = new NamespacedKey(realPlugin, "valmora_generic_base_lore");

        registry = new EnchantmentRegistry();
        ValmoraAPI api = mock(ValmoraAPI.class);
        EnchantModule enchantModule = mock(EnchantModule.class);
        when(api.getEnchantModule()).thenReturn(enchantModule);
        when(enchantModule.getRegistry()).thenReturn(registry);
        ValmoraAPI.setProvider(api);

        registry.register("sharpness", EnchantmentDefinition.builder("sharpness")
                .name("Sharpness")
                .target(ItemType.SWORD)
                .etableMaxLevel(4)
                .absoluteMaxLevel(7)
                .build());

        player = server.addPlayer();
        player.setLevel(100);
        sword = new ItemStack(Material.DIAMOND_SWORD);

        GuiDefinition definition = new GuiDefinition("enchanting_table", "Enchant", 54, 6,
                "enchanting_table", List.of(List.of(' ')), Map.of(),
                null, null, null, null, null, null);
        session = spy(new GuiSession(player, definition, server.createInventory(null, 54), new HashMap<>()));
        doReturn(Map.of("ingredient", sword)).when(session).getInputSnapshot();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void invoke(String[] args) {
        new EnchantApplyEventFactory(plugin)
                .compile(args, EventOptions.DEFAULT)
                .execute(new GuiExecutionContext(player, session));
    }

    @Test
    void appliesTheEnchantAndDeductsXpWhenAffordable() {
        invoke(new String[]{"ingredient", "sharpness", "3"});

        assertEquals(3, EnchantmentHelper.getEnchantLevel(sword, "sharpness"));
        assertEquals(100 - 6, player.getLevel(), "cost for level 3 is 3*2=6 XP levels");
    }

    @Test
    void enforcesTheEtableMaxLevelCapNotTheAbsoluteCap() {
        player.setLevel(300); // requesting level 99 costs 99*2=198 XP levels — must be affordable
        invoke(new String[]{"ingredient", "sharpness", "99"});

        // Previously only clamped to absoluteMaxLevel (7) — the etable path must clamp to
        // etableMaxLevel (4) instead, exactly like EnchantmentHelper's own 4-arg overload test.
        assertEquals(4, EnchantmentHelper.getEnchantLevel(sword, "sharpness"));
    }

    @Test
    void insufficientXpRejectsTheApplicationEntirely() {
        player.setLevel(1);

        invoke(new String[]{"ingredient", "sharpness", "5"});

        assertEquals(0, EnchantmentHelper.getEnchantLevel(sword, "sharpness"), "no enchant applied without enough XP");
        assertEquals(1, player.getLevel(), "no XP deducted when the check fails");
    }

    @Test
    void tooFewArgsIsANoOp() {
        assertDoesNotThrow(() -> invoke(new String[]{"ingredient", "sharpness"}));
        assertEquals(0, EnchantmentHelper.getEnchantLevel(sword, "sharpness"));
    }
}
