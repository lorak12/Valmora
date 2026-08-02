package org.nakii.valmora.module.script.expression;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.api.execution.ExecutionContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ExpressionTest {

    private final ExpressionParser parser = new ExpressionParser();
    private final ExecutionContext context = new DummyExecutionContext();
    private final ValmoraAPI api = mock(ValmoraAPI.class);
    private final ScriptModule scriptModule = mock(ScriptModule.class);
    private final VariableResolver variableResolver = mock(VariableResolver.class);

    static class DummyExecutionContext implements ExecutionContext {
        @Override public org.bukkit.entity.LivingEntity getCaster() { return null; }
        @Override public java.util.Optional<org.bukkit.entity.LivingEntity> getTarget() { return java.util.Optional.empty(); }
        @Override public org.bukkit.Location getLocation() { return null; }
        @Override public org.nakii.valmora.api.scripting.VariableResolver getVariableResolver() { return null; }
        @Override public org.nakii.valmora.api.scripting.TagService getTagService() { return null; }
        @Override public org.bukkit.configuration.ConfigurationSection getParams() { return null; }
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ValmoraAPI.setProvider(api);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getVariableResolver()).thenReturn(variableResolver);
    }

    @Test
    public void testArithmetic() {
        assertEquals(15.0, evaluate("10 + 5"));
        assertEquals(5.0, evaluate("10 - 5"));
        assertEquals(50.0, evaluate("10 * 5"));
        assertEquals(2.0, evaluate("10 / 5"));
        assertEquals(13.0, evaluate("10 + 6 / 2")); // Precedence: 10 + (6/2)
        assertEquals(8.0, evaluate("(10 + 6) / 2")); // Grouping
    }

    @Test
    public void testComparison() {
        assertEquals(true, evaluate("10 == 10"));
        assertEquals(false, evaluate("10 == 5"));
        assertEquals(true, evaluate("10 != 5"));
        assertEquals(true, evaluate("10 > 5"));
        assertEquals(false, evaluate("10 < 5"));
        assertEquals(true, evaluate("10 >= 10"));
        assertEquals(true, evaluate("10 <= 10"));
    }

    @Test
    public void testTernary() {
        assertEquals(1.0, evaluate("10 > 5 ? 1 : 0"));
        assertEquals(0.0, evaluate("10 < 5 ? 1 : 0"));
        assertEquals("yes", evaluate("true ? \"yes\" : \"no\""));
        assertEquals("no", evaluate("false ? \"yes\" : \"no\""));
    }

    @Test
    public void testStrings() {
        assertEquals("hello", evaluate("\"hello\""));
        assertEquals(true, evaluate("\"hello\" == \"hello\""));
        assertEquals(false, evaluate("\"hello\" == \"world\""));
    }

    @Test
    public void testBooleans() {
        assertEquals(true, evaluate("true"));
        assertEquals(false, evaluate("false"));
        assertEquals(true, evaluate("true == true"));
        assertEquals(false, evaluate("true == false"));
        assertEquals(true, evaluate("10 > 5 == true"));
    }

    @Test
    public void testVariables() {
        when(variableResolver.resolve("$player.health$", context)).thenReturn(20.0);
        assertEquals(20.0, evaluate("$player.health$"));
        assertEquals(true, evaluate("$player.health$ > 10"));
        assertEquals(25.0, evaluate("$player.health$ + 5"));
    }

    @Test
    public void testComplex() {
        // (10 + 5 > 20) ? "too big" : (5 * 2 == 10 ? "perfect" : "too small")
        assertEquals("perfect", evaluate("(10 + 5 > 20) ? \"too big\" : (5 * 2 == 10 ? \"perfect\" : \"too small\")"));
    }

    @Test
    public void testFunctions() {
        assertEquals(3.0, evaluate("floor(7 / 2)"));
        assertEquals(4.0, evaluate("ceil(7 / 2)"));
        assertEquals(3.0, evaluate("round(2.6)"));
        assertEquals(5.0, evaluate("abs(0 - 5)"));
        assertEquals(1.0, evaluate("min(1, 2, 3)"));
        assertEquals(3.0, evaluate("max(1, 2, 3)"));
        assertEquals(3.0, evaluate("log10(1000)"));
        assertEquals(8.0, evaluate("pow(2, 3)"));
        // Nested + arithmetic around a function
        assertEquals(130.0, evaluate("130 + min(1000, floor(0 / 1000000))"));
    }

    @Test
    public void testLogical() {
        assertEquals(true, evaluate("10 > 5 and 3 > 1"));
        assertEquals(false, evaluate("10 > 5 and 1 > 3"));
        assertEquals(true, evaluate("10 < 5 or 3 > 1"));
        assertEquals(false, evaluate("10 < 5 or 1 > 3"));
        assertEquals(true, evaluate("\"ZOMBIE\" == \"ZOMBIE\" or \"ZOMBIE\" == \"SKELETON\""));
        assertEquals(false, evaluate("\"CREEPER\" == \"ZOMBIE\" or \"CREEPER\" == \"SKELETON\""));
    }

    private Object evaluate(String expr) {
        return parser.parse(expr).evaluate(context);
    }
}
