package org.nakii.valmora.module.skill;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("skill")
@Tag("integration")
class SkillManagerXpTest {

    private SkillRegistry registry;
    private Player player;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(null);
    }

    private SkillDefinition makeSkill(String id, int maxLevel, CompiledEvent perLevel, Map<Integer, CompiledEvent> milestones) {
        return new SkillDefinition(id, id, "", null, maxLevel, "default", null, perLevel, milestones);
    }

    private void withBukkit(Runnable action) {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            Server server = mock(Server.class);
            PluginManager pm = mock(PluginManager.class);
            mockedBukkit.when(Bukkit::getServer).thenReturn(server);
            mockedBukkit.when(Bukkit::getPluginManager).thenReturn(pm);
            when(server.getPluginManager()).thenReturn(pm);
            action.run();
        }
    }

    @Test
    void testAddXp_belowThreshold_xpAccumulates() {
        SkillDefinition def = makeSkill("mining", 60, null, null);
        registry.registerSkill(def);
        SkillManager manager = new SkillManager(registry);

        withBukkit(() -> manager.addXp("mining", 5, player));

        assertEquals(5.0, manager.getXp("mining"), 1e-9);
    }

    @Test
    void testAddXp_exactThreshold_levelUpToLevel1() {
        CompiledEvent reward = mock(CompiledEvent.class);
        SkillDefinition def = makeSkill("mining", 60, reward, null);
        registry.registerSkill(def);
        SkillManager manager = new SkillManager(registry);

        // Threshold for level 1 is 10 XP
        withBukkit(() -> manager.addXp("mining", 10, player));

        assertEquals(1, manager.getLevel("mining"));
        verify(reward, times(1)).execute(any(ExecutionContext.class));
    }

    @Test
    void testAddXp_multiLevelJump_rewardsFireForEachLevel() {
        CompiledEvent reward = mock(CompiledEvent.class);
        SkillDefinition def = makeSkill("mining", 60, reward, null);
        registry.registerSkill(def);
        SkillManager manager = new SkillManager(registry);

        // Add enough XP to jump from 0 to level 3 (thresholds: 10, 20, 50)
        withBukkit(() -> manager.addXp("mining", 50, player));

        assertEquals(3, manager.getLevel("mining"));
        // Reward fires for levels 1, 2, and 3
        verify(reward, times(3)).execute(any(ExecutionContext.class));
    }

    @Test
    void testAddXp_paramLevelContextVariable_setCorrectly() {
        ArgumentCaptor<ExecutionContext> contextCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        CompiledEvent reward = mock(CompiledEvent.class);
        SkillDefinition def = makeSkill("mining", 60, reward, null);
        registry.registerSkill(def);
        SkillManager manager = new SkillManager(registry);

        // Jump from 0 to level 2 (XP 20 crosses thresholds 10 and 20)
        withBukkit(() -> manager.addXp("mining", 20, player));

        // Both level-up calls share the same MemoryConfiguration params object;
        // after all iterations the params reflect the final level (2).
        verify(reward, times(2)).execute(contextCaptor.capture());
        assertEquals(2, contextCaptor.getAllValues().get(0).getParams().getInt("level"));
        assertEquals(2, contextCaptor.getAllValues().get(1).getParams().getInt("level"));
    }

    @Test
    void testAddXp_milestoneReward_firesAtCorrectLevel() {
        CompiledEvent perLevel = mock(CompiledEvent.class);
        CompiledEvent milestone5 = mock(CompiledEvent.class);
        SkillDefinition def = makeSkill("mining", 60, perLevel, Map.of(5, milestone5));
        registry.registerSkill(def);
        SkillManager manager = new SkillManager(registry);

        // Thresholds: 10,20,50,100,200 → level 5 requires 200 XP
        withBukkit(() -> manager.addXp("mining", 200, player));

        assertEquals(5, manager.getLevel("mining"));
        verify(milestone5, times(1)).execute(any(ExecutionContext.class));
        verify(perLevel, times(5)).execute(any(ExecutionContext.class));
    }

    @Test
    void testAddXp_atMaxLevel_xpNotAdded() {
        SkillDefinition def = makeSkill("mining", 2, null, null);
        registry.registerSkill(def);
        SkillManager manager = new SkillManager(registry);

        // Level 2 requires 20 XP
        withBukkit(() -> {
            manager.addXp("mining", 20, player);
            // Already at max level; adding more should be a no-op
            manager.addXp("mining", 9999, player);
        });

        assertEquals(2, manager.getLevel("mining"));
        assertEquals(20.0, manager.getXp("mining"), 1e-9);
    }

    @Test
    void testAddXp_keyNormalized_toLowercase() {
        SkillDefinition def = makeSkill("mining", 60, null, null);
        registry.registerSkill(def);
        SkillManager manager = new SkillManager(registry);

        withBukkit(() -> manager.addXp("MINING", 5, player));

        assertEquals(5.0, manager.getXp("mining"), 1e-9);
    }

    @Test
    void testSetXp_getXp_roundTrip() {
        SkillManager manager = new SkillManager(registry);
        manager.setXp("combat", 1234.5);
        assertEquals(1234.5, manager.getXp("combat"), 1e-9);
    }

    @Test
    void testLoadData_normalizesKeysToLowercase() {
        SkillManager manager = new SkillManager(registry);
        manager.loadData(Map.of("COMBAT", 500.0, "Mining", 200.0));
        assertEquals(500.0, manager.getXp("combat"), 1e-9);
        assertEquals(200.0, manager.getXp("mining"), 1e-9);
    }

    @Test
    void testAddXp_unknownSkillId_isNoOp() {
        SkillManager manager = new SkillManager(registry);
        withBukkit(() -> manager.addXp("unknown_skill", 100, player));
        assertEquals(0.0, manager.getXp("unknown_skill"), 1e-9);
    }
}
