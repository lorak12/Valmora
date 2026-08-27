package org.nakii.valmora.module.modifier.value;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;

/**
 * The default rarity-aware balancing provider (docs/Valmora_Modifier_Framework_Design.docx §5):
 * <pre>
 * value:
 *   base: 10
 *   scaling: { type: RARITY, property: power, operation: MULTIPLY }        # base * rarity.power
 *   scaling: { type: RARITY, property: rank,  operation: ADD, factor: 3 }  # base + rarity.rank*factor
 * </pre>
 * A new rarity added to {@code rarities.yml} automatically participates — nothing here enumerates
 * rarities (§4/§23 hard constraint).
 */
public class RarityScaleValue implements ValueResolver {

    private final double base;
    private final String property;
    private final String operation; // ADD | MULTIPLY
    private final double factor;

    public RarityScaleValue(double base, String property, String operation, double factor) {
        this.base = base;
        this.property = property;
        this.operation = operation;
        this.factor = factor;
    }

    @Override
    public double resolve(RarityDefinition rarity, int tier, ExecutionContext context) {
        if (rarity == null) return base;
        double prop = rarity.getProperty(property);
        if (Double.isNaN(prop)) return base;
        return switch (operation) {
            case "ADD" -> base + prop * factor;
            default -> base * prop; // MULTIPLY
        };
    }
}
