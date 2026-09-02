package org.nakii.valmora.module.resource;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.zone.ResourceStage;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneResourceConfig;
import org.nakii.valmora.module.zone.ZoneResourceDrop;

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
    private final Map<String, ResourceTracker> trackedBlocks = new HashMap<>();

    public ResourceManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public boolean isTrackedResource(Location loc) {
        return trackedBlocks.containsKey(locationKey(loc));
    }

    /**
     * Handles a resource-block break attempt. Applies Breaking Power gating, Mining Fortune-scaled
     * drops, multi-stage progression, and (on success) triggers Mining Spread AOE mining on
     * adjacent matching blocks.
     */
    public BreakResult handleBlockBreak(Player player, Block block) {
        String key = locationKey(block.getLocation());
        ResourceTracker tracker = trackedBlocks.get(key);

        ZoneResourceConfig config;
        int stageIndex;
        Material originalMaterial;

        if (tracker != null) {
            if (tracker.stageIndex >= tracker.config.getStageCount()) return BreakResult.DEPLETED; // awaiting regen
            config = tracker.config;
            stageIndex = tracker.stageIndex;
            originalMaterial = tracker.originalMaterial;
        } else {
            ZoneDefinition zone = plugin.getZoneManager().getZoneAt(block.getLocation()).orElse(null);
            if (zone == null) return BreakResult.NOT_TRACKED;
            config = zone.getResourceBlocks().get(block.getType());
            if (config == null) return BreakResult.NOT_TRACKED;
            stageIndex = 0;
            originalMaterial = block.getType();
        }

        if (getPlayerBreakingPower(player) < config.getRequiredPower()) {
            return BreakResult.INSUFFICIENT_POWER;
        }

        // Resource pipeline (docs/COMBAT_PIPELINE_ANALYSIS.md §6 "grouped block breaking") — only
        // engaged for tracked/configured resource blocks, so ordinary block breaking never touches
        // the HookBus at all. Zero-cost when no stage/hook is registered at either point.
        HookBus bus = ValmoraAPI.getInstance().getHookBus();
        boolean pipelineActive = bus != null
                && bus.hasAnyStages(ResourcePipelineLoader.PRE_BREAK, ResourcePipelineLoader.POST_BREAK);
        ExecutionContext pipelineCtx = null;
        if (pipelineActive) {
            pipelineCtx = new SimpleExecutionContext(player, null, block.getLocation(), null);
            pipelineCtx.set("resource:material", originalMaterial.name());
            pipelineCtx.set("resource:stage", stageIndex);
            if (!bus.runPoint(ResourcePipelineLoader.PRE_BREAK, pipelineCtx)) {
                return BreakResult.INTERRUPTED;
            }
        }

        ResourceStage stage = config.getStage(stageIndex);

        // Vanilla Silk Touch (VANILLA_CONTROL_AUDIT.md §1 "Silk Touch / Fortune interaction") — this
        // resource system replaces vanilla's own drop calculation entirely (event.setDropItems(false)
        // in ResourceListener), so a Silk Touch tool would otherwise still yield the configured loot
        // table instead of the block itself. Mirrors vanilla: exactly 1 of the current-stage block,
        // ignoring the loot table and Mining Fortune (vanilla Silk Touch ignores Fortune too).
        // `resource.silk-touch.enabled` (default true) lets a server opt out entirely.
        boolean silkTouch = plugin.getConfig().getBoolean("resource.silk-touch.enabled", true)
                && player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);

        if (silkTouch) {
            ItemStack item = createItem(originalMaterial.name(), 1);
            if (item != null) player.getInventory().addItem(item);
        } else {
            double miningFortune = getPlayerMiningFortune(player);
            for (ZoneResourceDrop drop : stage.getDrops()) {
                if (Math.random() < drop.getChance()) {
                    int amount = applyFortune(drop.rollAmount(), miningFortune);
                    ItemStack item = createItem(drop.getItemId(), amount);
                    if (item != null) player.getInventory().addItem(item);
                }
            }
        }

        if (pipelineActive) {
            bus.runPoint(ResourcePipelineLoader.POST_BREAK, pipelineCtx);
        }

        boolean isLastStage = (stageIndex == config.getStageCount() - 1);
        Material nextMaterial = stage.getNextMaterial() != null ? stage.getNextMaterial() : Material.AIR;

        // Cancel any existing regen timer before scheduling a new one
        if (tracker != null && tracker.regenTask != null) tracker.regenTask.cancel();

        final Material nextMat = nextMaterial;
        final Material finalOriginal = originalMaterial;
        plugin.getServer().getScheduler().runTask(plugin, () -> block.setType(nextMat, false));

        // HC-241: guards a malformed resource config (e.g. `regen-delay: 1`) from scheduling a
        // near-per-tick task storm on every mined block of that type.
        long regenDelayTicks = clampRegenDelay(config.getRegenDelayTicks());
        long regenAtMillis = System.currentTimeMillis() + regenDelayTicks * 50L;
        BukkitTask regenTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            block.setType(finalOriginal, false);
            trackedBlocks.remove(key);
            playRegenFeedback(block.getLocation(), finalOriginal);
        }, regenDelayTicks);

        int depletedIndex = config.getStageCount(); // past end = depleted sentinel
        int nextStageIndex = isLastStage ? depletedIndex : stageIndex + 1;

        if (tracker == null) {
            ResourceTracker newTracker = new ResourceTracker(originalMaterial, config, block.getLocation(), nextStageIndex, regenTask);
            newTracker.regenAtMillis = regenAtMillis;
            trackedBlocks.put(key, newTracker);
        } else {
            tracker.stageIndex = nextStageIndex;
            tracker.regenTask = regenTask;
            tracker.regenAtMillis = regenAtMillis;
        }

        return BreakResult.HANDLED;
    }

    /** Plays break feedback for a resource block mined via Mining Spread AOE (no real {@code BlockBreakEvent}, so no vanilla sound/particle fires on its own). */
    public void playMineFeedback(Location loc, Material material) {
        World world = loc.getWorld();
        if (world == null) return;
        world.playSound(loc, Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 12, 0.25, 0.25, 0.25, material.createBlockData());
    }

    /** Plays a denial cue when Mining Spread skips a neighbor the player lacks Breaking Power for. */
    public void playDeniedFeedback(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        world.playSound(loc, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    /** HC-241: floors a configured regen delay to {@code resource.limits.min-regen-delay-ticks}. */
    private long clampRegenDelay(long configuredTicks) {
        long minTicks = plugin.getConfig().getLong("resource.limits.min-regen-delay-ticks", 20L);
        return Math.max(minTicks, configuredTicks);
    }

    private void playRegenFeedback(Location loc, Material restoredMaterial) {
        World world = loc.getWorld();
        if (world == null) return;
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.4f);
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 16, 0.3, 0.3, 0.3, restoredMaterial.createBlockData());
    }

    private double getPlayerBreakingPower(Player player) {
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

    /** Snapshots {@link #trackedBlocks} to disk. Called on a periodic autosave timer by {@link ResourceModule}. */
    public void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> entries = new ArrayList<>();
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
        yaml.set("tracked", entries);
        try {
            yaml.save(stateFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("[Resource] Failed to save resource_state.yml: " + ex.getMessage());
        }
    }

    /** Restores trackers (and reschedules their regen timers) from a prior unclean shutdown. Called once from {@code onEnable()}. */
    public void loadState() {
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
                    if (world == null) continue;
                    int x = ((Number) map.get("x")).intValue();
                    int y = ((Number) map.get("y")).intValue();
                    int z = ((Number) map.get("z")).intValue();
                    Material original = Material.matchMaterial(String.valueOf(map.get("original")));
                    if (original == null) continue;
                    int stageIndex = ((Number) map.get("stage")).intValue();
                    long regenAt = ((Number) map.get("regen-at")).longValue();

                    Location loc = new Location(world, x, y, z);
                    ZoneDefinition zone = plugin.getZoneManager().getZoneAt(loc).orElse(null);
                    if (zone == null) continue;
                    ZoneResourceConfig config = zone.getResourceBlocks().get(original);
                    if (config == null) continue;

                    String key = locationKey(loc);
                    long remainingTicks = Math.max(1L, (regenAt - now) / 50L);
                    BukkitTask regenTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        loc.getBlock().setType(original, false);
                        trackedBlocks.remove(key);
                        playRegenFeedback(loc, original);
                    }, remainingTicks);

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
        // Consume the file — it's only meant to bridge a single unclean restart.
        file.delete();
    }

    /** Deletes the persisted state file. Called on a clean {@code onDisable()} since {@link #cancelAll()} already restores the world. */
    public void clearStateFile() {
        File file = stateFile();
        if (file.exists()) file.delete();
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
