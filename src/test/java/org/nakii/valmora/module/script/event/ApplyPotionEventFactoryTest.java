package org.nakii.valmora.module.script.event;

import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.impl.ApplyPotionEventFactory;

import static org.junit.jupiter.api.Assertions.*;

/** Needs a real Bukkit server for {@code Registry.POTION_EFFECT_TYPE}. */
@Tag("mockbukkit")
class ApplyPotionEventFactoryTest {

    private final ApplyPotionEventFactory factory = new ApplyPotionEventFactory();
    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getName_returnsApplyPotion() {
        assertEquals("apply_potion", factory.getName());
    }

    @Test
    void tooFewArgs_isNoOp() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"speed", "1"}, EventOptions.DEFAULT).execute(ctx));
        assertFalse(player.hasPotionEffect(PotionEffectType.SPEED));
    }

    @Test
    void unknownEffect_isNoOp() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"not_a_real_effect", "1", "10"}, EventOptions.DEFAULT).execute(ctx));
    }

    @Test
    void appliesEffectToDefaultSelfSelector() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        CompiledEvent event = factory.compile(new String[]{"speed", "2", "10"}, EventOptions.DEFAULT);

        event.execute(ctx);

        assertTrue(player.hasPotionEffect(PotionEffectType.SPEED));
        assertEquals(1, player.getPotionEffect(PotionEffectType.SPEED).getAmplifier()); // 1-based -> 0-based
        assertEquals(200, player.getPotionEffect(PotionEffectType.SPEED).getDuration()); // 10s * 20 ticks
    }

    @Test
    void negativeDuration_meansInfinite() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        CompiledEvent event = factory.compile(new String[]{"speed", "1", "-1"}, EventOptions.DEFAULT);

        event.execute(ctx);

        assertEquals(org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                player.getPotionEffect(PotionEffectType.SPEED).getDuration());
    }

    @Test
    void malformedNumbers_isNoOp() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"speed", "abc", "10"}, EventOptions.DEFAULT).execute(ctx));
        assertFalse(player.hasPotionEffect(PotionEffectType.SPEED));
    }
}
