package org.nakii.valmora.module.fishing.flow;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.api.pipeline.flow.NumberParam;
import org.nakii.valmora.api.pipeline.flow.StepOutcome;
import org.nakii.valmora.module.fishing.FishingLootEntry;
import org.nakii.valmora.module.fishing.FishingLootTable;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.script.pipeline.FeedbackSteps;

import java.util.List;
import java.util.Map;

/**
 * The flows of {@code fishing_pipeline.yml} (docs/PIPELINE_FLOWS_PLAN.md §5.1) — the steps that
 * used to be {@code FishingManager.handleCatch}/{@code playBiteFeedback}, now each a built-in the
 * file lists in order.
 */
public final class FishingFlows {

    private FishingFlows() {}

    public static final String DOMAIN_ID = "fishing";
    public static final String POINT_PREFIX = "fishing:";
    public static final String PRE_CATCH = POINT_PREFIX + "pre_catch";
    public static final String POST_CATCH = POINT_PREFIX + "post_catch";

    /** {@code catch}: a fish was reeled in. */
    public static final FlowSpec<CatchState> CATCH = new FlowSpec<CatchState>(DOMAIN_ID, "catch")
            .builtin(BuiltinStepFactory.required("resolve_table", List.of("fallback-table"), params -> {
                String fallback = params.string("fallback-table", "default");
                return (ctx, state) -> {
                    state.table = state.manager.getTableFor(state.player, fallback);
                    return state.table == null ? StepOutcome.STOP : StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("roll_sea_creature", List.of("chance"), params -> {
                NumberParam chance = params.number("chance", (ctx, state) -> ((CatchState) state).table.getSeaCreatureChance());
                return (ctx, state) -> {
                    FishingLootTable table = state.table;
                    if (table.getSeaCreatureMobId() != null && Math.random() < chance.get(ctx, state)) {
                        state.seaCreatureId = table.getSeaCreatureMobId();
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("spawn_sea_creature", List.of("at"), params -> {
                boolean atPlayer = "player".equalsIgnoreCase(params.string("at", "hook"));
                return (ctx, state) -> {
                    if (state.seaCreatureId == null) return StepOutcome.CONTINUE;
                    MobDefinition def = state.manager.getPlugin().getMobManager().getMobDefinition(state.seaCreatureId);
                    if (def != null) {
                        state.manager.getPlugin().getMobManager().spawnMob(def, atPlayer ? state.player.getLocation() : state.hook);
                    }
                    state.caught = def != null;
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("roll_loot", (ctx, state) -> {
                if (state.seaCreatureId != null) return StepOutcome.CONTINUE;
                FishingLootEntry entry = state.table.roll();
                if (entry != null) {
                    // One roll, read by both the grant and $fishing.amount$ (they used to be two
                    // separate random rolls).
                    state.itemId = entry.getItemId();
                    state.amount = entry.rollAmount();
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("give_catch", List.of("overflow"), params -> {
                boolean drop = "drop".equalsIgnoreCase(params.string("overflow", "discard"));
                return (ctx, state) -> {
                    if (state.seaCreatureId != null || state.itemId == null) return StepOutcome.CONTINUE;
                    ItemStack item = state.manager.createItem(state.itemId, state.amount);
                    state.item = item;
                    if (item != null) {
                        Map<Integer, ItemStack> leftover = state.player.getInventory().addItem(item);
                        if (drop && leftover != null) {
                            for (ItemStack rest : leftover.values()) {
                                state.player.getWorld().dropItemNaturally(state.player.getLocation(), rest);
                            }
                        }
                    }
                    state.caught = item != null;
                    return StepOutcome.CONTINUE;
                };
            }))
            .anchor(PRE_CATCH, true, "before the sea-creature/loot roll — interrupt cancels the catch")
            .anchor(POST_CATCH, false, "after the reward is resolved")
            .writable("fishing.sea_creature", "fishing.item", "fishing.amount");

    /** {@code bite}: a fish bit (feedback only). */
    public static final FlowSpec<BiteState> BITE = new FlowSpec<BiteState>(DOMAIN_ID, "bite")
            .builtin(FeedbackSteps.playSound())
            .builtin(FeedbackSteps.spawnParticle());

    public static final FlowDomain DOMAIN = new FlowDomain(DOMAIN_ID, "fishing_pipeline.yml", POINT_PREFIX,
            List.of(CATCH, BITE), List.of("pre_catch", "post_catch"));
}
