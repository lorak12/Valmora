package org.nakii.valmora.module.item;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Shared dispatcher that runs an item {@link AbilityDefinition} for a player: evaluates
 * conditions, enforces cooldown and mana cost, then executes the configured mechanics.
 * Used by every trigger source (clicks, on-hit, sneak, shoot, ...) so the activation rules
 * stay in one place.
 */
public final class AbilityExecutor {

    private AbilityExecutor() {}

    /**
     * Fires every ability on {@code definition} whose trigger matches {@code trigger}.
     *
     * @param player     the activating player
     * @param definition the item definition holding the abilities
     * @param trigger    the trigger that fired
     * @param target     the resolved primary target (may be null)
     * @param silent     when true, suppress player-facing cooldown/mana messages (for passive
     *                   triggers like ON_HIT that fire frequently)
     */
    public static void fire(Player player, ItemDefinition definition, AbilityTrigger trigger,
                            LivingEntity target, boolean silent) {
        if (definition.getAbilities() == null || definition.getAbilities().isEmpty()) return;

        ValmoraAPI api = ValmoraAPI.getInstance();
        ValmoraProfile profile = api.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();
        if (profile == null) return;
        PlayerState state = profile.getPlayerState();

        for (AbilityDefinition ability : definition.getAbilities().values()) {
            if (ability.getTrigger() != trigger) continue;

            LivingEntity resolvedTarget = target;
            if (resolvedTarget == null && ability.getTargetRange() > 0) {
                resolvedTarget = (LivingEntity) player.getTargetEntity((int) ability.getTargetRange(), false);
                if (resolvedTarget == null) {
                    if (!silent) api.getUIManager().getActionBar().showTemporary(player, "<red>No target in range!", 10, 2);
                    continue;
                }
            }

            ExecutionContext context = new SimpleExecutionContext(player, resolvedTarget,
                    player.getLocation(), new MemoryConfiguration());

            if (!conditionsPass(ability, context)) continue;

            if (profile.getCooldownManager().isOnCooldown(ability.getId())) {
                if (!silent) {
                    double remaining = profile.getCooldownManager().getRemainingCooldown(ability.getId());
                    api.getUIManager().getActionBar().showTemporary(player, "<red>Ability on cooldown: " + remaining + "s", 10, 2);
                }
                continue;
            }

            if (ability.getManaCost() > 0) {
                if (state.getCurrentMana() < ability.getManaCost()) {
                    if (!silent) api.getUIManager().getActionBar().showTemporary(player, "<aqua>Not enough Mana!", 10, 2);
                    continue;
                }
                state.reduceMana(ability.getManaCost());
            }

            if (ability.getCooldown() > 0) {
                profile.getCooldownManager().setCooldown(ability.getId(), ability.getCooldown());
            }

            // Item ability pipeline hooks — see docs/VALMORA_DOCUMENTATION.md §39. Supplementary to
            // the native cooldown/mana gating above (unchanged): a stage/addon can veto this
            // specific ability firing (`interrupt` on item:pre_ability, e.g. "no abilities in this
            // zone") or react after it resolves (item:post_ability). Zero-cost when nothing is
            // registered at either point, same as every other domain.
            HookBus bus = ValmoraAPI.getInstance().getHookBus();
            boolean pipelineActive = bus != null && bus.hasAnyStages("item:pre_ability", "item:post_ability");
            if (pipelineActive) {
                context.set("item:ability_id", ability.getId());
                context.set("item:ability_trigger", trigger.name());
                if (!bus.runPoint("item:pre_ability", context)) {
                    continue; // cooldown/mana already consumed above — the attempt happened, effects didn't
                }
            }

            for (ConfiguredMechanic mechanic : ability.getMechanics()) {
                mechanic.execute(player, resolvedTarget);
            }

            if (pipelineActive) {
                bus.runPoint("item:post_ability", context);
            }
        }
    }

    /**
     * Convenience: fires the given trigger for the Valmora item the player is currently holding
     * in their main hand (if any).
     */
    public static void fireHeld(Player player, AbilityTrigger trigger, LivingEntity target, boolean silent) {
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        String itemId = item.getItemMeta().getPersistentDataContainer()
                .get(org.nakii.valmora.util.Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
        if (itemId == null) return;
        ValmoraAPI.getInstance().getItemManager().getItemRegistry().getItem(itemId)
                .ifPresent(def -> fire(player, def, trigger, target, silent));
    }

    private static boolean conditionsPass(AbilityDefinition ability, ExecutionContext context) {
        // Phase 5 (docs/REFACTOR/PROGRESS.md Task 20): conditions are pre-compiled once at item
        // load time (see AbilityDefinition/ItemDefinitionParser) — evaluating an already-built
        // ConditionGroup here, not re-parsing raw strings on every ON_HIT/etc. trigger.
        return ability.getConditions() == null || ability.getConditions().evaluate(context);
    }
}
