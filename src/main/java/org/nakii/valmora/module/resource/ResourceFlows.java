package org.nakii.valmora.module.resource;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.api.pipeline.flow.NumberParam;
import org.nakii.valmora.api.pipeline.flow.StepOutcome;
import org.nakii.valmora.api.pipeline.flow.StepParams;
import org.nakii.valmora.module.script.pipeline.FeedbackSteps;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneResourceConfig;
import org.nakii.valmora.module.zone.ZoneResourceDrop;
import org.nakii.valmora.util.MiningFortune;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The flows of {@code resource_pipeline.yml} (docs/PIPELINE_FLOWS_PLAN.md §5.2) — what used to be
 * {@code ResourceManager.handleBlockBreak} and its hardcoded sound/particle cues.
 */
public final class ResourceFlows {

    private ResourceFlows() {}

    public static final String DOMAIN_ID = "resource";
    public static final String POINT_PREFIX = "resource:";
    public static final String PRE_BREAK = POINT_PREFIX + "pre_break";
    public static final String POST_BREAK = POINT_PREFIX + "post_break";

    /** {@code break}: a player broke a block inside a zone that configures resource blocks. */
    public static final FlowSpec<BreakState> BREAK = new FlowSpec<BreakState>(DOMAIN_ID, "break")
            .builtin(BuiltinStepFactory.required("resolve_resource", ResourceFlows::resolveResource))
            .builtin(BuiltinStepFactory.of("breaking_power_gate", (ctx, s) -> {
                if (s.manager.getPlayerBreakingPower(s.player) < s.config.getRequiredPower()) {
                    s.result = ResourceManager.BreakResult.INSUFFICIENT_POWER;
                    return StepOutcome.STOP;
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("silk_touch", List.of("enabled"), params -> {
                BooleanSupplier enabled = params.bool("enabled", StepParams.configBool("resource.silk-touch.enabled", true));
                return (ctx, s) -> {
                    // This system replaces vanilla drops entirely (ResourceListener), so a Silk Touch
                    // tool would otherwise still yield the loot table. Mirrors vanilla: exactly 1 of
                    // the original block, no loot table, no Mining Fortune.
                    if (enabled.getAsBoolean()
                            && s.player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
                        s.drops.clear();
                        ItemStack item = s.manager.createItem(s.original.name(), 1);
                        if (item != null) s.drops.add(item);
                        s.dropsResolved = true;
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("roll_drops", (ctx, s) -> {
                if (s.dropsResolved) return StepOutcome.CONTINUE;
                double miningFortune = MiningFortune.getPlayerMiningFortune(s.player);
                for (ZoneResourceDrop drop : s.stage.getDrops()) {
                    if (Math.random() < drop.getChance()) {
                        int amount = MiningFortune.applyFortune(drop.rollAmount(), miningFortune);
                        ItemStack item = s.manager.createItem(drop.getItemId(), amount);
                        if (item != null) s.drops.add(item);
                    }
                }
                s.dropsResolved = true;
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("give_drops", List.of("overflow"), params -> {
                boolean drop = "drop".equalsIgnoreCase(params.string("overflow", "discard"));
                return (ctx, s) -> {
                    for (ItemStack item : s.drops) {
                        Map<Integer, ItemStack> leftover = s.player.getInventory().addItem(item);
                        if (drop && leftover != null) {
                            for (ItemStack rest : leftover.values()) {
                                s.player.getWorld().dropItemNaturally(s.player.getLocation(), rest);
                            }
                        }
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.required("advance_stage", (ctx, s) -> {
                Material next = s.stage.getNextMaterial() != null ? s.stage.getNextMaterial() : Material.AIR;
                Block block = s.block;
                s.manager.getPlugin().getServer().getScheduler().runTask(s.manager.getPlugin(), () -> block.setType(next, false));
                trackerFor(s).stageIndex = s.nextStageIndex;
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.required("schedule_regen", List.of("min-delay-ticks"), params -> {
                // HC-241: guards a malformed resource config (e.g. `regen-delay: 1`) from scheduling
                // a near-per-tick task storm on every mined block of that type.
                NumberParam minDelay = params.number("min-delay-ticks", NumberParam.config("resource.limits.min-regen-delay-ticks", 20));
                return (ctx, s) -> {
                    ResourceManager.ResourceTracker tracker = trackerFor(s);
                    if (tracker.regenTask != null) tracker.regenTask.cancel();
                    long delay = Math.max((long) minDelay.get(ctx, s), s.config.getRegenDelayTicks());
                    // Only plain values go into the task — never the flow state or context.
                    ResourceManager manager = s.manager;
                    Block block = s.block;
                    Material original = s.original;
                    String key = s.key;
                    tracker.regenTask = manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(),
                            () -> manager.regenerate(key, block, original), delay);
                    tracker.regenAtMillis = System.currentTimeMillis() + delay * 50L;
                    manager.requestSave();
                    return StepOutcome.CONTINUE;
                };
            }))
            .anchor(PRE_BREAK, true, "after the Breaking Power gate, before drops — interrupt cancels the break")
            .anchor(POST_BREAK, false, "after drops are granted, before the block advances");

    /** {@code mine_feedback}: a neighbour block mined by Mining Spread (no vanilla break cue fires for those). */
    public static final FlowSpec<ResourceFeedbackState> MINE_FEEDBACK = feedback("mine_feedback");
    /** {@code denied_feedback}: Mining Spread skipped a neighbour the player lacks Breaking Power for. */
    public static final FlowSpec<ResourceFeedbackState> DENIED_FEEDBACK = feedback("denied_feedback");
    /** {@code regen_feedback}: a depleted resource block regenerated. */
    public static final FlowSpec<ResourceFeedbackState> REGEN_FEEDBACK = feedback("regen_feedback");

    public static final FlowDomain DOMAIN = new FlowDomain(DOMAIN_ID, "resource_pipeline.yml", POINT_PREFIX,
            List.of(BREAK, MINE_FEEDBACK, DENIED_FEEDBACK, REGEN_FEEDBACK), List.of("pre_break", "post_break"));

    private static FlowSpec<ResourceFeedbackState> feedback(String id) {
        return new FlowSpec<ResourceFeedbackState>(DOMAIN_ID, id)
                .builtin(FeedbackSteps.playSound())
                .builtin(FeedbackSteps.spawnParticle());
    }

    private static StepOutcome resolveResource(org.nakii.valmora.api.execution.ExecutionContext ctx, BreakState s) {
        ResourceManager.ResourceTracker tracker = s.manager.trackedBlocks.get(s.key);
        if (tracker != null) {
            if (tracker.stageIndex >= tracker.config.getStageCount()) { // awaiting regen
                s.result = ResourceManager.BreakResult.DEPLETED;
                return StepOutcome.STOP;
            }
            s.tracker = tracker;
            s.config = tracker.config;
            s.stageIndex = tracker.stageIndex;
            s.original = tracker.originalMaterial;
        } else {
            ZoneDefinition zone = s.manager.getPlugin().getZoneManager().getZoneAt(s.block.getLocation()).orElse(null);
            ZoneResourceConfig config = zone != null ? zone.getResourceBlocks().get(s.block.getType()) : null;
            if (config == null) {
                s.result = ResourceManager.BreakResult.NOT_TRACKED;
                return StepOutcome.STOP;
            }
            s.config = config;
            s.stageIndex = 0;
            s.original = s.block.getType();
        }
        s.stage = s.config.getStage(s.stageIndex);
        boolean isLastStage = s.stageIndex == s.config.getStageCount() - 1;
        s.nextStageIndex = isLastStage ? s.config.getStageCount() : s.stageIndex + 1; // past end = depleted
        return StepOutcome.CONTINUE;
    }

    /** The block's tracker, created (and registered) on its first break. */
    private static ResourceManager.ResourceTracker trackerFor(BreakState s) {
        if (s.tracker == null) {
            s.tracker = new ResourceManager.ResourceTracker(s.original, s.config, s.block.getLocation(), s.stageIndex, null);
            s.manager.trackedBlocks.put(s.key, s.tracker);
        }
        return s.tracker;
    }
}
