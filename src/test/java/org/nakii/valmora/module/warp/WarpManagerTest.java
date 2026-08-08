package org.nakii.valmora.module.warp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.item.CooldownManager;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.skill.SkillDefinition;
import org.nakii.valmora.module.skill.SkillManager;
import org.nakii.valmora.module.skill.SkillModule;
import org.nakii.valmora.module.skill.SkillRegistry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers WarpManager.isUnlocked (tag/skill unlock conditions) and the teleport() gating chain
 * added 2026-08-07 (permission -> cooldown -> cost -> warmup) — previously zero coverage for the
 * warp module (docs/IMPLEMENTATION_BACKLOG.md "Add unit tests for untested modules").
 */
class WarpManagerTest {

    private Valmora plugin;
    private PlayerManager playerManager;
    private ValmoraProfile profile;
    private Player player;
    private UUID uuid;
    private WarpManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        playerManager = mock(PlayerManager.class);
        when(plugin.getPlayerManager()).thenReturn(playerManager);
        // Valmora implements ValmoraAPI itself; ValmoraProfile's StatManager reads the stat
        // registry off the static ValmoraAPI singleton at construction time.
        when(plugin.getStatRegistry()).thenReturn(new org.nakii.valmora.module.stat.StatRegistry());
        ValmoraAPI.setProvider(plugin);

        profile = new ValmoraProfile("Test");
        uuid = UUID.randomUUID();
        ValmoraPlayer vPlayer = new ValmoraPlayer(uuid);
        vPlayer.addProfile(profile);
        when(playerManager.getSession(uuid)).thenReturn(vPlayer);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        manager = new WarpManager(plugin);
    }

    private WarpDefinition warp(String id, String unlockCondition) {
        return new WarpDefinition(id, "Display " + id, "world", 1, 64, 1, 0f, 0f, unlockCondition, List.of());
    }

    // --- isUnlocked ---

    @Test
    void isUnlocked_nullOrAlwaysCondition_true() {
        assertTrue(manager.isUnlocked(player, warp("a", null)));
        assertTrue(manager.isUnlocked(player, warp("a", "always")));
        assertTrue(manager.isUnlocked(player, warp("a", "ALWAYS")));
    }

    @Test
    void isUnlocked_tagCondition_matchesProfileTags() {
        profile.getTags().add("vip");
        assertTrue(manager.isUnlocked(player, warp("a", "tag:vip")));
        assertFalse(manager.isUnlocked(player, warp("a", "tag:nope")));
    }

    @Test
    void isUnlocked_noSessionOrProfile_false() {
        when(playerManager.getSession(uuid)).thenReturn(null);
        assertFalse(manager.isUnlocked(player, warp("a", "tag:vip")));
    }

    @Test
    void isUnlocked_skillCondition_meetsRequiredLevel() {
        SkillModule skillModule = mock(SkillModule.class);
        SkillManager skillManagerPlugin = mock(SkillManager.class);
        when(plugin.getSkillModule()).thenReturn(skillModule);
        when(plugin.getSkillManager()).thenReturn(skillManagerPlugin);

        SkillRegistry moduleRegistry = new SkillRegistry();
        SkillDefinition mining = new SkillDefinition("mining", "Mining", "Mining skill",
                org.bukkit.Material.DIAMOND_PICKAXE, 60, "default", null, null, null);
        moduleRegistry.register("mining", mining);
        when(skillModule.getSkillRegistry()).thenReturn(moduleRegistry);
        when(skillManagerPlugin.getSkillRegistry()).thenReturn(moduleRegistry);

        // "default" curve: xp 20 -> level 2 (see SkillRegistryTest).
        profile.getSkillManager().setXp("mining", 20.0);

        assertTrue(manager.isUnlocked(player, warp("a", "skill:mining:2")));
        assertFalse(manager.isUnlocked(player, warp("a", "skill:mining:10")));
    }

    @Test
    void isUnlocked_skillCondition_malformedOrUnknownSkill_false() {
        SkillModule skillModule = mock(SkillModule.class);
        when(plugin.getSkillModule()).thenReturn(skillModule);
        when(skillModule.getSkillRegistry()).thenReturn(new SkillRegistry());

        assertFalse(manager.isUnlocked(player, warp("a", "skill:mining"))); // missing level part
        assertFalse(manager.isUnlocked(player, warp("a", "skill:mining:abc"))); // non-numeric level
        assertFalse(manager.isUnlocked(player, warp("a", "skill:unknown:1"))); // unregistered skill id
    }

    @Test
    void isUnlocked_unrecognizedConditionPrefix_false() {
        assertFalse(manager.isUnlocked(player, warp("a", "bogus:whatever")));
    }

    // --- teleport() gating chain ---

    @Test
    void teleport_locked_sendsMessageAndStops() {
        WarpDefinition warp = warp("locked", "tag:vip");
        manager.teleport(player, warp);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleport_missingPermission_sendsMessageAndStops() {
        WarpDefinition warp = new WarpDefinition("a", "A", "world", 1, 64, 1, 0f, 0f, "always", List.of(),
                0.0, 0, 0, "valmora.warp.a");
        when(player.hasPermission("valmora.warp.a")).thenReturn(false);

        manager.teleport(player, warp);

        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleport_noProfile_sendsMessageAndStops() {
        when(playerManager.getSession(uuid)).thenReturn(new ValmoraPlayer(uuid)); // no profile added
        manager.teleport(player, warp("a", "always"));
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleport_onCooldown_sendsMessageAndStops() {
        WarpDefinition warp = new WarpDefinition("a", "A", "world", 1, 64, 1, 0f, 0f, "always", List.of(),
                0.0, 60, 0, null);
        profile.getCooldownManager().setCooldown("warp:a", 60);

        manager.teleport(player, warp);

        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleport_insufficientCoins_sendsMessageAndStops() {
        WarpDefinition warp = new WarpDefinition("a", "A", "world", 1, 64, 1, 0f, 0f, "always", List.of(),
                500.0, 0, 0, null);
        EconomyService economy = mock(EconomyService.class);
        when(plugin.getEconomy()).thenReturn(economy);
        when(economy.hasCoins(player, 500.0)).thenReturn(false);

        manager.teleport(player, warp);

        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleport_worldNotLoaded_sendsMessageAndStops() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);

            manager.teleport(player, warp("a", "always"));

            verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
            verify(player, never()).teleportAsync(any());
        }
    }

    @Test
    void teleport_noWarmup_teleportsImmediatelyAndChargesCostAndSetsCooldown() {
        WarpDefinition warp = new WarpDefinition("a", "A", "world", 5, 65, 5, 0f, 0f, "always", List.of(),
                100.0, 30, 0, null);
        EconomyService economy = mock(EconomyService.class);
        when(plugin.getEconomy()).thenReturn(economy);
        when(economy.hasCoins(player, 100.0)).thenReturn(true);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            manager.teleport(player, warp);

            verify(player).teleportAsync(any(Location.class));
            verify(economy).removeCoins(player, 100.0);
            assertTrue(profile.getCooldownManager().isOnCooldown("warp:a"));
        }
    }

    @Test
    void teleport_failedTeleport_doesNotChargeCostOrSetCooldown() {
        WarpDefinition warp = new WarpDefinition("a", "A", "world", 5, 65, 5, 0f, 0f, "always", List.of(),
                100.0, 30, 0, null);
        EconomyService economy = mock(EconomyService.class);
        when(plugin.getEconomy()).thenReturn(economy);
        when(economy.hasCoins(player, 100.0)).thenReturn(true);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(false));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            manager.teleport(player, warp);

            verify(economy, never()).removeCoins(any(), anyDouble());
            assertFalse(profile.getCooldownManager().isOnCooldown("warp:a"));
        }
    }

    // --- getWarpByPad ---

    @Test
    void getWarpByPad_matchesRegisteredPad() {
        WarpDefinition warp = new WarpDefinition("a", "A", "world", 1, 64, 1, 0f, 0f, "always",
                List.of(new int[]{5, 64, 5}));
        manager.getRegistry().register("a", warp);

        assertTrue(manager.getWarpByPad("world", 5, 64, 5).isPresent());
        assertEquals("a", manager.getWarpByPad("world", 5, 64, 5).get().getId());
    }

    @Test
    void getWarpByPad_wrongWorldOrCoords_empty() {
        WarpDefinition warp = new WarpDefinition("a", "A", "world", 1, 64, 1, 0f, 0f, "always",
                List.of(new int[]{5, 64, 5}));
        manager.getRegistry().register("a", warp);

        assertTrue(manager.getWarpByPad("other_world", 5, 64, 5).isEmpty());
        assertTrue(manager.getWarpByPad("world", 1, 1, 1).isEmpty());
    }
}
