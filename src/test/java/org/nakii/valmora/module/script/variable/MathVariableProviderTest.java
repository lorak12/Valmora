package org.nakii.valmora.module.script.variable;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.providers.MathVariableProvider;

import static org.junit.jupiter.api.Assertions.*;

class MathVariableProviderTest {

    private final MathVariableProvider provider = new MathVariableProvider();

    @Test
    void getNamespace_returnsMath() {
        assertEquals("math", provider.getNamespace());
    }

    @Test
    void random_returnsValueInZeroToOneRange() {
        ExecutionContext ctx = null; // unused by this provider
        for (int i = 0; i < 20; i++) {
            Object result = provider.resolve(new String[]{"random"}, ctx);
            assertInstanceOf(Double.class, result);
            double d = (double) result;
            assertTrue(d >= 0.0 && d < 1.0, "expected [0,1), got " + d);
        }
    }

    @Test
    void unknownPath_returnsNull() {
        assertNull(provider.resolve(new String[]{"nope"}, null));
    }

    @Test
    void emptyPath_returnsNull() {
        assertNull(provider.resolve(new String[0], null));
    }
}
