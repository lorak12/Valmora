package org.nakii.valmora.module.enchant.state;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.enchant.EnchantModule;
import org.nakii.valmora.module.enchant.EnchantStateStore;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentRegistry;
import org.nakii.valmora.util.Keys;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Needs a real PDC (via MockBukkit) for the persistent-write path's {@code ItemStack}/{@code
 *  ItemMeta} round-trip; the transient path is pure Java and doesn't strictly need it, but sharing
 *  one setup keeps this test focused on the facade's tier-selection logic rather than plumbing. */
@Tag("mockbukkit")
class EnchantStateEngineTest {

    private ServerMock server;
    private ValmoraAPI api;
    private EnchantModule enchantModule;
    private EnchantmentRegistry registry;
    private EnchantStateEngine engine;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // Keys.init(Valmora) can't be called here — a Mockito mock of the concrete Valmora class
        // doesn't satisfy whatever NamespacedKey's real constructor needs internally (see
        // EnchantStateStoreTest) — so the fields are assigned directly off a real PluginMock.
        PluginMock plugin = MockBukkit.createMockPlugin("Valmora");
        Keys.ENCHANTS_CONTAINER_KEY = new NamespacedKey(plugin, "valmora_enchants_container");
        Keys.ENCHANTS_STATE_CONTAINER_KEY = new NamespacedKey(plugin, "valmora_enchants_state_container");
        Keys.ENCHANT_INSTANCE_ID_KEY = new NamespacedKey(plugin, "enchant_instance_id");
        Keys.ENCHANT_INSTANCE_LEVEL_KEY = new NamespacedKey(plugin, "enchant_instance_level");
        Keys.ENCHANT_INSTANCE_STATE_KEY = new NamespacedKey(plugin, "enchant_instance_state");

        api = mock(ValmoraAPI.class);
        ValmoraAPI.setProvider(api);

        enchantModule = mock(EnchantModule.class);
        registry = new EnchantmentRegistry();
        when(api.getEnchantModule()).thenReturn(enchantModule);
        when(enchantModule.getRegistry()).thenReturn(registry);

        engine = new EnchantStateEngine(new TransientStateTracker());
        when(enchantModule.getStateEngine()).thenReturn(engine);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SimpleExecutionContext ctxWithEnchant(String enchantId, EnchantStateStore.EnchantInstance instance,
                                                    LivingEntity caster, LivingEntity target) {
        var ctx = new SimpleExecutionContext(caster, target, null, null);
        ctx.set("enchant:id", enchantId);
        ctx.set("enchant:instance", instance);
        return ctx;
    }

    @Test
    void resolveReadsTransientStateWhenDeclaredTransient() {
        var def = EnchantmentDefinition.builder("lethality")
                .transientState("stacks", new TransientStateDefinition(0, false, 0))
                .build();
        registry.register("lethality", def);

        LivingEntity attacker = mock(LivingEntity.class);
        LivingEntity victim = mock(LivingEntity.class);
        UUID attackerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerId);
        when(victim.getUniqueId()).thenReturn(victimId);

        var instance = new EnchantStateStore.EnchantInstance("lethality", 3, Map.of());
        var ctx = ctxWithEnchant("lethality", instance, attacker, victim);

        assertEquals(0, engine.resolve(ctx, "stacks"));
        engine.mutate(ctx, "stacks", "increment", 0);
        assertEquals(1, engine.resolve(ctx, "stacks"));
    }

    @Test
    void resolveReadsPersistentStateFromTheAttachedInstanceWhenDeclaredPersistent() {
        var def = EnchantmentDefinition.builder("champion")
                .persistentState("kills", new PersistentStateDefinition(0))
                .build();
        registry.register("champion", def);

        var instance = new EnchantStateStore.EnchantInstance("champion", 3, Map.of("kills", 42));
        var ctx = ctxWithEnchant("champion", instance, mock(LivingEntity.class), null);

        assertEquals(42, engine.resolve(ctx, "kills"));
    }

    @Test
    void resolveDefaultsToZeroWhenNoEnchantIsAttached() {
        var ctx = new SimpleExecutionContext(mock(LivingEntity.class), null, null, null);
        assertEquals(0, engine.resolve(ctx, "anything"));
    }

    @Test
    void resolveUsesThePersistentStateDefinitionsDefaultValue() {
        var def = EnchantmentDefinition.builder("champion")
                .persistentState("kills", new PersistentStateDefinition(7))
                .build();
        registry.register("champion", def);

        var instance = new EnchantStateStore.EnchantInstance("champion", 3, Map.of());
        var ctx = ctxWithEnchant("champion", instance, mock(LivingEntity.class), null);

        assertEquals(7, engine.resolve(ctx, "kills"));
    }

    @Test
    void mutatePersistentSavesBackToTheAttachedItem() {
        var def = EnchantmentDefinition.builder("champion")
                .persistentState("kills", new PersistentStateDefinition(0))
                .build();
        registry.register("champion", def);

        ItemStack item = new ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        EnchantStateStore.save(meta, Map.of("champion", new EnchantStateStore.EnchantInstance("champion", 3, Map.of("kills", 5))));
        item.setItemMeta(meta);

        var ctx = new SimpleExecutionContext(mock(LivingEntity.class), null, null, null);
        ctx.set("enchant:id", "champion");
        ctx.set("enchant:item", item);

        assertEquals(6, engine.mutate(ctx, "kills", "increment", 0));

        Map<String, EnchantStateStore.EnchantInstance> reloaded = EnchantStateStore.load(item);
        assertEquals(6, reloaded.get("champion").getStateValue("kills", -1));
    }

    @Test
    void mutatePersistentWithNoItemAttachedIsANoOp() {
        var def = EnchantmentDefinition.builder("champion")
                .persistentState("kills", new PersistentStateDefinition(0))
                .build();
        registry.register("champion", def);

        var ctx = new SimpleExecutionContext(mock(LivingEntity.class), null, null, null);
        ctx.set("enchant:id", "champion");

        assertEquals(0, engine.mutate(ctx, "kills", "increment", 0));
    }
}
