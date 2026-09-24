package org.nakii.valmora.module.modifier;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.item.AbilityDefinition;
import org.nakii.valmora.module.item.AbilityTrigger;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.effect.AbilityEffect;
import org.nakii.valmora.module.modifier.effect.EventEffect;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.modifier.effect.StatEffect;
import org.nakii.valmora.module.modifier.effect.StateEffect;
import org.nakii.valmora.module.rarity.RarityDefinition;
import org.nakii.valmora.module.rarity.RarityRegistry;
import org.nakii.valmora.module.script.variable.providers.ItemAbilityVariableProvider;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The generic modifier resolver — application semantics (EXCLUSIVE/STACKABLE/MULTIPLE, capacity,
 * replacement, removal, conflicts) and effect resolution over {@link ModifierComponentStore}
 * components (docs/Valmora_Modifier_Framework_Design.docx §3, §6, §15, §21).
 *
 * <p>No core-engine branch special-cases a default group by name (§23 hard constraint) — every
 * method here operates purely on {@link ModifierGroupDefinition}/{@link ModifierDefinition} data.
 */
public class ModifierEngine {

    public enum ApplyResult { OK, GROUP_UNKNOWN, MODIFIER_UNKNOWN, WRONG_GROUP, TARGET_NOT_ALLOWED,
        REQUIREMENTS_NOT_MET, CONFLICT, CAPACITY_REACHED, ALREADY_PRESENT_NO_REPLACEMENT, REMOVAL_NOT_ALLOWED, NOTHING_TO_REMOVE }

    public record ApplyOutcome(ApplyResult result, ItemStack item) {
        public boolean isSuccess() { return result == ApplyResult.OK; }
    }

    private final ModifierGroupRegistry groups;
    private final ModifierRegistry modifiers;
    private final ModifierComponentStore store;
    private final RarityRegistry rarities;

    public ModifierEngine(ModifierGroupRegistry groups, ModifierRegistry modifiers,
                           ModifierComponentStore store, RarityRegistry rarities) {
        this.groups = groups;
        this.modifiers = modifiers;
        this.store = store;
        this.rarities = rarities;
    }

    // ─── Application semantics ───

    public ApplyOutcome apply(ItemStack baseItem, String groupId, String modifierId, int tier) {
        ApplyOutcome outcome = applyInternal(baseItem, groupId, modifierId, tier);
        DebugManager.log("modifier", "apply(group=" + groupId + ", modifier=" + modifierId + ", tier=" + tier
                + ") -> " + outcome.result());
        return outcome;
    }

    private ApplyOutcome applyInternal(ItemStack baseItem, String groupId, String modifierId, int tier) {
        ModifierGroupDefinition group = groups.get(groupId).orElse(null);
        if (group == null) return new ApplyOutcome(ApplyResult.GROUP_UNKNOWN, baseItem);

        ModifierDefinition def = modifiers.get(modifierId).orElse(null);
        if (def == null) return new ApplyOutcome(ApplyResult.MODIFIER_UNKNOWN, baseItem);
        if (!def.getGroupId().equalsIgnoreCase(groupId)) return new ApplyOutcome(ApplyResult.WRONG_GROUP, baseItem);

        ItemType itemType = readItemType(baseItem);
        if (!group.appliesTo(itemType) || !def.appliesToItemType(itemType)) {
            return new ApplyOutcome(ApplyResult.TARGET_NOT_ALLOWED, baseItem);
        }

        ItemMeta meta = baseItem.getItemMeta();
        if (meta == null) return new ApplyOutcome(ApplyResult.TARGET_NOT_ALLOWED, baseItem);

        if (!def.getRequirements().evaluate(minimalContext(baseItem))) {
            return new ApplyOutcome(ApplyResult.REQUIREMENTS_NOT_MET, baseItem);
        }

        List<ModifierInstance> current = store.read(meta, groupId);

        // Conflicts: check against every OTHER attached modifier across ALL groups on this item.
        Map<String, List<ModifierInstance>> everything = store.readAll(meta, groups);
        for (List<ModifierInstance> list : everything.values()) {
            for (ModifierInstance existing : list) {
                if (existing.getModifierId().equalsIgnoreCase(def.getId())) continue;
                ModifierDefinition existingDef = modifiers.get(existing.getModifierId()).orElse(null);
                if (existingDef == null) continue;
                if (def.getConflictIds().contains(existingDef.getId().toLowerCase(Locale.ROOT))
                        || existingDef.getConflictIds().contains(def.getId().toLowerCase(Locale.ROOT))
                        || !intersectionEmpty(def.getConflictTags(), existingDef.getTags())
                        || !intersectionEmpty(existingDef.getConflictTags(), def.getTags())) {
                    return new ApplyOutcome(ApplyResult.CONFLICT, baseItem);
                }
            }
        }

        List<ModifierInstance> updated = new ArrayList<>(current);
        switch (group.getApplicationMode()) {
            case EXCLUSIVE -> {
                if (!current.isEmpty()) {
                    if (!group.isReplacementAllowed()) return new ApplyOutcome(ApplyResult.ALREADY_PRESENT_NO_REPLACEMENT, baseItem);
                    updated.clear();
                }
                updated.add(newInstance(def, tier));
            }
            case STACKABLE -> {
                ModifierInstance existing = current.stream()
                        .filter(i -> i.getModifierId().equalsIgnoreCase(def.getId()) && i.getTier() == clampTier(def, tier))
                        .findFirst().orElse(null);
                int totalApplications = current.stream().mapToInt(ModifierInstance::getCount).sum();
                if (totalApplications >= group.getMax()) return new ApplyOutcome(ApplyResult.CAPACITY_REACHED, baseItem);
                if (existing != null) {
                    updated.remove(existing);
                    updated.add(existing.withCount(existing.getCount() + 1));
                } else {
                    updated.add(newInstance(def, tier));
                }
            }
            case MULTIPLE -> {
                boolean alreadyPresent = current.stream().anyMatch(i -> i.getModifierId().equalsIgnoreCase(def.getId()));
                if (alreadyPresent && !group.isReplacementAllowed()) {
                    return new ApplyOutcome(ApplyResult.ALREADY_PRESENT_NO_REPLACEMENT, baseItem);
                }
                if (!alreadyPresent && current.size() >= group.getMax()) {
                    return new ApplyOutcome(ApplyResult.CAPACITY_REACHED, baseItem);
                }
                updated.removeIf(i -> i.getModifierId().equalsIgnoreCase(def.getId()));
                updated.add(newInstance(def, tier));
            }
        }

        ItemStack output = baseItem.clone();
        ItemMeta outMeta = output.getItemMeta();
        store.write(outMeta, groupId, updated);
        output.setItemMeta(outMeta);
        return new ApplyOutcome(ApplyResult.OK, output);
    }

    public ApplyOutcome remove(ItemStack baseItem, String groupId, String modifierId) {
        ApplyOutcome outcome = removeInternal(baseItem, groupId, modifierId);
        DebugManager.log("modifier", "remove(group=" + groupId + ", modifier=" + modifierId + ") -> " + outcome.result());
        return outcome;
    }

    private ApplyOutcome removeInternal(ItemStack baseItem, String groupId, String modifierId) {
        ModifierGroupDefinition group = groups.get(groupId).orElse(null);
        if (group == null) return new ApplyOutcome(ApplyResult.GROUP_UNKNOWN, baseItem);
        if (!group.isRemovalAllowed()) return new ApplyOutcome(ApplyResult.REMOVAL_NOT_ALLOWED, baseItem);

        ItemMeta meta = baseItem.getItemMeta();
        if (meta == null) return new ApplyOutcome(ApplyResult.NOTHING_TO_REMOVE, baseItem);

        List<ModifierInstance> current = store.read(meta, groupId);
        if (current.isEmpty()) return new ApplyOutcome(ApplyResult.NOTHING_TO_REMOVE, baseItem);

        List<ModifierInstance> updated;
        if (modifierId == null) {
            updated = List.of(); // remove everything in the group (e.g. reforge slot reset)
        } else {
            boolean hadAny = current.stream().anyMatch(i -> i.getModifierId().equalsIgnoreCase(modifierId));
            if (!hadAny) return new ApplyOutcome(ApplyResult.NOTHING_TO_REMOVE, baseItem);
            updated = current.stream().filter(i -> !i.getModifierId().equalsIgnoreCase(modifierId)).toList();
        }

        ItemStack output = baseItem.clone();
        ItemMeta outMeta = output.getItemMeta();
        store.write(outMeta, groupId, updated);
        output.setItemMeta(outMeta);
        return new ApplyOutcome(ApplyResult.OK, output);
    }

    private ModifierInstance newInstance(ModifierDefinition def, int tier) {
        int clamped = clampTier(def, tier);
        Map<String, Integer> state = new HashMap<>();
        def.getState().values().forEach(s -> state.put(s.getKey(), s.getDefaultValue()));
        return new ModifierInstance(def.getGroupId(), def.getId(), clamped, 1, state);
    }

    private int clampTier(ModifierDefinition def, int tier) {
        if (!def.isTiered()) return 1;
        return Math.max(1, Math.min(tier, def.getMaxTier()));
    }

    /**
     * The tier actually used to resolve effects/display for one instance: either the tier stored on
     * the instance ({@link TierSource#INSTANCE}, the default — e.g. gemstones), or derived fresh
     * from the item's current rarity rank ({@link TierSource#RARITY_RANK} — e.g. reforges, so
     * re-rarity-ing an item automatically reflects in its reforge's granted stats without rewriting
     * the component).
     */
    private int effectiveTier(ModifierGroupDefinition group, ModifierDefinition def, ModifierInstance instance, RarityDefinition rarity) {
        if (group.getTierSource() == TierSource.RARITY_RANK && rarity != null) {
            return Math.max(1, Math.min(rarityRankTier(rarity), def.getMaxTier()));
        }
        return instance.getTier();
    }

    /** HC-122: {@code modifiers.tier-source.formula} — an Expression evaluated with
     *  {@code $rarity.rank$}; falls back to the original hardcoded {@code rank + 1}. */
    private int rarityRankTier(RarityDefinition rarity) {
        var api = org.nakii.valmora.api.ValmoraAPI.getInstance();
        var plugin = org.nakii.valmora.Valmora.getInstance();
        String formula = plugin != null ? plugin.getConfig().getString("modifiers.tier-source.formula", "") : null;
        if (formula != null && !formula.isBlank() && api != null && api.getScriptModule() != null) {
            var ctx = new org.nakii.valmora.api.execution.SimpleExecutionContext(null, null, null, null);
            ctx.set("rarity:rank", (double) rarity.getRank());
            Object result = api.getScriptModule().getExpressionEvaluator().evaluate(formula, ctx);
            if (result instanceof Number n) return n.intValue();
        }
        return rarity.getRank() + 1;
    }

    /**
     * Applies a uniformly-weighted random modifier from {@code groupId} (by {@link
     * ModifierDefinition#getWeight()}), excluding whichever modifier(s) from that group are already
     * attached — the generic equivalent of the legacy {@code forge_random} reforge reroll, but not
     * special-cased to any one group id.
     */
    public ApplyOutcome applyRandom(ItemStack baseItem, String groupId) {
        ModifierGroupDefinition group = groups.get(groupId).orElse(null);
        if (group == null) return new ApplyOutcome(ApplyResult.GROUP_UNKNOWN, baseItem);

        ItemType itemType = readItemType(baseItem);
        ItemMeta meta = baseItem.getItemMeta();
        List<ModifierInstance> current = meta != null ? store.read(meta, groupId) : List.of();
        java.util.Set<String> excludeIds = new java.util.HashSet<>();
        for (ModifierInstance i : current) excludeIds.add(i.getModifierId().toLowerCase(Locale.ROOT));

        List<ModifierDefinition> eligible = new ArrayList<>();
        for (ModifierDefinition def : modifiers.valuesInGroup(groupId)) {
            if (excludeIds.contains(def.getId().toLowerCase(Locale.ROOT))) continue;
            if (!def.getGroupId().equalsIgnoreCase(groupId)) continue;
            eligible.add(def);
        }
        // appliesTo(itemType) is checked centrally in apply(...) too, but filtering here first keeps
        // the weighted roll fair (an ineligible modifier shouldn't consume weight and force a retry).
        eligible.removeIf(def -> !group.appliesTo(itemType) || !def.appliesToItemType(itemType));
        if (eligible.isEmpty()) return new ApplyOutcome(ApplyResult.MODIFIER_UNKNOWN, baseItem);

        double totalWeight = eligible.stream().mapToDouble(ModifierDefinition::getWeight).sum();
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        ModifierDefinition chosen = eligible.get(eligible.size() - 1);
        for (ModifierDefinition def : eligible) {
            cumulative += def.getWeight();
            if (roll < cumulative) { chosen = def; break; }
        }

        return apply(baseItem, groupId, chosen.getId(), 1);
    }

    private boolean intersectionEmpty(java.util.Set<String> a, java.util.Set<String> b) {
        for (String s : a) if (b.contains(s)) return false;
        return true;
    }

    // ─── Effect resolution ───

    /**
     * Resolves every STAT effect from every modifier attached (across all groups) to this item and
     * feeds contributions to {@code sink} (statId, value) — called from {@code StatManager
     * .recalculateStats} exactly like the existing item-stat/enchant/pet contribution steps.
     */
    public void contributeStats(ItemStack item, Player player, BiConsumer<String, Double> sink) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        RarityDefinition rarity = readRarity(item);
        ExecutionContext ctx = fullContext(player, item);

        for (Map.Entry<String, List<ModifierInstance>> entry : store.readAll(meta, groups).entrySet()) {
            ModifierGroupDefinition group = groups.get(entry.getKey()).orElse(null);
            if (group == null) continue;
            for (ModifierInstance instance : entry.getValue()) {
                ModifierDefinition def = modifiers.get(instance.getModifierId()).orElse(null);
                if (def == null) continue;
                int tier = effectiveTier(group, def, instance, rarity);
                for (ModifierEffect effect : def.getEffects(tier)) {
                    if (!(effect instanceof StatEffect stat)) continue;
                    if (!stat.getConditions().evaluate(ctx)) continue;
                    double value = stat.getValue().resolve(rarity, tier, ctx) * instance.getCount();
                    switch (stat.getOperation()) {
                        case ADD -> sink.accept(stat.getStat(), value);
                        case MULTIPLY -> {
                            double current = player != null ? statManagerStat(player, stat.getStat()) : 0;
                            sink.accept(stat.getStat(), current * (value - 1));
                        }
                    }
                }
            }
        }
    }

    private double statManagerStat(Player player, String statId) {
        var session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session == null || session.getActiveProfile() == null) return 0;
        StatManager sm = session.getActiveProfile().getStatManager();
        return sm != null ? sm.getStat(statId) : 0;
    }

    /**
     * Fires PASSIVE-triggered {@link AbilityEffect}s on every modifier attached to {@code item},
     * mirroring the passive-ability step already in {@code StatManager.recalculateStats} for
     * item-defined abilities. PASSIVE mechanics run unconditionally on every stat recalculation
     * (no cooldown/mana gating, matching the pre-existing item-passive behavior) — non-passive
     * triggers go through {@link #getGrantedAbilities} + {@code AbilityExecutor} instead, which does
     * apply cooldown/mana/condition gating (see {@code AbilityExecutor.fireModifiersForItem}).
     */
    public void applyPassiveAbilities(ItemStack item, Player player) {
        for (AbilityDefinition ability : getGrantedAbilities(item, AbilityTrigger.PASSIVE, player)) {
            for (ConfiguredMechanic mechanic : ability.getMechanics()) {
                mechanic.execute(player, player);
            }
        }
    }

    /**
     * Every {@link AbilityDefinition} granted by an {@link AbilityEffect} attached to {@code item}
     * whose trigger matches and whose effect-level {@code conditions:} pass — the modifier-content
     * equivalent of {@code ItemDefinition.getAbilities()}. {@code caster} is used only to build the
     * {@link ExecutionContext} for evaluating the effect's own conditions (e.g. gating a granted
     * ability on the wielder's health); actual ability-level conditions/cooldown/mana are evaluated
     * separately by {@code AbilityExecutor} once it receives the returned definition.
     */
    public List<AbilityDefinition> getGrantedAbilities(ItemStack item, AbilityTrigger trigger, LivingEntity caster) {
        List<AbilityDefinition> result = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return result;
        ItemMeta meta = item.getItemMeta();
        RarityDefinition rarity = readRarity(item);
        ExecutionContext ctx = fullContext(caster, item);

        for (Map.Entry<String, List<ModifierInstance>> entry : store.readAll(meta, groups).entrySet()) {
            ModifierGroupDefinition group = groups.get(entry.getKey()).orElse(null);
            if (group == null) continue;
            for (ModifierInstance instance : entry.getValue()) {
                ModifierDefinition def = modifiers.get(instance.getModifierId()).orElse(null);
                if (def == null) continue;
                int tier = effectiveTier(group, def, instance, rarity);
                for (ModifierEffect effect : def.getEffects(tier)) {
                    if (!(effect instanceof AbilityEffect ability)) continue;
                    if (ability.getDefinition().getTrigger() != trigger) continue;
                    if (!ability.getConditions().evaluate(ctx)) continue;
                    result.add(ability.getDefinition());
                }
            }
        }
        return result;
    }

    /**
     * Every {@link org.nakii.valmora.api.scripting.CompiledEvent} action from an {@link EventEffect}
     * attached to {@code item} whose trigger matches and whose effect-level {@code conditions:} pass.
     * Unlike {@link #getGrantedAbilities}, there's no separate cooldown/mana gate — EVENT effects
     * fire every time their trigger occurs.
     */
    public List<org.nakii.valmora.api.scripting.CompiledEvent> getGrantedEventActions(ItemStack item, AbilityTrigger trigger, LivingEntity caster) {
        List<org.nakii.valmora.api.scripting.CompiledEvent> result = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return result;
        ItemMeta meta = item.getItemMeta();
        RarityDefinition rarity = readRarity(item);
        ExecutionContext ctx = fullContext(caster, item);

        for (Map.Entry<String, List<ModifierInstance>> entry : store.readAll(meta, groups).entrySet()) {
            ModifierGroupDefinition group = groups.get(entry.getKey()).orElse(null);
            if (group == null) continue;
            for (ModifierInstance instance : entry.getValue()) {
                ModifierDefinition def = modifiers.get(instance.getModifierId()).orElse(null);
                if (def == null) continue;
                int tier = effectiveTier(group, def, instance, rarity);
                for (ModifierEffect effect : def.getEffects(tier)) {
                    if (!(effect instanceof EventEffect event)) continue;
                    if (event.getTrigger() != trigger) continue;
                    if (!event.getConditions().evaluate(ctx)) continue;
                    result.addAll(event.getActions());
                }
            }
        }
        return result;
    }

    /**
     * Resolves the display name (prefix/suffix) to render for the given {@link DisplayFormat}
     * group(s) on this item — used by {@code ItemFactory.updateLore} instead of the old
     * reforge-specific {@code Keys.REFORGE_DISPLAY_KEY} lookup. Returns the first match found across
     * groups of that format (a group normally has at most one active instance when it drives display
     * text, since {@code EXCLUSIVE}/{@code SINGLE} is the natural pairing for prefix/suffix content).
     */
    public java.util.Optional<String> getDisplayText(ItemStack item, DisplayFormat format) {
        if (item == null || !item.hasItemMeta()) return java.util.Optional.empty();
        ItemMeta meta = item.getItemMeta();
        RarityDefinition rarity = readRarity(item);
        for (Map.Entry<String, List<ModifierInstance>> entry : store.readAll(meta, groups).entrySet()) {
            ModifierGroupDefinition group = groups.get(entry.getKey()).orElse(null);
            if (group == null || group.getDisplayFormat() != format) continue;
            for (ModifierInstance instance : entry.getValue()) {
                ModifierDefinition def = modifiers.get(instance.getModifierId()).orElse(null);
                if (def == null) continue;
                int tier = effectiveTier(group, def, instance, rarity);
                String text = format == DisplayFormat.SUFFIX ? def.getSuffix() : def.getPrefix();
                if (text == null) {
                    String name = def.getDisplayName(tier);
                    text = name == null ? null : (format == DisplayFormat.SUFFIX ? " " + name : name + " ");
                }
                if (text != null) return java.util.Optional.of(text);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Every LORE-format modifier attached to this item, as {@code (definition, effective tier)}
     * pairs in group display order then attachment order — used by {@code ItemFactory.updateLore}
     * to render gemstone-style lines. Effects themselves are folded into the caller's stat map via
     * {@link #contributeStats}; this is purely for name/tier lore lines.
     */
    public List<Map.Entry<ModifierDefinition, Integer>> getLoreEntries(ItemStack item) {
        List<Map.Entry<ModifierDefinition, Integer>> result = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return result;
        ItemMeta meta = item.getItemMeta();
        RarityDefinition rarity = readRarity(item);

        List<ModifierGroupDefinition> orderedGroups = new ArrayList<>(groups.values());
        orderedGroups.sort(java.util.Comparator.comparingInt(ModifierGroupDefinition::getDisplayOrder));

        for (ModifierGroupDefinition group : orderedGroups) {
            if (group.getDisplayFormat() != DisplayFormat.LORE) continue;
            for (ModifierInstance instance : store.read(meta, group.getId())) {
                ModifierDefinition def = modifiers.get(instance.getModifierId()).orElse(null);
                if (def == null) continue;
                int tier = effectiveTier(group, def, instance, rarity);
                result.add(Map.entry(def, tier));
            }
        }
        return result;
    }

    /** Applies base-level (untiered-position) STATE effects once, at attachment time (§13). */
    public void applyStateEffectsOnAttach(ModifierDefinition def, ModifierInstance instance) {
        for (ModifierEffect effect : def.getEffects(instance.getTier())) {
            if (!(effect instanceof StateEffect state)) continue;
            ModifierStateDefinition stateDef = def.getState().get(state.getKey());
            int delta = (int) Math.round(state.getValue().resolve(null, instance.getTier(), null));
            int current = instance.getState().getOrDefault(state.getKey(), stateDef != null ? stateDef.getDefaultValue() : 0);
            int next = state.getOperation() == StateEffect.Operation.SET ? delta : current + delta;
            if (stateDef != null) next = stateDef.clamp(next);
            instance.getState().put(state.getKey(), next);
        }
    }

    // ─── Helpers ───

    public RarityDefinition readRarity(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(Keys.RARITY_KEY, PersistentDataType.STRING);
        if (raw == null) return null;
        return rarities.getByKey(raw).orElse(null);
    }

    private ItemType readItemType(ItemStack item) {
        if (!item.hasItemMeta()) return ItemType.NONE;
        String raw = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        if (raw == null) return ItemType.NONE;
        return ItemType.find(raw).orElse(ItemType.NONE);
    }

    private ExecutionContext minimalContext(ItemStack item) {
        return fullContext(null, item);
    }

    /**
     * Builds an {@link ExecutionContext} for evaluating a modifier requirement/condition/expression
     * against {@code item}, exposing it via {@code $item.*$}
     * ({@code org.nakii.valmora.module.script.variable.providers.ItemAbilityVariableProvider} — the
     * one shared provider for the {@code item} namespace, see its javadoc for why it isn't a
     * separate class). {@code item:stats} is the item's own baked stat map (not the dynamically
     * resolved effective stats) — safe to compute here without recursing into {@link
     * #contributeStats}.
     */
    private ExecutionContext fullContext(LivingEntity caster, ItemStack item) {
        ExecutionContext ctx = caster == null
                ? new SimpleExecutionContext(null, null, null, new org.bukkit.configuration.MemoryConfiguration())
                : new SimpleExecutionContext(caster, null, caster.getLocation(), new org.bukkit.configuration.MemoryConfiguration());
        if (item == null) return ctx;

        ctx.set(ItemAbilityVariableProvider.RARITY_ATTACHMENT_KEY, readRarity(item));
        ctx.set(ItemAbilityVariableProvider.TYPE_ATTACHMENT_KEY, readItemType(item).name());
        if (item.hasItemMeta()) {
            String itemId = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            ctx.set(ItemAbilityVariableProvider.ID_ATTACHMENT_KEY, itemId);
            ctx.set(ItemAbilityVariableProvider.STATS_ATTACHMENT_KEY, ValmoraAPI.getInstance().getStatModule().loadStats(item.getItemMeta()));
        }
        return ctx;
    }

    public ModifierGroupRegistry getGroups() { return groups; }
    public ModifierRegistry getModifiers() { return modifiers; }
    public ModifierComponentStore getStore() { return store; }
}
