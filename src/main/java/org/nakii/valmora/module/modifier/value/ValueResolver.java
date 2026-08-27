package org.nakii.valmora.module.modifier.value;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;

/**
 * Generic value provider for a modifier effect's numeric magnitude (docs/
 * Valmora_Modifier_Framework_Design.docx §5/§18). One resolver pipeline covers literal, expression,
 * rarity-scaled, and Java-registered custom values — deliberately not split into
 * {@code RarityValue}/{@code GemstoneValue}/{@code ReforgeValue} classes (§18, §23).
 */
public interface ValueResolver {

    /**
     * @param rarity the rarity resolved for the item carrying this modifier (may be {@code null})
     * @param tier the modifier instance's tier (1 if untiered)
     * @param context an execution context for expression evaluation (may be {@code null} if the
     *                resolver doesn't need one, e.g. a literal or rarity-scale value)
     */
    double resolve(RarityDefinition rarity, int tier, ExecutionContext context);
}
