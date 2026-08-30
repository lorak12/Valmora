package org.nakii.valmora.module.enchant;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HitVariableProviderTest {

    private final HitVariableProvider provider = new HitVariableProvider();

    private SimpleExecutionContext ctx() {
        return new SimpleExecutionContext(mock(LivingEntity.class), null, null, null);
    }

    @Test
    void resolvesDamageIsCritAndDamageTypeFromAttachments() {
        var ctx = ctx();
        ctx.set("hit:damage", 12.0);
        ctx.set("hit:is_crit", true);
        ctx.set("hit:damage_type", "MELEE");

        assertEquals(12.0, provider.resolve(new String[]{"damage"}, ctx));
        assertEquals(true, provider.resolve(new String[]{"is_crit"}, ctx));
        assertEquals("MELEE", provider.resolve(new String[]{"damage_type"}, ctx));
    }

    @Test
    void defaultsWhenUnattached() {
        var ctx = ctx();
        assertEquals(0.0, provider.resolve(new String[]{"damage"}, ctx));
        assertEquals(false, provider.resolve(new String[]{"is_crit"}, ctx));
        assertEquals("", provider.resolve(new String[]{"damage_type"}, ctx));
    }

    @Test
    void unknownPathSegmentIsNull() {
        assertNull(provider.resolve(new String[]{"nonsense"}, ctx()));
    }
}
