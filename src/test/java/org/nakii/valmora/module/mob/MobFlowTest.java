package org.nakii.valmora.module.mob;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.mob.ability.MobAbility;
import org.nakii.valmora.module.mob.ability.MobAbilityTrigger;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.skill.SkillManager;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.StatModule;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.Keys;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Characterization tests for boss ability firing ({@link BossController}) and custom-mob death
 * rewards ({@link MobDeathListener}) — written against the pre-flow implementation and kept
 * unchanged after the move to {@code mob_pipeline.yml}'s default {@code cast}/{@code death} flows
 * (docs/PIPELINE_FLOWS_PLAN.md §6 steps 1 and 7).
 */
class MobFlowTest {

    private Valmora plugin;
    private YamlConfiguration config;
    private ScriptModule scriptModule;
    private MobManager mobManager;
    private World world;
    private LivingEntity boss;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;

    @BeforeEach
    void setUp() throws Exception {
        Keys.MOB_ID_KEY = new NamespacedKey("valmora", "valmora_mob_id");
        File dataFolder = Files.createTempDirectory("valmora-mob").toFile();
        plugin = mock(Valmora.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MobFlowTest"));
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getHookBus()).thenReturn(scriptModule.getHookBus());
        when(api.getScriptModule()).thenReturn(scriptModule);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        mobManager = mock(MobManager.class);
        when(plugin.getMobManager()).thenReturn(mobManager);
        when(mobManager.getBossController()).thenReturn(mock(BossController.class));

        world = mock(World.class);
        when(world.getPlayers()).thenReturn(new ArrayList<>());
        boss = mock(LivingEntity.class);
        when(boss.getUniqueId()).thenReturn(UUID.randomUUID());
        when(boss.getWorld()).thenReturn(world);
        Location bossLoc = new Location(world, 0, 64, 0);
        when(boss.getLocation()).thenReturn(bossLoc);
        when(boss.getHealth()).thenReturn(50.0);

        new MobPipelineLoader(plugin, scriptModule).load();
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
    }

    // ------------------------------------------------------------------ boss abilities

    private MobDefinition bossDefinition(MobAbility... abilities) {
        MobDefinition def = mock(MobDefinition.class);
        when(def.isBoss()).thenReturn(true);
        when(def.getId()).thenReturn("golem");
        when(def.getLevel()).thenReturn(10);
        when(def.getAbilities()).thenReturn(List.of(abilities));
        return def;
    }

    private Player playerAt(double x) {
        Player p = mock(Player.class);
        when(p.getLocation()).thenReturn(new Location(world, x, 64, 0));
        when(p.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        return p;
    }

    @Test
    void spawnAbility_runsMechanicsOnTheNearestPlayerInRange() {
        ConfiguredMechanic mechanic = mock(ConfiguredMechanic.class);
        Player near = playerAt(3);
        Player far = playerAt(30);
        when(world.getPlayers()).thenReturn(List.of(far, near));
        MobAbility slam = new MobAbility.Builder("slam").trigger(MobAbilityTrigger.ON_SPAWN)
                .targetRange(10).addMechanic(mechanic).build();

        new BossController(plugin).register(boss, bossDefinition(slam));

        verify(mechanic).execute(boss, near);
    }

    @Test
    void announce_isSentToPlayersInTheAnnounceRadius() {
        Player near = playerAt(5);
        Player far = playerAt(100);
        when(world.getPlayers()).thenReturn(List.of(near, far));
        MobAbility roar = new MobAbility.Builder("roar").trigger(MobAbilityTrigger.ON_SPAWN)
                .announce("<red>The golem roars!").build();

        new BossController(plugin).register(boss, bossDefinition(roar));

        verify(near).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(far, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void cooldown_blocksARepeatFiring() {
        ConfiguredMechanic mechanic = mock(ConfiguredMechanic.class);
        MobAbility smash = new MobAbility.Builder("smash").trigger(MobAbilityTrigger.ON_ATTACK)
                .cooldownSeconds(30).addMechanic(mechanic).build();
        BossController controller = new BossController(plugin);
        controller.register(boss, bossDefinition(smash));
        LivingEntity victim = mock(LivingEntity.class);

        controller.onAttack(boss, victim);
        controller.onAttack(boss, victim);

        verify(mechanic, times(1)).execute(boss, victim);
    }

    @Test
    void preAbilityInterrupt_skipsMechanicsButTheCooldownStays() {
        ConfiguredMechanic mechanic = mock(ConfiguredMechanic.class);
        MobAbility smash = new MobAbility.Builder("smash").trigger(MobAbilityTrigger.ON_ATTACK)
                .cooldownSeconds(30).addMechanic(mechanic).build();
        BossController controller = new BossController(plugin);
        controller.register(boss, bossDefinition(smash));
        LivingEntity victim = mock(LivingEntity.class);
        boolean[] veto = {true};
        Object[] seen = new Object[2];
        scriptModule.getHookBus().registerHook("mob:pre_ability", "test-veto", ctx -> {
            seen[0] = ctx.get("mob:ability_id");
            seen[1] = ctx.get("mob:id");
            return veto[0] ? StageResult.INTERRUPT : StageResult.CONTINUE;
        });

        controller.onAttack(boss, victim);
        veto[0] = false;
        controller.onAttack(boss, victim); // still on cooldown from the vetoed attempt

        verify(mechanic, never()).execute(any(), any());
        assertEquals("smash", seen[0]);
        assertEquals("golem", seen[1]);
    }

    // ------------------------------------------------------------------ mob death

    private EntityDeathEvent deathOf(MobDefinition def, Player killer, List<ItemStack> drops) {
        LivingEntity mob = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(Keys.MOB_ID_KEY, PersistentDataType.STRING)).thenReturn("zombie_brute");
        when(mob.getPersistentDataContainer()).thenReturn(pdc);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.getKiller()).thenReturn(killer);
        when(mob.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(mobManager.getMobDefinition("zombie_brute")).thenReturn(def);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);
        return event;
    }

    private MobDefinition rewardingMob(int xp, int gold, LootEntry... loot) {
        MobDefinition def = mock(MobDefinition.class);
        when(def.getId()).thenReturn("zombie_brute");
        when(def.getLevel()).thenReturn(5);
        when(def.getXpReward()).thenReturn(xp);
        when(def.getGoldReward()).thenReturn(gold);
        when(def.getLootTable()).thenReturn(new LootTable(List.of(loot)));
        return def;
    }

    private record Killer(Player player, SkillManager skills, EconomyService economy) {}

    private Killer killerWithProfile() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("hunter");
        ValmoraProfile profile = mock(ValmoraProfile.class);
        StatManager stats = mock(StatManager.class);
        SkillManager skills = mock(SkillManager.class);
        when(profile.getStatManager()).thenReturn(stats);
        when(profile.getSkillManager()).thenReturn(skills);
        ValmoraPlayer session = mock(ValmoraPlayer.class);
        when(session.getActiveProfile()).thenReturn(profile);
        PlayerManager players = mock(PlayerManager.class);
        when(players.getSession(any())).thenReturn(session);
        when(plugin.getPlayerManager()).thenReturn(players);
        StatModule statModule = mock(StatModule.class);
        SystemStats systemStats = mock(SystemStats.class);
        when(systemStats.getLuck()).thenReturn("luck");
        when(statModule.getSystemStats()).thenReturn(systemStats);
        when(plugin.getStatModule()).thenReturn(statModule);
        EconomyService economy = mock(EconomyService.class);
        when(plugin.getEconomy()).thenReturn(economy);
        return new Killer(player, skills, economy);
    }

    @Test
    void death_grantsXpGoldAndLoot() {
        Killer killer = killerWithProfile();
        LootEntry loot = mock(LootEntry.class);
        ItemStack drop = mock(ItemStack.class);
        when(loot.getChance()).thenReturn(1.0);
        when(loot.createDroppedItem()).thenReturn(drop);
        List<ItemStack> drops = new ArrayList<>();

        new MobDeathListener(plugin).onMobDeath(deathOf(rewardingMob(25, 7, loot), killer.player(), drops));

        verify(killer.skills()).addXp(eq("combat"), eq(25.0), eq(killer.player()));
        verify(killer.economy()).addCoins(killer.player(), 7.0);
        assertEquals(List.of(drop), drops);
    }

    @Test
    void death_combatSkillIdComesFromConfig() {
        config.set("mobs.combat-skill-id", "slaying");
        Killer killer = killerWithProfile();

        new MobDeathListener(plugin).onMobDeath(deathOf(rewardingMob(3, 0), killer.player(), new ArrayList<>()));

        verify(killer.skills()).addXp(eq("slaying"), eq(3.0), any());
        verify(killer.economy(), never()).addCoins(any(), anyDouble());
    }

    @Test
    void deathWithoutKiller_stillDropsLootButGrantsNothing() {
        LootEntry loot = mock(LootEntry.class);
        ItemStack drop = mock(ItemStack.class);
        when(loot.getChance()).thenReturn(1.0);
        when(loot.createDroppedItem()).thenReturn(drop);
        List<ItemStack> drops = new ArrayList<>();
        boolean[] hookRan = {false};
        scriptModule.getHookBus().registerHook("combat:on_death", "test", ctx -> {
            hookRan[0] = true;
            return StageResult.CONTINUE;
        });

        new MobDeathListener(plugin).onMobDeath(deathOf(rewardingMob(25, 7, loot), null, drops));

        assertEquals(List.of(drop), drops);
        assertFalse(hookRan[0], "combat:on_death only fires when there's a killer");
    }

    @Test
    void onDeathHook_seesTheMob() {
        Killer killer = killerWithProfile();
        Object[] seen = new Object[2];
        scriptModule.getHookBus().registerHook("combat:on_death", "test", ctx -> {
            seen[0] = ctx.get("mob:id");
            seen[1] = ctx.get("mob:level");
            return StageResult.CONTINUE;
        });

        new MobDeathListener(plugin).onMobDeath(deathOf(rewardingMob(1, 0), killer.player(), new ArrayList<>()));

        assertEquals("zombie_brute", seen[0]);
        assertEquals(5, seen[1]);
    }

    @Test
    void unknownMob_isIgnored() {
        EntityDeathEvent event = deathOf(null, null, new ArrayList<>());
        assertDoesNotThrow(() -> new MobDeathListener(plugin).onMobDeath(event));
        verify(event, never()).getDrops();
    }

    @SuppressWarnings("unused")
    private static String anyStr() {
        return anyString();
    }
}
