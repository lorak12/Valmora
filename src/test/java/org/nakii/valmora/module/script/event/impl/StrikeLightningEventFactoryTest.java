package org.nakii.valmora.module.script.event.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.script.event.EventOptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrikeLightningEventFactoryTest {

    private final StrikeLightningEventFactory factory = new StrikeLightningEventFactory();

    @Test
    void getNameReturnsStrikeLightning() {
        assertEquals("strike_lightning", factory.getName());
    }

    @Test
    void strikesTheEffectOnlyVariantAtTheDefaultTargetSelectorsLocation() {
        LivingEntity caster = mock(LivingEntity.class);
        LivingEntity target = mock(LivingEntity.class);
        World world = mock(World.class);
        Location loc = new Location(world, 1, 2, 3);
        when(target.getLocation()).thenReturn(loc);

        var ctx = new SimpleExecutionContext(caster, target, null, null);
        factory.compile(new String[]{}, EventOptions.DEFAULT).execute(ctx); // default selector: @target

        verify(world).strikeLightningEffect(loc);
        verify(world, never()).strikeLightning(any());
    }

    @Test
    void noTargetIsANoOp() {
        LivingEntity caster = mock(LivingEntity.class);
        var ctx = new SimpleExecutionContext(caster, null, null, null);
        assertDoesNotThrow(() -> factory.compile(new String[]{}, EventOptions.DEFAULT).execute(ctx));
    }
}
