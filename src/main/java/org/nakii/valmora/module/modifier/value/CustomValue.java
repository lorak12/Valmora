package org.nakii.valmora.module.modifier.value;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;

/** Delegates to a Java-registered resolver in {@link ModifierValueResolverRegistry} by id. */
public class CustomValue implements ValueResolver {

    private final String resolverId;

    public CustomValue(String resolverId) {
        this.resolverId = resolverId;
    }

    @Override
    public double resolve(RarityDefinition rarity, int tier, ExecutionContext context) {
        ValueResolver resolver = ModifierValueResolverRegistry.get(resolverId);
        return resolver != null ? resolver.resolve(rarity, tier, context) : 0;
    }
}
