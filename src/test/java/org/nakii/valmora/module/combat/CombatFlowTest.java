package org.nakii.valmora.module.combat;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.mob.BossController;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.script.ScriptModule;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * Characterization tests for {@link CombatListener}'s hit handling — written against the pre-flow
 * implementation and kept unchanged after the move to {@code combat_pipeline.yml}'s default
 * {@code entity_hit}/{@code environment_damage} flows (docs/PIPELINE_FLOWS_PLAN.md §6 steps 1 and 7).
 * Uses plain (non-player, non-Valmora) entities so the damage math is the config fallback path.
 */
class CombatFlowTest {

    private YamlConfiguration config;
    private ScriptModule scriptModule;
    private MobManager mobManager;
    private BossController bosses;
    private DamageIndicatorManager indicators;
    private LivingEntity attacker;
    private LivingEntity victim;
    private double victimHealth;
    private CombatListener listener;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;

    @BeforeEach
    void setUp() throws Exception {
        File dataFolder = Files.createTempDirectory("valmora-combat").toFile();
        Valmora plugin = mock(Valmora.class);
        config = new YamlConfiguration();
        config.set("combat.fallback-base-damage", 10.0);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CombatFlowTest"));
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        mobManager = mock(MobManager.class);
        bosses = mock(BossController.class);
        when(mobManager.getBossController()).thenReturn(bosses);
        indicators = mock(DamageIndicatorManager.class);
        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getHookBus()).thenReturn(scriptModule.getHookBus());
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(api.getMobManager()).thenReturn(mobManager);
        when(api.getDamageIndicatorManager()).thenReturn(indicators);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        attacker = mock(LivingEntity.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getName()).thenReturn("zombie");
        victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getName()).thenReturn("pig");
        when(victim.getLocation()).thenReturn(mock(Location.class));
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
        victimHealth = 100.0;
        when(victim.getHealth()).thenAnswer(inv -> victimHealth);
        doAnswer(inv -> { victimHealth = inv.getArgument(0); return null; }).when(victim).setHealth(anyDouble());

        listener = new CombatListener(plugin);
        new CombatPipelineLoader(plugin, scriptModule).load();
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
    }

    private EntityDamageByEntityEvent hit(Entity damager) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(damager);
        when(event.getDamage()).thenReturn(5.0);
        when(event.getDamageSource()).thenReturn(mock(DamageSource.class));
        return event;
    }

    @Test
    void meleeHit_replacesVanillaDamageWithValmoraDamage() {
        EntityDamageByEntityEvent event = hit(attacker);

        listener.onEntityDamageByEntity(event);

        verify(event).setDamage(0);
        assertEquals(90.0, victimHealth);
        verify(victim).setNoDamageTicks(20);
        verify(indicators).spawnIndicator(argThat(r -> r.getFinalDamage() == 10.0 && r.getDamageType() == DamageType.MELEE));
    }

    @Test
    void iFrames_cancelTheHit() {
        when(victim.getNoDamageTicks()).thenReturn(15);
        EntityDamageByEntityEvent event = hit(attacker);

        listener.onEntityDamageByEntity(event);

        verify(event).setCancelled(true);
        assertEquals(100.0, victimHealth);
    }

    @Test
    void nonLivingDamager_isLeftToVanilla() {
        EntityDamageByEntityEvent event = hit(mock(Entity.class));

        listener.onEntityDamageByEntity(event);

        verify(event, never()).setDamage(anyDouble());
        assertEquals(100.0, victimHealth);
    }

    @Test
    void projectileHit_usesTheShooterAndProjectileType() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);

        listener.onEntityDamageByEntity(hit(arrow));

        verify(indicators).spawnIndicator(argThat(r -> r.getDamageType() == DamageType.PROJECTILE && r.getAttacker() == attacker));
    }

    @Test
    void preDamageInterrupt_zeroesTheHitWithoutApplyingAnything() {
        scriptModule.getHookBus().registerHook("combat:pre_damage", "test-veto", ctx -> StageResult.INTERRUPT);
        EntityDamageByEntityEvent event = hit(attacker);

        listener.onEntityDamageByEntity(event);

        verify(event).setDamage(0);
        assertEquals(100.0, victimHealth);
        verify(indicators, never()).spawnIndicator(any());
    }

    @Test
    void postCalculationInterrupt_preventsTheHitLanding() {
        Object[] seen = new Object[2];
        scriptModule.getHookBus().registerHook("combat:post_calculation", "test-veto", ctx -> {
            seen[0] = ctx.get("dmg:final_damage");
            seen[1] = ctx.get("dmg:damage_type");
            return StageResult.INTERRUPT;
        });

        listener.onEntityDamageByEntity(hit(attacker));

        assertEquals(10.0, seen[0]);
        assertEquals("MELEE", seen[1]);
        assertEquals(100.0, victimHealth);
        verify(indicators, never()).spawnIndicator(any());
    }

    @Test
    void multiplyDamageAtPreDamage_scalesTheHit() {
        scriptModule.getHookBus().registerHook("combat:pre_damage", "test-buff", ctx -> {
            ctx.set("dmg:pipeline_multiplier", 2.0);
            return StageResult.CONTINUE;
        });

        listener.onEntityDamageByEntity(hit(attacker));

        assertEquals(80.0, victimHealth);
    }

    @Test
    void laterHooksRunAfterTheHitLands() {
        StringBuilder order = new StringBuilder();
        scriptModule.getHookBus().registerHook("combat:post_application", "a", ctx -> { order.append("A"); return StageResult.INTERRUPT; });
        scriptModule.getHookBus().registerHook("combat:on_dmg_dealt", "b", ctx -> { order.append("B"); return StageResult.CONTINUE; });

        listener.onEntityDamageByEntity(hit(attacker));

        assertEquals("AB", order.toString(), "an interrupt at post_application must not stop later points");
        assertEquals(90.0, victimHealth);
    }

    @Test
    void bossTriggersFireForTrackedBosses() {
        when(bosses.isTracked(attacker.getUniqueId())).thenReturn(true);
        when(bosses.isTracked(victim.getUniqueId())).thenReturn(true);

        listener.onEntityDamageByEntity(hit(attacker));

        verify(bosses).onAttack(attacker, victim);
        verify(bosses).onDamaged(victim, attacker);
    }

    // ------------------------------------------------------------------ environment

    private EntityDamageEvent envDamage(EntityDamageEvent.DamageCause cause, double amount) {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(amount);
        when(event.getCause()).thenReturn(cause);
        return event;
    }

    @Test
    void environmentDamage_isScaledAndMappedToADamageType() {
        EntityDamageEvent event = envDamage(EntityDamageEvent.DamageCause.FALL, 2.0);

        listener.onEntityDamage(event);

        verify(event).setDamage(0);
        assertEquals(90.0, victimHealth);
        verify(indicators).spawnIndicator(argThat(r -> r.getDamageType() == DamageType.FALL && r.getFinalDamage() == 10.0));
    }

    @Test
    void environmentDamage_causeMappingFromConfig() {
        config.set("combat.cause-mapping.FALL", "MAGIC");

        listener.onEntityDamage(envDamage(EntityDamageEvent.DamageCause.FALL, 2.0));

        verify(indicators).spawnIndicator(argThat(r -> r.getDamageType() == DamageType.MAGIC));
    }

    @Test
    void environmentDamage_zeroIsIgnored() {
        EntityDamageEvent event = envDamage(EntityDamageEvent.DamageCause.FALL, 0.0);
        listener.onEntityDamage(event);
        verify(event, never()).setDamage(anyDouble());
    }

    @Test
    void environmentDamage_iFramesCancel() {
        when(victim.getNoDamageTicks()).thenReturn(15);
        EntityDamageEvent event = envDamage(EntityDamageEvent.DamageCause.FALL, 2.0);
        listener.onEntityDamage(event);
        verify(event).setCancelled(true);
        assertEquals(100.0, victimHealth);
    }
}
