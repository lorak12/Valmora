package org.nakii.valmora.module.script.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.condition.ConditionParser;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventParserTest {

    private final SimpleRegistry<EventFactory> registry = new SimpleRegistry<>();
    private final List<String[]> compiledArgs = new ArrayList<>();
    private final List<EventOptions> compiledOptions = new ArrayList<>();
    private final List<ConfigDiagnostic> diags = new ArrayList<>();
    private EventParser parser;
    private LoadScope scope;

    /** A recording factory taking 1-2 arguments. */
    private EventFactory factory(String name) {
        return new EventFactory() {
            @Override public String getName() { return name; }
            @Override public int minArgs() { return 1; }
            @Override public int maxArgs() { return 2; }
            @Override public String usage() { return name + " <a> [b]"; }
            @Override public void references(String[] args, ReferenceSink sink) { }
            @Override public CompiledEvent compile(String[] args, EventOptions options) {
                compiledArgs.add(args);
                compiledOptions.add(options);
                return ctx -> {};
            }
        };
    }

    @BeforeEach
    void setUp() {
        ScriptModule module = mock(ScriptModule.class);
        Valmora plugin = mock(Valmora.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EventParserTest"));
        when(module.getValmora()).thenReturn(plugin);
        when(module.getEventFactoryRegistry()).thenReturn(registry);
        when(module.getConditionParser()).thenReturn(new ConditionParser(new ExpressionParser()));
        registry.register("give", factory("give"));
        registry.register("gui", factory("gui"));
        parser = new EventParser(module);
        scope = LoadScope.enter("Test", "items/a.yml", "blade", diags::add);
    }

    @AfterEach
    void tearDown() {
        scope.close();
    }

    @Test
    void optionsAreSeparatedFromArguments() {
        parser.parse("give STONE:2 notify delay:1s");
        assertArrayEquals(new String[]{"STONE:2"}, compiledArgs.get(0));
        assertTrue(compiledOptions.get(0).notifyPlayer());
        assertTrue(diags.isEmpty());
    }

    @Test
    void delayUnits() {
        assertEquals(20, EventParser.parseDelayTicks("20"));
        assertEquals(20, EventParser.parseDelayTicks("20t"));
        assertEquals(30, EventParser.parseDelayTicks("1.5s"));
        assertEquals(1200, EventParser.parseDelayTicks("1m"));
        assertNull(EventParser.parseDelayTicks("soon"));
        assertNull(EventParser.parseDelayTicks("-5"));
    }

    @Test
    void badDelayIsAnError() {
        parser.parse("give STONE delay:soon");
        assertEquals(1, diags.size());
        assertEquals(Severity.ERROR, diags.get(0).severity());
        assertEquals("blade", diags.get(0).entryId());
    }

    @Test
    void quotedArgumentsKeepTheirQuotesInRawArgs() {
        parser.parse("gui \"a b\" \"say \\\"hi\\\"\"");
        assertArrayEquals(new String[]{"a b", "say \"hi\""}, compiledArgs.get(0));
        assertEquals("\"a b\" \"say \\\"hi\\\"\"", compiledOptions.get(0).rawArgs());
        assertEquals("\"say \\\"hi\\\"\"", compiledOptions.get(0).rawArgsAfter(1, compiledArgs.get(0)));
    }

    @Test
    void wrongArgumentCountIsReportedWithUsage() {
        parser.parse("give");
        parser.parse("give a b c");
        assertEquals(2, diags.size());
        assertEquals("usage: give <a> [b]", diags.get(0).hint());
    }

    @Test
    void listLinesGetTheirIndexInThePath() {
        parser.parseList(List.of("give STONE", "give"));
        assertEquals("[1]", diags.get(0).path());
    }

    @Test
    void unknownEventsAreReportedOnceWithSourcesAndSuggestion() {
        parser.parse("gvie STONE");
        try (LoadScope other = LoadScope.enter("Test", "guis/b.yml", "shop", diags::add)) {
            parser.parse("gvie DIRT");
        }
        List<ConfigDiagnostic> deferred = new ArrayList<>();
        parser.runDeferred(deferred::add);
        assertEquals(1, deferred.size());
        ConfigDiagnostic d = deferred.get(0);
        assertEquals(Severity.WARN, d.severity());
        assertEquals("did you mean 'give'?", d.hint());
        assertTrue(d.message().contains("also used in"), d.message());
        deferred.clear();
        parser.runDeferred(deferred::add);
        assertTrue(deferred.isEmpty(), "reported once");
    }

    @Test
    void lazyEventsAreCheckedInTheirOwnScopeOnceTheirModuleLoads() {
        parser.parse("warp_to");               // module not up yet: lazy
        assertTrue(diags.isEmpty());
        registry.register("warp_to", factory("warp_to"));
        List<ConfigDiagnostic> deferred = new ArrayList<>();
        parser.runDeferred(deferred::add);
        assertEquals(1, deferred.size());
        assertEquals("items/a.yml", deferred.get(0).file());
        assertEquals("blade", deferred.get(0).entryId());
        assertTrue(deferred.get(0).message().startsWith("warp_to: expected"));
    }
}
