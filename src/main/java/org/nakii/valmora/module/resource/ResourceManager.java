package org.nakii.valmora.module.resource;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.flow.FlowResult;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneResourceConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ResourceManager {

    /** Result of attempting to break a tracked/potential resource block. */
    public enum BreakResult {
        /** Not a configured resource block in this zone — vanilla handling applies. */
        NOT_TRACKED,
        /** A resource block, but the player's Breaking Power is below the required threshold. */
        INSUFFICIENT_POWER,
        /** A {@code resource:pre_break} pipeline stage called {@code interrupt} — the break is cancelled. */
        INTERRUPTED,
        /** Tracked, but already fully depleted and mid-regen — the break attempt is cancelled. */
        DEPLETED,
        /** Successfully mined; drops were generated and the block progressed/regenerated. */
        HANDLED
    }

    private final Valmora plugin;
    final Map<String, ResourceTracker> trackedBlocks = new HashMap<>();
    /** Saved entries whose world isn't loaded yet — kept and written back until it is. */
    private final List<Map<?, ?>> pendingStateEntries = new ArrayList<>();
    private BukkitTask pendingSave;

    public ResourceManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public boolean isTrackedResource(Location loc) {
        return trackedBlocks.containsKey(locationKey(loc));
    }

    /**
     * Handles a resource-block break attempt by running {@code resource_pipeline.yml}'s
     * {@code break} flow ({@link ResourceFlows#BREAK}): Breaking Power gating, Silk Touch, Mining
     * Fortune-scaled drops, multi-stage progression and regeneration.
     */
    public BreakResult handleBlockBreak(Player player, Block block) {
        if (block.getLocation().getWorld() == null) return BreakResult.NOT_TRACKED;
        BreakState state = new BreakState(this, player, block, locationKey(block.getLocation()));
        ExecutionContext ctx = new SimpleExecutionContext(player, null, block.getLocation(), null);
        FlowResult result = ResourceFlows.BREAK.run(ctx, state);
        if (state.result != null) return state.result;
        if (result.skipped()) return BreakResult.NOT_TRACKED; // flow never loaded — vanilla handling
        return result.stopped() ? BreakResult.INTERRUPTED : BreakResult.HANDLED;
    }

    /** Plays break feedback for a resource block mined via Mining Spread AOE (no real {@code BlockBreakEvent}, so no vanilla sound/particle fires on its own) — the {@code mine_feedback} flow. */
    public void playMineFeedback(Location loc, Material material) {
        playFeedback(ResourceFlows.MINE_FEEDBACK, loc, material);
    }

    /** Plays a denial cue when Mining Spread skips a neighbor the player lacks Breaking Power for — the {@code denied_feedback} flow. */
    public void playDeniedFeedback(Location loc) {
        playFeedback(ResourceFlows.DENIED_FEEDBACK, loc, null);
    }

    private void playRegenFeedback(Location loc, Material restoredMaterial) {
        playFeedback(ResourceFlows.REGEN_FEEDBACK, loc, restoredMaterial);
    }

    private static void playFeedback(org.nakii.valmora.api.pipeline.flow.FlowSpec<ResourceFeedbackState> flow, Location loc, Material material) {
        if (loc.getWorld() == null) return;
        flow.run(new SimpleExecutionContext(null, null, loc, null), new ResourceFeedbackState(loc, material));
    }

    /** Restores a depleted block (the scheduled end of {@code schedule_regen}). */
    void regenerate(String key, Block block, Material original) {
        block.setType(original, false);
        trackedBlocks.remove(key);
        playRegenFeedback(block.getLocation(), original);
    }

    double getPlayerBreakingPower(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return 0.0;
        var profile = session.getActiveProfile();
        if (profile == null) return 0.0;
        return profile.getStatManager().getStat(ValmoraAPI.getInstance().getSystemStats().getBreakingPower());
    }

    Valmora getPlugin() { return plugin; }

    /** Exposes the resource-block config for a location, if any, for AOE-mining adjacency checks. */
    public ZoneResourceConfig getResourceConfigAt(Location loc) {
        ZoneDefinition zone = plugin.getZoneManager().getZoneAt(loc).orElse(null);
        if (zone == null) return null;
        return zone.getResourceBlocks().get(loc.getBlock().getType());
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

    // --- Crash-safe persistence ------------------------------------------------------------
    // trackedBlocks is otherwise purely in-memory (§5 of docs/modules/design/resource.md). A
    // clean disable/reload always restores blocks via cancelAll() above, so the state file only
    // matters after an unclean shutdown (crash, kill -9) where onDisable never runs — in that
    // case the world chunk itself already holds the intermediate block material; this file only
    // needs to carry enough to re-derive the tracker + reschedule the regen timer on next start.

    private File stateFile() {
        return new File(plugin.getDataFolder(), "resource_state.yml");
    }

    /**
     * Saves soon (debounced ~2s) after a block is depleted. The 30s timer alone left up to 30s of
     * depleted blocks unrecorded, so they stayed broken forever after a crash.
     */
    void requestSave() {
        if (pendingSave != null) return;
        pendingSave = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            saveState();
        }, 40L);
    }

    /** Snapshots {@link #trackedBlocks} to disk. Called on a periodic autosave timer by {@link ResourceModule}. */
    public void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<?, ?>> entries = new ArrayList<>();
        for (ResourceTracker tracker : trackedBlocks.values()) {
            if (tracker.location.getWorld() == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", tracker.location.getWorld().getName());
            entry.put("x", tracker.location.getBlockX());
            entry.put("y", tracker.location.getBlockY());
            entry.put("z", tracker.location.getBlockZ());
            entry.put("original", tracker.originalMaterial.name());
            entry.put("stage", tracker.stageIndex);
            entry.put("regen-at", tracker.regenAtMillis);
            entries.add(entry);
        }
        entries.addAll(pendingStateEntries);
        yaml.set("tracked", entries);
        try {
            yaml.save(stateFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("[Resource] Failed to save resource_state.yml: " + ex.getMessage());
        }
    }

    /** Restores trackers (and reschedules their regen timers) from a prior unclean shutdown. Called once from {@code onEnable()}. */
    public void loadState() {
        pendingStateEntries.clear();
        File file = stateFile();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> list = yaml.getList("tracked");
        long now = System.currentTimeMillis();
        int restored = 0;
        if (list != null) {
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                try {
                    World world = plugin.getServer().getWorld(String.valueOf(map.get("world")));
                    if (world == null) {
                        // World not loaded (yet): keep the entry instead of deleting it with the file.
                        pendingStateEntries.add(map);
                        continue;
                    }
                    int x = ((Number) map.get("x")).intValue();
                    int y = ((Number) map.get("y")).intValue();
                    int z = ((Number) map.get("z")).intValue();
                    Material original = Material.matchMaterial(String.valueOf(map.get("original")));
                    if (original == null) continue;
                    int stageIndex = ((Number) map.get("stage")).intValue();
                    long regenAt = ((Number) map.get("regen-at")).longValue();

                    Location loc = new Location(world, x, y, z);
                    ZoneDefinition zone = plugin.getZoneManager().getZoneAt(loc).orElse(null);
                    ZoneResourceConfig config = zone != null ? zone.getResourceBlocks().get(original) : null;
                    if (config == null) {
                        // The zone or resource config was removed meanwhile — nothing will ever
                        // regenerate this block, so put the original back now instead of leaving
                        // it depleted forever.
                        loc.getBlock().setType(original, false);
                        continue;
                    }

                    String key = locationKey(loc);
                    long remainingTicks = Math.max(1L, (regenAt - now) / 50L);
                    Block restoredBlock = loc.getBlock();
                    BukkitTask regenTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> regenerate(key, restoredBlock, original), remainingTicks);

                    ResourceTracker tracker = new ResourceTracker(original, config, loc, stageIndex, regenTask);
                    tracker.regenAtMillis = regenAt;
                    trackedBlocks.put(key, tracker);
                    restored++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("[Resource] Skipped a malformed resource_state.yml entry: " + ex.getMessage());
                }
            }
        }
        if (restored > 0) {
            plugin.getLogger().info("[Resource] Restored " + restored + " mid-progress resource block(s) after an unclean shutdown.");
        }
        // Consume the file — it's only meant to bridge a single unclean restart. Entries for worlds
        // that aren't loaded are written back so they survive until those worlds are.
        file.delete();
        if (!pendingStateEntries.isEmpty()) saveState();
    }

    /** Deletes the persisted state file. Called on a clean {@code onDisable()} since {@link #cancelAll()} already restores the world. */
    public void clearStateFile() {
        if (pendingSave != null) { pendingSave.cancel(); pendingSave = null; }
        File file = stateFile();
        if (!pendingStateEntries.isEmpty()) {
            saveState(); // trackedBlocks is empty after cancelAll(); only unloaded-world entries remain
            return;
        }
        if (file.exists()) file.delete();
    }

    ItemStack createItem(String itemId, int amount) {
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

    static class ResourceTracker {
        final Material originalMaterial;
        final ZoneResourceConfig config;
        final Location location;
        int stageIndex;
        BukkitTask regenTask;
        long regenAtMillis;

        ResourceTracker(Material originalMaterial, ZoneResourceConfig config, Location location, int stageIndex, BukkitTask regenTask) {
            this.originalMaterial = originalMaterial;
            this.config = config;
            this.location = location;
            this.stageIndex = stageIndex;
            this.regenTask = regenTask;
        }
    }
}
