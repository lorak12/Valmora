package org.nakii.valmora.module.modifier;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.item.AbilityTrigger;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.effect.AbilityEffect;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.modifier.effect.StatEffect;
import org.nakii.valmora.module.modifier.effect.StateEffect;
import org.nakii.valmora.module.rarity.RarityDefinition;
import org.nakii.valmora.module.rarity.RarityRegistry;
import org.nakii.valmora.module.stat.StatManager;
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
        ModifierGroupDefinition group = groups.get(groupId).orElse(null);
        if (group == null) return new ApplyOutcome(ApplyResult.GROUP_UNKNOWN, baseItem);

        ModifierDefinition def = modifiers.get(modifierId).orElse(null);
        if (def == null) return new ApplyOutcome(ApplyResult.MODIFIER_UNKNOWN, baseItem);
        if (!def.getGroupId().equalsIgnoreCase(groupId)) return new ApplyOutcome(ApplyResult.WRONG_GROUP, baseItem);

        ItemType itemType = readItemType(baseItem);
        if (!group.appliesTo(itemType)) return new ApplyOutcome(ApplyResult.TARGET_NOT_ALLOWED, baseItem);

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
            for (ModifierInstance instance : entry.getValue()) {
                ModifierDefinition def = modifiers.get(instance.getModifierId()).orElse(null);
                if (def == null) continue;
                for (ModifierEffect effect : def.getEffects(instance.getTier())) {
                    if (!(effect instanceof StatEffect stat)) continue;
                    if (!stat.getConditions().evaluate(ctx)) continue;
                    double value = stat.getValue().resolve(rarity, instance.getTier(), ctx) * instance.getCount();
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
     * item-defined abilities. Non-passive triggers are not yet dispatched — see {@link AbilityEffect}.
     */
    public void applyPassiveAbilities(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        for (Map.Entry<String, List<ModifierInstance>> entry : store.readAll(meta, groups).entrySet()) {
            for (ModifierInstance instance : entry.getValue()) {
                ModifierDefinition def = modifiers.get(instance.getModifierId()).orElse(null);
                if (def == null) continue;
                for (ModifierEffect effect : def.getEffects(instance.getTier())) {
                    if (!(effect instanceof AbilityEffect ability)) continue;
                    if (ability.getDefinition().getTrigger() != AbilityTrigger.PASSIVE) continue;
                    for (ConfiguredMechanic mechanic : ability.getDefinition().getMechanics()) {
                        mechanic.execute(player, player);
                    }
                }
            }
        }
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
        ExecutionContext ctx = new SimpleExecutionContext(null, null, null, new org.bukkit.configuration.MemoryConfiguration());
        if (item != null) ctx.set(ItemRarityVariableProvider.ATTACHMENT_KEY, readRarity(item));
        return ctx;
    }

    private ExecutionContext fullContext(LivingEntity caster, ItemStack item) {
        ExecutionContext ctx = caster == null
                ? new SimpleExecutionContext(null, null, null, new org.bukkit.configuration.MemoryConfiguration())
                : new SimpleExecutionContext(caster, null, caster.getLocation(), new org.bukkit.configuration.MemoryConfiguration());
        if (item != null) ctx.set(ItemRarityVariableProvider.ATTACHMENT_KEY, readRarity(item));
        return ctx;
    }

    public ModifierGroupRegistry getGroups() { return groups; }
    public ModifierRegistry getModifiers() { return modifiers; }
    public ModifierComponentStore getStore() { return store; }
}
