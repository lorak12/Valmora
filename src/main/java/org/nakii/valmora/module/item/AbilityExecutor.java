package org.nakii.valmora.module.item;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Keys;

/**
 * Shared dispatcher that runs an {@link AbilityDefinition} for a player: evaluates
 * conditions, enforces cooldown and mana cost, then executes the configured mechanics.
 * Used by every trigger source (clicks, on-hit, sneak, shoot, ...) so the activation rules
 * stay in one place — for both item-defined abilities ({@link #fire}/{@link #fireHeld}) and
 * modifier-granted abilities ({@link #fireModifiersForItem}/{@link #fireModifiersHeld}, backed by
 * {@code org.nakii.valmora.module.modifier.ModifierEngine#getGrantedAbilities}, docs/
 * Valmora_Modifier_Framework_Design.docx §9/§17).
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

        ValmoraProfile profile = activeProfile(player);
        if (profile == null) return;
        PlayerState state = profile.getPlayerState();

        for (AbilityDefinition ability : definition.getAbilities().values()) {
            if (ability.getTrigger() != trigger) continue;
            fireOne(player, profile, state, ability, trigger, target, silent);
        }
    }

    /**
     * Fires a single ability definition whose trigger has already been matched by the caller —
     * used for modifier-granted abilities ({@link ModifierEngine#getGrantedAbilities}), which don't
     * live inside an {@link ItemDefinition#getAbilities()} map.
     */
    public static void fireAbility(Player player, AbilityDefinition ability, AbilityTrigger trigger,
                                    LivingEntity target, boolean silent) {
        ValmoraProfile profile = activeProfile(player);
        if (profile == null) return;
        fireOne(player, profile, profile.getPlayerState(), ability, trigger, target, silent);
    }

    /**
     * Convenience: fires the given trigger for the Valmora item the player is currently holding
     * in their main hand (if any) — item-defined abilities only. See {@link #fireModifiersHeld} for
     * the modifier-granted equivalent.
     */
    public static void fireHeld(Player player, AbilityTrigger trigger, LivingEntity target, boolean silent) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        String itemId = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return;
        ValmoraAPI.getInstance().getItemManager().getItemRegistry().getItem(itemId)
                .ifPresent(def -> fire(player, def, trigger, target, silent));
    }

    /**
     * Fires every modifier-granted ABILITY effect (cooldown/mana/condition-gated via {@link
     * #fireAbility}) and EVENT effect (unconditional dispatch) attached to {@code item} whose
     * trigger matches — the modifier-framework equivalent of {@link #fire}/{@link #fireHeld}. A
     * no-op if the modifier module/engine isn't available or the item carries no matching effects.
     */
    public static void fireModifiersForItem(Player player, ItemStack item, AbilityTrigger trigger,
                                             LivingEntity target, boolean silent) {
        if (item == null || !item.hasItemMeta()) return;
        var modifierModule = ValmoraAPI.getInstance().getModifierModule();
        if (modifierModule == null || modifierModule.getEngine() == null) return;
        var engine = modifierModule.getEngine();

        for (AbilityDefinition ability : engine.getGrantedAbilities(item, trigger, player)) {
            fireAbility(player, ability, trigger, target, silent);
        }

        var actions = engine.getGrantedEventActions(item, trigger, player);
        if (!actions.isEmpty()) {
            ExecutionContext eventContext = new SimpleExecutionContext(player, target, player.getLocation(), new MemoryConfiguration());
            for (CompiledEvent action : actions) {
                action.execute(eventContext);
            }
        }
    }

    /** Convenience: {@link #fireModifiersForItem} for the player's current main-hand item. */
    public static void fireModifiersHeld(Player player, AbilityTrigger trigger, LivingEntity target, boolean silent) {
        fireModifiersForItem(player, player.getInventory().getItemInMainHand(), trigger, target, silent);
    }

    private static ValmoraProfile activeProfile(Player player) {
        var session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        return session != null ? session.getActiveProfile() : null;
    }

    private static void fireOne(Player player, ValmoraProfile profile, PlayerState state,
                                 AbilityDefinition ability, AbilityTrigger trigger, LivingEntity target, boolean silent) {
        ValmoraAPI api = ValmoraAPI.getInstance();

        LivingEntity resolvedTarget = target;
        if (resolvedTarget == null && ability.getTargetRange() > 0) {
            resolvedTarget = (LivingEntity) player.getTargetEntity((int) ability.getTargetRange(), false);
            if (resolvedTarget == null) {
                if (!silent) api.getUIManager().getActionBar().showTemporary(player, "<red>No target in range!", 10, 2);
                return;
            }
        }

        ExecutionContext context = new SimpleExecutionContext(player, resolvedTarget,
                player.getLocation(), new MemoryConfiguration());

        if (!conditionsPass(ability, context)) {
            DebugManager.log("abilities", "ability '" + ability.getId() + "' (trigger=" + trigger
                    + ") REJECTED by conditions for player=" + player.getName());
            return;
        }

        if (profile.getCooldownManager().isOnCooldown(ability.getId())) {
            if (!silent) {
                double remaining = profile.getCooldownManager().getRemainingCooldown(ability.getId());
                api.getUIManager().getActionBar().showTemporary(player, "<red>Ability on cooldown: " + remaining + "s", 10, 2);
            }
            DebugManager.log("abilities", "ability '" + ability.getId() + "' (trigger=" + trigger
                    + ") REJECTED — on cooldown for player=" + player.getName());
            return;
        }

        if (ability.getManaCost() > 0) {
            if (state.getCurrentMana() < ability.getManaCost()) {
                if (!silent) api.getUIManager().getActionBar().showTemporary(player, "<aqua>Not enough Mana!", 10, 2);
                DebugManager.log("abilities", "ability '" + ability.getId() + "' (trigger=" + trigger
                        + ") REJECTED — not enough mana (have=" + state.getCurrentMana()
                        + " need=" + ability.getManaCost() + ") for player=" + player.getName());
                return;
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
        HookBus bus = api.getHookBus();
        boolean pipelineActive = bus != null && bus.hasAnyStages("item:pre_ability", "item:post_ability");
        if (pipelineActive) {
            context.set("item:ability_id", ability.getId());
            context.set("item:ability_trigger", trigger.name());
            if (!bus.runPoint("item:pre_ability", context)) {
                DebugManager.log("abilities", "ability '" + ability.getId() + "' (trigger=" + trigger
                        + ") INTERRUPTED at item:pre_ability for player=" + player.getName());
                return; // cooldown/mana already consumed above — the attempt happened, effects didn't
            }
        }

        DebugManager.log("abilities", "ability '" + ability.getId() + "' (trigger=" + trigger + ") FIRING for player="
                + player.getName() + " target=" + (resolvedTarget != null ? resolvedTarget.getName() : "none")
                + " mechanics=" + ability.getMechanics().size());

        for (ConfiguredMechanic mechanic : ability.getMechanics()) {
            mechanic.execute(player, resolvedTarget);
        }

        if (pipelineActive) {
            bus.runPoint("item:post_ability", context);
        }
    }

    private static boolean conditionsPass(AbilityDefinition ability, ExecutionContext context) {
        // Phase 5 (docs/REFACTOR/PROGRESS.md Task 20): conditions are pre-compiled once at item
        // load time (see AbilityDefinition/ItemDefinitionParser) — evaluating an already-built
        // ConditionGroup here, not re-parsing raw strings on every ON_HIT/etc. trigger.
        return ability.getConditions() == null || ability.getConditions().evaluate(context);
    }
}
