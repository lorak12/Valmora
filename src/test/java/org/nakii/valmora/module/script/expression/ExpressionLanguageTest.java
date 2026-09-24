package org.nakii.valmora.module.script.expression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.module.script.ScriptModule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The expression language added on top of the original grammar, and its diagnostics. */
class ExpressionLanguageTest {

    private final ExpressionParser parser = new ExpressionParser();
    private final ExecutionContext context = new ExpressionTest.DummyExecutionContext();
    private final ValmoraAPI api = mock(ValmoraAPI.class);
    private final ScriptModule scriptModule = mock(ScriptModule.class);
    private final VariableResolver variableResolver = mock(VariableResolver.class);
    private final List<ConfigDiagnostic> diags = new ArrayList<>();
    private LoadScope scope;

    @BeforeEach
    void setUp() {
        ValmoraAPI.setProvider(api);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getVariableResolver()).thenReturn(variableResolver);
        scope = LoadScope.enter("Test", "test.yml", "entry", diags::add);
    }

    @AfterEach
    void tearDown() {
        scope.close();
    }

    private Object eval(String expr) {
        return parser.parse(expr).evaluate(context);
    }

    @Test
    void moduloAndUnary() {
        assertEquals(1.0, eval("10 % 3"));
        assertEquals(0.0, eval("10 % 0"));
        assertEquals(-6.0, eval("-2 * 3"));
        assertEquals(true, eval("!false"));
        assertEquals(false, eval("not true"));
        assertEquals(true, eval("!(1 > 2)"));
        assertEquals(true, eval("not 1 > 2 == false") instanceof Boolean);
        assertTrue(diags.isEmpty(), diags.toString());
    }

    @Test
    void stringConcatenation() {
        assertEquals("lvl 3", eval("\"lvl \" + 3"));
        assertEquals(3.0, eval("1 + \"2\""), "numbers still win");
        assertEquals("53", eval("\"5\" + \"3\""));
        assertEquals("x", eval("null + \"x\""));
        assertEquals("it's", eval("'it\\'s'"));
        assertEquals("a \"b\"", eval("\"a \\\"b\\\"\""));
    }

    @Test
    void functions() {
        assertEquals(5.0, eval("clamp(9, 0, 5)"));
        assertEquals(-1.0, eval("sign(-4)"));
        assertEquals(true, eval("contains(\"diamond_sword\", \"sword\")"));
        assertEquals(true, eval("startsWith(\"diamond_sword\", \"dia\")"));
        assertEquals("ABC", eval("upper(\"abc\")"));
        assertEquals(3.0, eval("len(\"abc\")"));
        assertEquals("b", eval("default(null, \"b\")"));
        assertEquals(3.0, eval("max(1, 3, 2)"));
        Object r = eval("random(5, 6)");
        assertTrue((Double) r >= 5.0 && (Double) r < 6.0);
        assertTrue(diags.isEmpty(), diags.toString());
    }

    @Test
    void shortCircuitSkipsTheRightSide() {
        when(variableResolver.resolve(eq("$boom$"), any())).thenReturn(true);
        assertEquals(false, eval("false and $boom$"));
        assertEquals(true, eval("true or $boom$"));
        verify(variableResolver, never()).resolve(eq("$boom$"), any());
    }

    @Test
    void unknownFunctionIsReportedWithSuggestion() {
        assertEquals(0.0, eval("flor(2.5)"));
        assertEquals(1, diags.size());
        assertEquals(Severity.ERROR, diags.get(0).severity());
        assertEquals("did you mean 'floor'?", diags.get(0).hint());
    }

    @Test
    void wrongArgumentCountIsReported() {
        parser.parse("pow(2)");
        assertTrue(diags.get(0).message().contains("takes 2 arguments"), diags.get(0).message());
    }

    @Test
    void syntaxErrorsAreReportedButStillEvaluateLikeBefore() {
        assertEquals(3.0, eval("(1 + 2"), "missing ')' is assumed");
        assertTrue(diags.get(0).message().contains("expected ')'"), diags.get(0).message());
        diags.clear();
        parser.parse("1 + 2 )");
        assertTrue(diags.get(0).message().contains("after the end of the expression"));
        diags.clear();
        parser.parse("1 # 2");
        assertTrue(diags.get(0).message().contains("unexpected character '#'"));
        diags.clear();
        assertEquals(true, eval("2 = 2"), "single '=' reported and treated as '=='");
        assertEquals(1, diags.size());
    }

    @Test
    void depthLimitIsReported() {
        String deep = "(".repeat(150) + "1" + ")".repeat(150);
        assertNull(eval(deep));
        assertTrue(diags.get(0).message().contains("max-expression-depth"));
    }

    @Test
    void parserIsThreadSafe() throws Exception {
        scope.close();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                final int n = i;
                results.add(pool.submit(() -> {
                    Expression e = parser.parse("(" + n + " + 1) * 2 - " + n);
                    return e.evaluate(context);
                }));
            }
            for (int i = 0; i < results.size(); i++) {
                assertEquals((i + 1) * 2.0 - i, results.get(i).get());
            }
        } finally {
            pool.shutdownNow();
            scope = LoadScope.enter("Test", "test.yml", "entry", diags::add);
        }
    }
}
