package org.nakii.valmora.module.modifier.value;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;

/** Fixed value, ignoring rarity/tier/context (docs/Valmora_Modifier_Framework_Design.docx §18). */
public class LiteralValue implements ValueResolver {

    private final double value;

    public LiteralValue(double value) {
        this.value = value;
    }

    @Override
    public double resolve(RarityDefinition rarity, int tier, ExecutionContext context) {
        return value;
    }
}
