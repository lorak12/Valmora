package org.nakii.valmora.module.resource;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.zone.ResourceStage;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneResourceConfig;
import org.nakii.valmora.module.zone.ZoneResourceDrop;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ResourceManager {

    private final Valmora plugin;
    private final Map<String, ResourceTracker> trackedBlocks = new HashMap<>();

    public ResourceManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public boolean isTrackedResource(Location loc) {
        return trackedBlocks.containsKey(locationKey(loc));
    }

    /**
     * Returns true if the break event should suppress vanilla drops.
     * Handles multi-stage progression: each mine advances the block to the next stage material
     * until the final stage, after which the regen timer restores the original block.
     */
    public boolean handleBlockBreak(Player player, Block block) {
        String key = locationKey(block.getLocation());
        ResourceTracker tracker = trackedBlocks.get(key);

        ZoneResourceConfig config;
        int stageIndex;
        Material originalMaterial;

        if (tracker != null) {
            if (tracker.stageIndex >= tracker.config.getStageCount()) return true; // depleted, awaiting regen
            config = tracker.config;
            stageIndex = tracker.stageIndex;
            originalMaterial = tracker.originalMaterial;
        } else {
            ZoneDefinition zone = plugin.getZoneManager().getZoneAt(block.getLocation()).orElse(null);
            if (zone == null) return false;
            config = zone.getResourceBlocks().get(block.getType());
            if (config == null) return false;
            stageIndex = 0;
            originalMaterial = block.getType();
        }

        ResourceStage stage = config.getStage(stageIndex);
        double miningFortune = getPlayerMiningFortune(player);

        for (ZoneResourceDrop drop : stage.getDrops()) {
            if (Math.random() < drop.getChance()) {
                int amount = applyFortune(drop.rollAmount(), miningFortune);
                ItemStack item = createItem(drop.getItemId(), amount);
                if (item != null) player.getInventory().addItem(item);
            }
        }

        boolean isLastStage = (stageIndex == config.getStageCount() - 1);
        Material nextMaterial = stage.getNextMaterial() != null ? stage.getNextMaterial() : Material.AIR;

        // Cancel any existing regen timer before scheduling a new one
        if (tracker != null && tracker.regenTask != null) tracker.regenTask.cancel();

        final Material nextMat = nextMaterial;
        final Material finalOriginal = originalMaterial;
        plugin.getServer().getScheduler().runTask(plugin, () -> block.setType(nextMat, false));

        BukkitTask regenTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            block.setType(finalOriginal, false);
            trackedBlocks.remove(key);
        }, config.getRegenDelayTicks());

        int depletedIndex = config.getStageCount(); // past end = depleted sentinel
        int nextStageIndex = isLastStage ? depletedIndex : stageIndex + 1;

        if (tracker == null) {
            trackedBlocks.put(key, new ResourceTracker(originalMaterial, config, block.getLocation(), nextStageIndex, regenTask));
        } else {
            tracker.stageIndex = nextStageIndex;
            tracker.regenTask = regenTask;
        }

        return true;
    }

    public void cancelAll() {
        for (ResourceTracker tracker : trackedBlocks.values()) {
            if (tracker.regenTask != null) tracker.regenTask.cancel();
            // Restore the block immediately so the world isn't left in a broken state
            if (tracker.location.getWorld() != null) {
                tracker.location.getBlock().setType(tracker.originalMaterial, false);
            }
        }
        trackedBlocks.clear();
    }

    private double getPlayerMiningFortune(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return 0.0;
        var profile = session.getActiveProfile();
        if (profile == null) return 0.0;
        return profile.getStatManager().getStat(ValmoraAPI.getInstance().getSystemStats().getMiningFortune());
    }

    private int applyFortune(int baseAmount, double miningFortune) {
        if (miningFortune <= 0) return baseAmount;
        double multiplier = 1.0 + miningFortune / 100.0;
        return (int) Math.max(baseAmount, Math.round(baseAmount * multiplier));
    }

    private ItemStack createItem(String itemId, int amount) {
        try {
            var stack = plugin.getItemManager().getItemRegistry().createItemStack(itemId.toLowerCase());
            if (stack.isPresent()) {
                stack.get().setAmount(amount);
                return stack.get();
            }
        } catch (Exception ignored) {}

        Material mat = Material.matchMaterial(itemId.toUpperCase());
        if (mat == null) return null;
        ItemStack vanilla = new ItemStack(mat, amount);
        return plugin.getItemManager().getItemTranslator().translate(vanilla);
    }

    private String locationKey(Location loc) {
        return Objects.requireNonNull(loc.getWorld()).getName()
                + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static class ResourceTracker {
        final Material originalMaterial;
        final ZoneResourceConfig config;
        final Location location;
        int stageIndex;
        BukkitTask regenTask;

        ResourceTracker(Material originalMaterial, ZoneResourceConfig config, Location location, int stageIndex, BukkitTask regenTask) {
            this.originalMaterial = originalMaterial;
            this.config = config;
            this.location = location;
            this.stageIndex = stageIndex;
            this.regenTask = regenTask;
        }
    }
}
