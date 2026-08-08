package org.nakii.valmora.module.script.event;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.impl.EntityEventFactory;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityEventFactoryTest {

    private final ExpressionParser expressionParser = new ExpressionParser();
    private final ScriptModule module = mock(ScriptModule.class);
    private EntityEventFactory factory;

    private LivingEntity caster;
    private LivingEntity target;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        when(module.getExpressionParser()).thenReturn(expressionParser);
        factory = new EntityEventFactory(module);

        caster = mock(LivingEntity.class);
        target = mock(LivingEntity.class);
        VariableResolver resolver = mock(VariableResolver.class);

        // VariableNode (used by health/max_health expressions) resolves via the static
        // ValmoraAPI singleton, not ctx.getVariableResolver() — see ExpressionTest.java.
        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getScriptModule()).thenReturn(module);
        when(module.getVariableResolver()).thenReturn(resolver);
        ValmoraAPI.setProvider(api);

        ctx = mock(ExecutionContext.class);
        when(ctx.getCaster()).thenReturn(caster);
        when(ctx.getTarget()).thenReturn(Optional.of(target));
        when(ctx.getVariableResolver()).thenReturn(resolver);
        when(resolver.resolveTemplate(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getName_returnsEntity() {
        assertEquals("entity", factory.getName());
    }

    @Test
    void malformedInvocation_isNoOp() {
        assertDoesNotThrow(() -> factory.compile(new String[]{"set", "health"}, EventOptions.DEFAULT).execute(ctx));
        assertDoesNotThrow(() -> factory.compile(new String[]{"notset", "health", "10"}, EventOptions.DEFAULT).execute(ctx));
    }

    @Test
    void setHealth_defaultsToTargetSelector() {
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(maxHealthAttr.getValue()).thenReturn(20.0);
        when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);

        factory.compile(new String[]{"set", "health", "15"}, EventOptions.DEFAULT).execute(ctx);

        verify(target).setHealth(15.0);
        verify(caster, never()).setHealth(anyDouble());
    }

    @Test
    void setHealth_clampsToMaxHealth() {
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(maxHealthAttr.getValue()).thenReturn(20.0);
        when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);

        factory.compile(new String[]{"set", "health", "999"}, EventOptions.DEFAULT).execute(ctx);

        verify(target).setHealth(20.0);
    }

    @Test
    void setHealth_clampsToZeroMinimum() {
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(maxHealthAttr.getValue()).thenReturn(20.0);
        when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);

        factory.compile(new String[]{"set", "health", "-5"}, EventOptions.DEFAULT).execute(ctx);

        verify(target).setHealth(0.0);
    }

    @Test
    void setMaxHealth_updatesAttributeBaseValue() {
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);

        factory.compile(new String[]{"set", "max_health", "50"}, EventOptions.DEFAULT).execute(ctx);

        verify(maxHealthAttr).setBaseValue(50.0);
    }

    @Test
    void setMaxHealth_clampsToMinimumOne() {
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);

        factory.compile(new String[]{"set", "max_health", "0"}, EventOptions.DEFAULT).execute(ctx);

        verify(maxHealthAttr).setBaseValue(1.0);
    }

    @Test
    void setName_usesSelfSelector() {
        factory.compile(new String[]{"set", "name", "Boss", "@self"}, EventOptions.DEFAULT).execute(ctx);

        verify(caster).customName(any());
        verify(caster).setCustomNameVisible(true);
        verify(target, never()).customName(any());
    }

    @Test
    void setName_joinsMultiWordValueBeforeSelector() {
        factory.compile(new String[]{"set", "name", "The", "Big", "Boss", "@self"}, EventOptions.DEFAULT).execute(ctx);
        verify(ctx.getVariableResolver()).resolveTemplate(eq("The Big Boss"), eq(ctx));
    }

    @Test
    void expressionValue_supportsArithmeticAndVariables() {
        VariableResolver resolver = ctx.getVariableResolver();
        when(resolver.resolve(eq("$target.max_health$"), eq(ctx))).thenReturn(20.0);
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);

        factory.compile(new String[]{"set", "max_health", "$target.max_health$", "*", "1.5"}, EventOptions.DEFAULT)
                .execute(ctx);

        verify(maxHealthAttr).setBaseValue(30.0);
    }
}
