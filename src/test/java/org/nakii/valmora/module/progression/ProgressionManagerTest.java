package org.nakii.valmora.module.progression;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.quest.points.PointsManager;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.expression.ExpressionParser;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the generic progression-tree engine (Geomancy's underlying module): cost-curve
 * evaluation, level-up currency deduction, tier/prerequisite gating, and reset refund math.
 */
public class ProgressionManagerTest {

    private ProgressionManager manager;
    private ProgressionRegistry registry;
    private ValmoraProfile profile;
    private UUID playerUuid;
    private Player player;

    // In-memory stand-in for the real PointsManager's profile-variable-backed storage.
    private final Map<String, Integer> points = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        Valmora plugin = mock(Valmora.class);
        ScriptModule scriptModule = mock(ScriptModule.class);
        when(scriptModule.getExpressionParser()).thenReturn(new ExpressionParser());
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        registry = new ProgressionRegistry();
        manager = new ProgressionManager(plugin, registry);

        ProgressionTier tier0 = new ProgressionTier(0, "Novice", 0, List.of("root"));
        ProgressionTier tier1 = new ProgressionTier(1, "Adept", 3, List.of("branch"));
        ProgressionNode root = new ProgressionNode("root", "Root", "", Material.BOOK, 0, 10,
                "floor(5 * pow(1.12, $level$))", List.of(),
                new ProgressionNode.StatBonus("mining_speed", 4.0), null);
        ProgressionNode branch = new ProgressionNode("branch", "Branch", "", Material.BOOK, 1, 5,
                "floor(8 * pow(1.15, $level$))", List.of(),
                new ProgressionNode.StatBonus("mining_fortune", 3.0), null);
        ProgressionTreeDefinition tree = new ProgressionTreeDefinition("geomancy", "Geomancy", "",
                "ferrite_powder", "geomancy_tokens", List.of(tier0, tier1),
                Map.of("root", root, "branch", branch));
        registry.registerTree(tree);

        playerUuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);

        profile = new ValmoraProfile("test"); // constructs StatManager, which needs the provider above

        PlayerManager playerManager = mock(PlayerManager.class);
        ValmoraPlayer vp = mock(ValmoraPlayer.class);
        when(vp.getActiveProfile()).thenReturn(profile);
        when(playerManager.getSession(playerUuid)).thenReturn(vp);
        when(api.getPlayerManager()).thenReturn(playerManager);

        PointsManager pointsManager = mock(PointsManager.class);
        when(pointsManager.getPoints(eq(playerUuid), anyString()))
                .thenAnswer(inv -> points.getOrDefault(inv.getArgument(1, String.class), 0));
        doAnswer(inv -> {
            points.merge(inv.getArgument(1, String.class), inv.getArgument(2, Integer.class), Integer::sum);
            return null;
        }).when(pointsManager).addPoints(eq(playerUuid), anyString(), anyInt());
        doAnswer(inv -> {
            String category = inv.getArgument(1, String.class);
            int amount = inv.getArgument(2, Integer.class);
            int current = points.getOrDefault(category, 0);
            points.put(category, Math.max(0, current - amount));
            return null;
        }).when(pointsManager).takePoints(eq(playerUuid), anyString(), anyInt());
        when(api.getPointsManager()).thenReturn(pointsManager);
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
    public void costCurve_evaluatesFloorPowExpression() {
        assertEquals(5, manager.getNodeCost("geomancy", "root", 0)); // floor(5 * 1.12^0) = 5
        assertEquals(5, manager.getNodeCost("geomancy", "root", 1)); // floor(5 * 1.12^1) = floor(5.6) = 5
        assertEquals(6, manager.getNodeCost("geomancy", "root", 2)); // floor(5 * 1.12^2) = floor(6.272) = 6
    }

    @Test
    public void levelUp_deductsCurrencyAndIncrementsLevel() {
        points.put("ferrite_powder", 100);

        assertTrue(manager.canLevelUp(player, "geomancy", "root"));
        withBukkit(() -> manager.levelUp(player, "geomancy", "root"));

        assertEquals(1, manager.getNodeLevel(playerUuid, "geomancy", "root"));
        assertEquals(95, points.get("ferrite_powder")); // 100 - cost(5)
    }

    @Test
    public void levelUp_refusedWithoutEnoughCurrency() {
        points.put("ferrite_powder", 2); // cost at level 0 is 5

        assertFalse(manager.canLevelUp(player, "geomancy", "root"));
        withBukkit(() -> manager.levelUp(player, "geomancy", "root"));

        assertEquals(0, manager.getNodeLevel(playerUuid, "geomancy", "root"));
        assertEquals(2, points.get("ferrite_powder"));
    }

    @Test
    public void tierGating_blocksNodeUntilTierUnlocked() {
        points.put("ferrite_powder", 1000);
        assertFalse(manager.canLevelUp(player, "geomancy", "branch")); // tier 1 not yet unlocked

        points.put("geomancy_tokens", 3);
        withBukkit(() -> manager.unlockTier(player, "geomancy"));
        assertEquals(1, manager.getUnlockedTier(playerUuid, "geomancy"));

        assertTrue(manager.canLevelUp(player, "geomancy", "branch"));
    }

    @Test
    public void resetTree_refundsExactlyWhatWasSpent() {
        points.put("ferrite_powder", 100);
        withBukkit(() -> {
            manager.levelUp(player, "geomancy", "root"); // spends 5
            manager.levelUp(player, "geomancy", "root"); // spends 5 (cost at level 1)
        });
        int afterSpending = points.get("ferrite_powder");
        assertEquals(90, afterSpending);

        withBukkit(() -> manager.resetTree(player, "geomancy"));

        assertEquals(0, manager.getNodeLevel(playerUuid, "geomancy", "root"));
        assertEquals(100, points.get("ferrite_powder"));
    }
}
