package org.nakii.valmora.module.script.variable;

import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.script.ScriptModule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link VariableResolverImpl} — previously untested (see
 * docs/IMPLEMENTATION_BACKLOG.md "Add unit tests for untested modules" / script item).
 */
public class VariableResolverImplTest {

    private final Registry<VariableProvider> providerRegistry = new SimpleRegistry<>();
    private final ScriptModule module = mock(ScriptModule.class);
    private VariableResolverImpl resolver;

    @BeforeEach
    void setUp() {
        when(module.getVariableProviderRegistry()).thenReturn(providerRegistry);
        resolver = new VariableResolverImpl(module);
    }

    private ExecutionContext bareContext() {
        return new SimpleExecutionContext(null, null, new MemoryConfiguration());
    }

    @Test
    void resolve_nullOrEmptyPathReturnsNull() {
        assertNull(resolver.resolve(null, bareContext()));
        assertNull(resolver.resolve("", bareContext()));
    }

    @Test
    void resolve_dispatchesToRegisteredProviderStrippingDollarWrapper() {
        VariableProvider provider = mock(VariableProvider.class);
        when(provider.resolve(any(), any())).thenReturn(42);
        providerRegistry.register("player", provider);

        assertEquals(42, resolver.resolve("$player.stat.HEALTH$", bareContext()));

        // Remaining path (namespace stripped) is passed through, in order.
        verify(provider).resolve(eq(new String[]{"stat", "HEALTH"}), any());
    }

    @Test
    void resolve_worksWithoutDollarWrapper() {
        VariableProvider provider = mock(VariableProvider.class);
        when(provider.resolve(any(), any())).thenReturn("value");
        providerRegistry.register("prop", provider);

        assertEquals("value", resolver.resolve("prop.key", bareContext()));
    }

    @Test
    void resolve_unknownNamespaceWithNoParamsFallbackReturnsNull() {
        assertNull(resolver.resolve("$unknown.path$", bareContext()));
    }

    @Test
    void resolve_fallsBackToConfigurationSectionParams() {
        // Params fallback keys off the first path segment directly (no dedicated "param"
        // namespace prefix at this layer — that's a separate registered VariableProvider).
        MemoryConfiguration params = new MemoryConfiguration();
        params.set("level", 5);
        ExecutionContext ctx = new SimpleExecutionContext(null, null, params);

        assertEquals(5, resolver.resolve("$level$", ctx));
    }

    @Test
    void resolve_fallsBackThroughNestedMapInParams() {
        MemoryConfiguration params = new MemoryConfiguration();
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", "found");
        params.set("outer", nested);
        ExecutionContext ctx = new SimpleExecutionContext(null, null, params);

        assertEquals("found", resolver.resolve("$outer.inner$", ctx));
    }

    @Test
    void resolve_paramsFallbackMissingKeyReturnsNull() {
        MemoryConfiguration params = new MemoryConfiguration();
        params.set("level", 5);
        ExecutionContext ctx = new SimpleExecutionContext(null, null, params);

        assertNull(resolver.resolve("$level.nonexistent$", ctx));
    }

    @Test
    void resolve_guiLoopVarTakesPriorityOverProvider() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));
        GuiExecutionContext ctx = new GuiExecutionContext(player, null);

        Map<String, Object> item = new HashMap<>();
        item.put("id", "diamond_sword");
        ctx.setLoopVar("element", item);

        assertEquals("diamond_sword", resolver.resolve("$element.id$", ctx));
    }

    @Test
    void resolve_guiLoopVarWithNoRemainingPathReturnsWholeValue() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));
        GuiExecutionContext ctx = new GuiExecutionContext(player, null);
        ctx.setLoopVar("element", "raw-value");

        assertEquals("raw-value", resolver.resolve("$element$", ctx));
    }

    @Test
    void resolve_guiContextFallsThroughToParamsWhenNoMatchingLoopVar() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));
        // GuiExecutionContext's own params ctor always uses a fresh MemoryConfiguration,
        // so an unmatched namespace with no loop var resolves to null.
        GuiExecutionContext ctx = new GuiExecutionContext(player, null);

        assertNull(resolver.resolve("$missing.path$", ctx));
    }
}
