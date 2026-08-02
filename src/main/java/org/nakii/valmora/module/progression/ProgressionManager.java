package org.nakii.valmora.module.progression;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.progression.event.ProgressionNodeLevelUpEvent;
import org.nakii.valmora.module.progression.event.ProgressionTierUnlockedEvent;
import org.nakii.valmora.module.progression.event.ProgressionTreeResetEvent;
import org.nakii.valmora.module.quest.points.PointsManager;

import java.util.Map;
import java.util.UUID;

public class ProgressionManager {

    private final Valmora plugin;
    private final ProgressionRegistry registry;

    public ProgressionManager(Valmora plugin, ProgressionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public ProgressionRegistry getRegistry() { return registry; }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public int getNodeLevel(UUID playerUuid, String treeId, String nodeId) {
        ValmoraProfile profile = getProfile(playerUuid);
        if (profile == null) return 0;
        Object v = profile.getVariables().get(varKey(treeId, nodeId, "level"));
        return v instanceof Number n ? n.intValue() : 0;
    }

    public int getUnlockedTier(UUID playerUuid, String treeId) {
        ValmoraProfile profile = getProfile(playerUuid);
        if (profile == null) return 0;
        Object v = profile.getVariables().get("progression." + treeId + ".tier");
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** Evaluates the tree's cost-curve expression at the given level (cost to go level -> level+1). */
    public int getNodeCost(String treeId, String nodeId, int level) {
        ProgressionNode node = registry.getTree(treeId).flatMap(t -> t.getNode(nodeId)).orElse(null);
        if (node == null) return Integer.MAX_VALUE;
        return evaluateCostCurve(node.getCostCurve(), level);
    }

    public boolean isNodeUnlocked(UUID playerUuid, String treeId, String nodeId) {
        ProgressionTreeDefinition tree = registry.getTree(treeId).orElse(null);
        if (tree == null) return false;
        ProgressionNode node = tree.getNode(nodeId).orElse(null);
        if (node == null) return false;

        if (getUnlockedTier(playerUuid, treeId) < node.getTierIndex()) return false;

        for (String prereqId : node.getPrerequisiteNodeIds()) {
            if (getNodeLevel(playerUuid, treeId, prereqId) <= 0) return false;
        }
        return true;
    }

    public boolean canLevelUp(Player player, String treeId, String nodeId) {
        ProgressionTreeDefinition tree = registry.getTree(treeId).orElse(null);
        if (tree == null) return false;
        ProgressionNode node = tree.getNode(nodeId).orElse(null);
        if (node == null) return false;

        int currentLevel = getNodeLevel(player.getUniqueId(), treeId, nodeId);
        if (currentLevel >= node.getMaxLevel()) return false;
        if (!isNodeUnlocked(player.getUniqueId(), treeId, nodeId)) return false;

        int cost = evaluateCostCurve(node.getCostCurve(), currentLevel);
        PointsManager pm = ValmoraAPI.getInstance().getPointsManager();
        return pm != null && pm.getPoints(player.getUniqueId(), tree.getLevelCurrencyCategory()) >= cost;
    }

    public boolean canUnlockTier(Player player, String treeId) {
        ProgressionTreeDefinition tree = registry.getTree(treeId).orElse(null);
        if (tree == null) return false;
        int nextTier = getUnlockedTier(player.getUniqueId(), treeId) + 1;
        ProgressionTier tier = tree.getTier(nextTier).orElse(null);
        if (tier == null) return false;

        PointsManager pm = ValmoraAPI.getInstance().getPointsManager();
        return pm != null && pm.getPoints(player.getUniqueId(), tree.getTierCurrencyCategory()) >= tier.getUnlockCost();
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    public void levelUp(Player player, String treeId, String nodeId) {
        if (!canLevelUp(player, treeId, nodeId)) return;
        ProgressionTreeDefinition tree = registry.getTree(treeId).orElse(null);
        ProgressionNode node = tree.getNode(nodeId).orElse(null);
        if (node == null) return;

        ValmoraProfile profile = getProfile(player.getUniqueId());
        if (profile == null) return;

        int currentLevel = getNodeLevel(player.getUniqueId(), treeId, nodeId);
        int cost = evaluateCostCurve(node.getCostCurve(), currentLevel);

        PointsManager pm = ValmoraAPI.getInstance().getPointsManager();
        pm.takePoints(player.getUniqueId(), tree.getLevelCurrencyCategory(), cost);
        addSpent(profile, treeId, tree.getLevelCurrencyCategory(), cost);

        int newLevel = currentLevel + 1;
        profile.getVariables().put(varKey(treeId, nodeId, "level"), newLevel);

        new ProgressionNodeLevelUpEvent(player, treeId, nodeId, newLevel).callEvent();
    }

    public void unlockTier(Player player, String treeId) {
        if (!canUnlockTier(player, treeId)) return;
        ProgressionTreeDefinition tree = registry.getTree(treeId).orElse(null);
        if (tree == null) return;

        ValmoraProfile profile = getProfile(player.getUniqueId());
        if (profile == null) return;

        int nextTierIndex = getUnlockedTier(player.getUniqueId(), treeId) + 1;
        ProgressionTier tier = tree.getTier(nextTierIndex).orElse(null);
        if (tier == null) return;

        PointsManager pm = ValmoraAPI.getInstance().getPointsManager();
        pm.takePoints(player.getUniqueId(), tree.getTierCurrencyCategory(), tier.getUnlockCost());
        addSpent(profile, treeId, tree.getTierCurrencyCategory(), tier.getUnlockCost());

        profile.getVariables().put("progression." + treeId + ".tier", nextTierIndex);

        new ProgressionTierUnlockedEvent(player, treeId, nextTierIndex).callEvent();
    }

    /** Refunds every point ever spent on both currencies in this tree, and zeroes all node/tier progress. */
    public void resetTree(Player player, String treeId) {
        ProgressionTreeDefinition tree = registry.getTree(treeId).orElse(null);
        if (tree == null) return;
        ValmoraProfile profile = getProfile(player.getUniqueId());
        if (profile == null) return;

        Map<String, Object> vars = profile.getVariables();
        PointsManager pm = ValmoraAPI.getInstance().getPointsManager();

        int spentLevel = getSpent(profile, treeId, tree.getLevelCurrencyCategory());
        int spentTier = getSpent(profile, treeId, tree.getTierCurrencyCategory());
        if (spentLevel > 0) pm.addPoints(player.getUniqueId(), tree.getLevelCurrencyCategory(), spentLevel);
        if (spentTier > 0) pm.addPoints(player.getUniqueId(), tree.getTierCurrencyCategory(), spentTier);

        vars.remove(spentKey(treeId, tree.getLevelCurrencyCategory()));
        vars.remove(spentKey(treeId, tree.getTierCurrencyCategory()));
        vars.remove("progression." + treeId + ".tier");
        for (String nodeId : tree.getNodes().keySet()) {
            vars.remove(varKey(treeId, nodeId, "level"));
        }

        new ProgressionTreeResetEvent(player, treeId).callEvent();
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void addSpent(ValmoraProfile profile, String treeId, String currency, int amount) {
        String key = spentKey(treeId, currency);
        int current = getSpent(profile, treeId, currency);
        profile.getVariables().put(key, current + amount);
    }

    private int getSpent(ValmoraProfile profile, String treeId, String currency) {
        Object v = profile.getVariables().get(spentKey(treeId, currency));
        return v instanceof Number n ? n.intValue() : 0;
    }

    private String spentKey(String treeId, String currency) {
        return "progression." + treeId + ".spent." + currency;
    }

    private String varKey(String treeId, String nodeId, String field) {
        return "progression." + treeId + "." + nodeId + "." + field;
    }

    private int evaluateCostCurve(String costCurve, int level) {
        String substituted = costCurve.replace("$level$", String.valueOf(level));
        Expression expr = plugin.getScriptModule().getExpressionParser().parse(substituted);
        Object result = expr.evaluate(new SimpleExecutionContext(null, null, null, new MemoryConfiguration()));
        if (result instanceof Number n) return Math.max(0, n.intValue());
        return 0;
    }

    private ValmoraProfile getProfile(UUID uuid) {
        ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(uuid);
        return vp != null ? vp.getActiveProfile() : null;
    }
}
