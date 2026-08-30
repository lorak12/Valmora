package org.nakii.valmora.module.script.event.impl;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.script.event.EventOptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers the non-player {@link LivingEntity} heal path directly (no Valmora profile involved);
 *  the {@link org.bukkit.entity.Player} path mirrors {@code HealMechanic}'s already-tested logic. */
class HealEventFactoryTest {

    private final HealEventFactory factory = new HealEventFactory();

    @Test
    void getNameReturnsHeal() {
        assertEquals("heal", factory.getName());
    }

    @Test
    void tooFewArgsIsNoOp() {
        var ctx = new SimpleExecutionContext(mock(LivingEntity.class), null, null, null);
        assertDoesNotThrow(() -> factory.compile(new String[]{"@self"}, EventOptions.DEFAULT).execute(ctx));
    }

    @Test
    void healsANonPlayerEntityClampedToMaxHealth() {
        LivingEntity self = mock(LivingEntity.class);
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(maxHealthAttr.getValue()).thenReturn(20.0);
        when(self.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);
        when(self.getHealth()).thenReturn(15.0);

        var ctx = new SimpleExecutionContext(self, null, null, null);
        factory.compile(new String[]{"@self", "10"}, EventOptions.DEFAULT).execute(ctx);

        verify(self).setHealth(20.0); // 15 + 10 clamped to max 20
    }

    @Test
    void zeroOrNegativeAmountIsNoOp() {
        LivingEntity self = mock(LivingEntity.class);
        var ctx = new SimpleExecutionContext(self, null, null, null);
        factory.compile(new String[]{"@self", "0"}, EventOptions.DEFAULT).execute(ctx);
        verify(self, never()).setHealth(anyDouble());
    }

    @Test
    void formulaAmountIsEvaluated() {
        LivingEntity self = mock(LivingEntity.class);
        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(maxHealthAttr.getValue()).thenReturn(100.0);
        when(self.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);
        when(self.getHealth()).thenReturn(0.0);

        var ctx = new SimpleExecutionContext(self, null, null, null);
        // No "$" in this token means it's treated as a literal per EventArgs.resolveDouble.
        factory.compile(new String[]{"@self", "5"}, EventOptions.DEFAULT).execute(ctx);

        verify(self).setHealth(5.0);
    }
}
