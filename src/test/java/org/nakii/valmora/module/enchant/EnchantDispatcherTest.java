package org.nakii.valmora.module.enchant;

import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.script.ScriptModule;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end coverage of {@link EnchantDispatcher}'s "run legacy Java logic, then dispatch the
 * compiled trigger" hybrid-coexistence contract — the core design goal of the enchant overhaul
 * (existing {@code EnchantmentLogic} implementations keep working unmodified, while YAML-compiled
 * {@code triggers:} blocks fire alongside them, not instead of them).
 */
class EnchantDispatcherTest {

    private EnchantmentRegistry registry;
    private HookBus hookBus;

    @BeforeEach
    void setUp() {
        registry = new EnchantmentRegistry();

        ValmoraAPI api = mock(ValmoraAPI.class);
        var enchantModule = mock(EnchantModule.class);
        when(api.getEnchantModule()).thenReturn(enchantModule);
        when(enchantModule.getRegistry()).thenReturn(registry);

        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EnchantDispatcherTest"));
        hookBus = new HookBus(plugin);
        when(api.getHookBus()).thenReturn(hookBus);

        ScriptModule scriptModule = mock(ScriptModule.class);
        when(api.getScriptModule()).thenReturn(scriptModule);

        ValmoraAPI.setProvider(api);
    }

    private SimpleExecutionContext ctx() {
        LivingEntity attacker = mock(LivingEntity.class);
        LivingEntity victim = mock(LivingEntity.class);
        return new SimpleExecutionContext(attacker, victim, null, null);
    }

    @Test
    void dispatchPostAttackRunsBothTheLegacyLogicAndTheCompiledTrigger() {
        List<String> ran = new java.util.ArrayList<>();
        EnchantmentLogic legacyLogic = mock(EnchantmentLogic.class);
        EnchantTriggerBlock triggerBlock = new EnchantTriggerBlock(null, c -> ran.add("trigger"), null);

        EnchantmentDefinition def = EnchantmentDefinition.builder("test_enchant")
                .logic(legacyLogic)
                .trigger(EnchantTrigger.ON_ATTACK_POST, triggerBlock)
                .build();
        registry.register("test_enchant", def);
        hookBus.registerYamlStage(EnchantDispatcher.point("test_enchant", EnchantTrigger.ON_ATTACK_POST),
                new EnchantTriggerStage("test_enchant", triggerBlock));

        var instance = new EnchantStateStore.EnchantInstance("test_enchant", 3, Map.of());
        var context = ctx();
        LivingEntity attacker = context.getCaster();
        LivingEntity victim = context.getTarget().orElseThrow();
        DamageResult result = new DamageResult(10.0, DamageType.MELEE, false, attacker, victim);

        EnchantDispatcher.dispatchPostAttack(result, attacker, victim, 3, instance, def, context);

        verify(legacyLogic).onPostAttack(result, attacker, victim, 3);
        assertEquals(List.of("trigger"), ran, "the compiled trigger must also fire, not just the legacy logic");
    }

    @Test
    void dispatchPostAttackWithNoLogicStillFiresTheTrigger() {
        List<String> ran = new java.util.ArrayList<>();
        EnchantTriggerBlock triggerBlock = new EnchantTriggerBlock(null, c -> ran.add("trigger"), null);
        EnchantmentDefinition def = EnchantmentDefinition.builder("no_logic")
                .trigger(EnchantTrigger.ON_ATTACK_POST, triggerBlock)
                .build();
        registry.register("no_logic", def);
        hookBus.registerYamlStage(EnchantDispatcher.point("no_logic", EnchantTrigger.ON_ATTACK_POST),
                new EnchantTriggerStage("no_logic", triggerBlock));

        var instance = new EnchantStateStore.EnchantInstance("no_logic", 1, Map.of());
        var context = ctx();
        DamageResult result = new DamageResult(5.0, DamageType.MELEE, false, context.getCaster(), context.getTarget().orElseThrow());

        assertDoesNotThrow(() ->
                EnchantDispatcher.dispatchPostAttack(result, context.getCaster(), context.getTarget().get(), 1, instance, def, context));
        assertEquals(List.of("trigger"), ran);
    }

    @Test
    void fireTriggerForItemSkipsEnchantsWithNoStagesRegistered() {
        // No hookBus.registerYamlStage(...) call for this enchant — dispatch must be a safe no-op,
        // not attach anything or throw, when nothing is listening at that point.
        EnchantmentDefinition def = EnchantmentDefinition.builder("silent").build();
        registry.register("silent", def);

        var context = ctx();
        assertDoesNotThrow(() -> EnchantDispatcher.fireTrigger(def, new EnchantStateStore.EnchantInstance("silent", 1, Map.of()),
                1, EnchantTrigger.ON_KILL, context));
        assertFalse(context.has("enchant:id"));
    }
}
