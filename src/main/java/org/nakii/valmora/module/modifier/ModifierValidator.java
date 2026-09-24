package org.nakii.valmora.module.modifier;

import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.modifier.effect.StateEffect;
import org.nakii.valmora.module.modifier.recipe.ModifierRecipeDefinition;
import org.nakii.valmora.module.modifier.recipe.ModifierRecipeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Cross-reference validation for loaded modifier content (docs/
 * Valmora_Modifier_Framework_Design.docx §21 tasks 12-13: "unknown group IDs, unknown modifiers,
 * invalid targets, duplicate exclusive modifiers, bad rarity references, unsupported effect types,
 * and malformed value providers").
 *
 * <p><b>Scope note:</b> this runs <i>after</i> groups/modifiers/recipes have already been parsed
 * into the live registries (matching every other content loader in this codebase — none of them
 * stage into a temporary registry and swap atomically either), so it reports problems rather than
 * preventing a bad reload from taking effect. The design doc's §23 hard constraint ("content reload
 * must validate all definitions before replacing live registries") is not fully met — doing so would
 * require restructuring {@link ModifierModule}'s (and every sibling module's) load methods to build
 * into a staging registry first, which is out of scope here. See
 * docs/MODIFIER_FRAMEWORK_BACKLOG.md.
 */
public final class ModifierValidator {

    private ModifierValidator() {}

    public static void validate(ModifierGroupRegistry groups, ModifierRegistry modifiers,
                                 ModifierRecipeRegistry recipes, ItemManager itemManager, Logger logger) {
        List<String> warnings = collect(groups, modifiers, recipes, itemManager);

        if (!warnings.isEmpty()) {
            logger.warning("Modifier content validation found " + warnings.size() + " issue(s):");
            logger.warning("------------------------------");
            for (String w : warnings) logger.warning("- " + w);
            logger.warning("------------------------------");
        }
    }

    /** Every problem found, one message each — what {@link #validate} logs. */
    public static List<String> collect(ModifierGroupRegistry groups, ModifierRegistry modifiers,
                                       ModifierRecipeRegistry recipes, ItemManager itemManager) {
        List<String> warnings = new ArrayList<>();

        for (ModifierDefinition def : modifiers.values()) {
            validateModifier(def, groups, modifiers, warnings);
        }

        for (ModifierRecipeDefinition recipe : recipes.values()) {
            validateRecipe(recipe, groups, modifiers, itemManager, warnings);
        }

        return warnings;
    }

    private static void validateModifier(ModifierDefinition def, ModifierGroupRegistry groups,
                                          ModifierRegistry modifiers, List<String> warnings) {
        String ctx = "Modifier '" + def.getId() + "'";

        if (groups.get(def.getGroupId()).isEmpty()) {
            warnings.add(ctx + " references unknown group '" + def.getGroupId() + "'");
        }

        for (String conflictId : def.getConflictIds()) {
            if (modifiers.get(conflictId).isEmpty()) {
                warnings.add(ctx + " conflicts.ids references unknown modifier '" + conflictId + "'");
            }
        }

        // Every STATE effect's key should be declared in this modifier's own `state:` map.
        List<ModifierEffect> allEffects = new ArrayList<>();
        if (def.isTiered()) {
            def.getTiers().values().forEach(t -> allEffects.addAll(t.getEffects()));
        } else {
            allEffects.addAll(def.getEffects(1));
        }
        for (ModifierEffect effect : allEffects) {
            if (effect instanceof StateEffect state && !def.getState().containsKey(state.getKey().toLowerCase(Locale.ROOT))) {
                warnings.add(ctx + " has a STATE effect referencing undeclared state key '" + state.getKey() + "' (add it under 'state:')");
            }
        }
    }

    private static void validateRecipe(ModifierRecipeDefinition recipe, ModifierGroupRegistry groups,
                                        ModifierRegistry modifiers, ItemManager itemManager, List<String> warnings) {
        String ctx = "Modifier recipe '" + recipe.getId() + "'";

        if (groups.get(recipe.getModifierGroup()).isEmpty()) {
            warnings.add(ctx + " references unknown group '" + recipe.getModifierGroup() + "'");
        }

        if (recipe.getOperation() == ModifierRecipeDefinition.Operation.APPLY_MODIFIER
                && recipe.getModifierId() != null
                && !"RANDOM".equalsIgnoreCase(recipe.getModifierId())
                && modifiers.get(recipe.getModifierId()).isEmpty()) {
            warnings.add(ctx + " references unknown modifier id '" + recipe.getModifierId() + "'");
        }

        if (recipe.getAdditionItemId() != null && itemManager != null
                && itemManager.getItemRegistry().getItem(recipe.getAdditionItemId()).isEmpty()) {
            warnings.add(ctx + " addition.item references unknown item id '" + recipe.getAdditionItemId()
                    + "' (a vanilla material name is also accepted at match time, so this may be a false positive — but check for a typo)");
        }
    }
}
