package org.nakii.valmora.module.item;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.condition.ConditionGroup;
import org.nakii.valmora.module.ui.ActionBarUI;
import org.nakii.valmora.module.ui.UIManager;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Characterization tests for {@link AbilityExecutor}'s single-ability firing — written against the
 * pre-flow implementation and kept unchanged after the move to {@code item_pipeline.yml}'s default
 * {@code cast} flow (docs/PIPELINE_FLOWS_PLAN.md §6 steps 1 and 7).
 */
class AbilityCastFlowTest {

    private ScriptModule scriptModule;
    private Player player;
    private LivingEntity target;
    private PlayerState state;
    private CooldownManager cooldowns;
    private ActionBarUI actionBar;
    private ConfiguredMechanic mechanic;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;

    @BeforeEach
    void setUp() throws Exception {
        File dataFolder = Files.createTempDirectory("valmora-ability").toFile();
        Valmora plugin = mock(Valmora.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AbilityCastFlowTest"));
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("caster");
        when(player.getLocation()).thenReturn(mock(Location.class));
        target = mock(LivingEntity.class);

        ValmoraProfile profile = mock(ValmoraProfile.class);
        cooldowns = new CooldownManager();
        state = mock(PlayerState.class);
        when(state.getCurrentMana()).thenReturn(100.0);
        when(profile.getCooldownManager()).thenReturn(cooldowns);
        when(profile.getPlayerState()).thenReturn(state);
        ValmoraPlayer session = mock(ValmoraPlayer.class);
        when(session.getActiveProfile()).thenReturn(profile);
        PlayerManager playerManager = mock(PlayerManager.class);
        when(playerManager.getSession(any())).thenReturn(session);

        UIManager ui = mock(UIManager.class);
        actionBar = mock(ActionBarUI.class);
        when(ui.getActionBar()).thenReturn(actionBar);

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getHookBus()).thenReturn(scriptModule.getHookBus());
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(api.getPlayerManager()).thenReturn(playerManager);
        when(api.getUIManager()).thenReturn(ui);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        mechanic = mock(ConfiguredMechanic.class);
        new ItemPipelineLoader(plugin, scriptModule).load();
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
    }

    private AbilityDefinition.Builder ability() {
        return new AbilityDefinition.Builder("blink").trigger(AbilityTrigger.RIGHT_CLICK).addMechanic(mechanic);
    }

    @Test
    void fires_runsMechanicsAndStartsTheCooldown() {
        AbilityExecutor.fireAbility(player, ability().cooldown(5).build(), AbilityTrigger.RIGHT_CLICK, target, false);
        verify(mechanic).execute(player, target);
        assertTrue(cooldowns.isOnCooldown("blink"));
    }

    @Test
    void onCooldown_isRefusedWithAMessage() {
        cooldowns.setCooldown("blink", 30);
        AbilityExecutor.fireAbility(player, ability().cooldown(5).build(), AbilityTrigger.RIGHT_CLICK, target, false);
        verify(mechanic, never()).execute(any(), any());
        verify(actionBar).showTemporary(eq(player), startsWith("<red>Ability on cooldown: "), eq(10), eq(2));
    }

    @Test
    void notEnoughMana_isRefusedWithoutSpendingAnything() {
        AbilityExecutor.fireAbility(player, ability().manaCost(500).cooldown(5).build(), AbilityTrigger.RIGHT_CLICK, target, false);
        verify(mechanic, never()).execute(any(), any());
        verify(state, never()).reduceMana(anyDouble());
        assertFalse(cooldowns.isOnCooldown("blink"));
        verify(actionBar).showTemporary(player, "<aqua>Not enough Mana!", 10, 2);
    }

    @Test
    void enoughMana_isSpent() {
        AbilityExecutor.fireAbility(player, ability().manaCost(40).build(), AbilityTrigger.RIGHT_CLICK, target, false);
        verify(state).reduceMana(40.0);
        verify(mechanic).execute(player, target);
    }

    @Test
    void noTargetInRange_isRefusedWithAMessage() {
        when(player.getTargetEntity(anyInt(), eq(false))).thenReturn(null);
        AbilityExecutor.fireAbility(player, ability().targetRange(10).build(), AbilityTrigger.RIGHT_CLICK, null, false);
        verify(mechanic, never()).execute(any(), any());
        verify(actionBar).showTemporary(player, "<red>No target in range!", 10, 2);
    }

    @Test
    void targetInRange_isUsed() {
        when(player.getTargetEntity(10, false)).thenReturn(target);
        AbilityExecutor.fireAbility(player, ability().targetRange(10).build(), AbilityTrigger.RIGHT_CLICK, null, false);
        verify(mechanic).execute(player, target);
    }

    @Test
    void silent_showsNoMessages() {
        cooldowns.setCooldown("blink", 30);
        AbilityExecutor.fireAbility(player, ability().build(), AbilityTrigger.ON_HIT, target, true);
        verify(actionBar, never()).showTemporary(any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void failingConditions_doNothing() {
        AbilityExecutor.fireAbility(player, ability().cooldown(5).conditions(new ConditionGroup(List.of(ctx -> false))).build(),
                AbilityTrigger.RIGHT_CLICK, target, false);
        verify(mechanic, never()).execute(any(), any());
        assertFalse(cooldowns.isOnCooldown("blink"));
    }

    @Test
    void preAbilityInterrupt_skipsMechanicsButCostsStay() {
        scriptModule.getHookBus().registerHook("item:pre_ability", "test-veto", ctx -> StageResult.INTERRUPT);
        AbilityExecutor.fireAbility(player, ability().manaCost(10).cooldown(5).build(), AbilityTrigger.RIGHT_CLICK, target, false);
        verify(mechanic, never()).execute(any(), any());
        verify(state).reduceMana(10.0);
        assertTrue(cooldowns.isOnCooldown("blink"));
    }

    @Test
    void preAbilityHook_seesAbilityAndResolvedTarget() {
        when(player.getTargetEntity(10, false)).thenReturn(target);
        Object[] seen = new Object[3];
        scriptModule.getHookBus().registerHook("item:pre_ability", "test-observer", ctx -> {
            seen[0] = ctx.get("item:ability_id");
            seen[1] = ctx.get("item:ability_trigger");
            seen[2] = ctx.getTarget().orElse(null);
            return StageResult.CONTINUE;
        });
        AbilityExecutor.fireAbility(player, ability().targetRange(10).build(), AbilityTrigger.RIGHT_CLICK, null, false);
        assertEquals("blink", seen[0]);
        assertEquals("RIGHT_CLICK", seen[1]);
        assertSame(target, seen[2]);
    }

    private static String startsWith(String prefix) {
        return argThat(s -> s != null && s.startsWith(prefix));
    }
}
