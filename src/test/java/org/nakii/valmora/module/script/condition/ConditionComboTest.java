package org.nakii.valmora.module.script.condition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Keyword conditions combined with and / or / not / parentheses. */
class ConditionComboTest {

    private final ConditionParser parser = new ConditionParser(new ExpressionParser());
    private final Set<String> flags = new HashSet<>();
    private final AtomicInteger evaluations = new AtomicInteger();
    private final ExecutionContext ctx = mock(ExecutionContext.class);
    private final List<ConfigDiagnostic> diags = new ArrayList<>();
    private LoadScope scope;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        ScriptModule script = mock(ScriptModule.class);
        ValmoraAPI.setProvider(api);
        when(api.getScriptModule()).thenReturn(script);
        when(script.getVariableResolver()).thenReturn(mock(VariableResolver.class));
        // Test keywords with observable behaviour: "flag x" is true when x is set.
        parser.registerKeyword(ConditionKeyword.of("flag", 1, 1, "flag <name>", (text, a) -> c -> {
            evaluations.incrementAndGet();
            return flags.contains(text);
        }));
        scope = LoadScope.enter("Test", "test.yml", "entry", diags::add);
    }

    @AfterEach
    void tearDown() {
        scope.close();
    }

    private boolean test(String condition) {
        return parser.parse(condition).evaluate(ctx);
    }

    @Test
    void orAndParentheses() {
        flags.add("hub");
        flags.add("rich");
        assertTrue(test("flag vip or (flag hub and flag rich)"));
        assertFalse(test("flag vip or (flag hub and flag poor)"));
        assertTrue(test("(flag vip or flag hub) and flag rich"));
        assertTrue(diags.isEmpty(), diags.toString());
    }

    @Test
    void notBindsToTheNextCondition() {
        flags.add("hub");
        assertTrue(test("not flag vip and flag hub"));
        assertTrue(test("!flag vip and flag hub"));
        assertFalse(test("!flag hub"));
        assertTrue(test("!(flag vip or flag x)"));
    }

    @Test
    void mixesKeywordsAndExpressions() {
        flags.add("hub");
        assertTrue(test("flag hub and 3 > 2"));
        assertFalse(test("flag hub and 3 < 2"));
        assertTrue(test("2 > 3 or flag hub"));
        assertTrue(test("flag hub and (1 + 1) == 2"), "a parenthesised expression is not a group");
    }

    @Test
    void orShortCircuits() {
        flags.add("a");
        assertTrue(test("flag a or flag b"));
        assertEquals(1, evaluations.get());
    }

    @Test
    void plainExpressionsStaySingleExpressions() {
        Condition c = parser.parse("$x$ > 1 and $y$ < 2");
        assertInstanceOf(ExpressionCondition.class, c);
    }

    @Test
    void malformedKeywordIsReported() {
        Condition c = parser.parse("health lots");
        assertInstanceOf(ExpressionCondition.class, c);
        assertEquals(1, diags.size());
        assertEquals("usage: health <min>", diags.get(0).hint());
    }

    @Test
    void unbalancedParenthesesAreReported() {
        parser.parse("(flag a or flag b");
        assertTrue(diags.stream().anyMatch(d -> d.message().contains("missing ')'")));
    }

    @Test
    void yamlGroupForms() {
        flags.add("a");
        Condition any = parser.parseValue(java.util.Map.of("any", List.of("flag x", "flag a")));
        assertTrue(any.evaluate(ctx));
        Condition none = parser.parseValue(java.util.Map.of("none", List.of("flag a")));
        assertFalse(none.evaluate(ctx));
    }
}
