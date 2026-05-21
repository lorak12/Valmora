package org.nakii.valmora.module.script.variable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.variable.providers.RangeVariableProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("scripting")
class RangeVariableProviderTest {

    private RangeVariableProvider provider;
    private VariableResolver variableResolver;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        ScriptModule scriptModule = mock(ScriptModule.class);
        variableResolver = mock(VariableResolver.class);
        context = mock(ExecutionContext.class);

        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getVariableResolver()).thenReturn(variableResolver);
        ValmoraAPI.setProvider(api);

        provider = new RangeVariableProvider();
    }

    @Test
    void testResolve_literalRange_1to5() {
        Object result = provider.resolve(new String[]{"1", "5"}, context);
        assertEquals(List.of(1, 2, 3, 4, 5), result);
    }

    @Test
    void testResolve_reverseRange_5to1() {
        Object result = provider.resolve(new String[]{"5", "1"}, context);
        assertEquals(List.of(5, 4, 3, 2, 1), result);
    }

    @Test
    void testResolve_sameStartEnd_returnsSingleton() {
        Object result = provider.resolve(new String[]{"3", "3"}, context);
        assertEquals(List.of(3), result);
    }

    @Test
    void testResolve_tooFewSegments_returnsNull() {
        assertNull(provider.resolve(new String[]{"1"}, context));
    }

    @Test
    void testResolve_emptyPath_returnsNull() {
        assertNull(provider.resolve(new String[]{}, context));
    }

    @Test
    void testResolve_nonNumericStart_returnsNull() {
        assertNull(provider.resolve(new String[]{"abc", "5"}, context));
    }

    @Test
    void testResolve_dynamicEnd_resolvedFromVariableResolver() {
        when(variableResolver.resolve("prop.max_level", context)).thenReturn(3);
        Object result = provider.resolve(new String[]{"1", "prop.max_level"}, context);
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void testResolve_dynamicEnd_resolvedAsString() {
        when(variableResolver.resolve("prop.count", context)).thenReturn("4");
        Object result = provider.resolve(new String[]{"1", "prop.count"}, context);
        assertEquals(List.of(1, 2, 3, 4), result);
    }

    @Test
    void testResolve_dynamicEnd_unresolvable_returnsNull() {
        when(variableResolver.resolve("prop.unknown", context)).thenReturn(null);
        assertNull(provider.resolve(new String[]{"1", "prop.unknown"}, context));
    }

    @Test
    void testNamespace_isRange() {
        assertEquals("range", provider.getNamespace());
    }
}
