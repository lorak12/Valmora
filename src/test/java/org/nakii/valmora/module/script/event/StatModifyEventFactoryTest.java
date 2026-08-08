package org.nakii.valmora.module.script.event;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.impl.StatModifyEventFactory;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StatManager.addStat/setStat/resetStat trigger a full stat-recalculation pass touching a dozen
 * other subsystems (see StatManager.recalculateStats) — out of scope for a script-event unit test,
 * so the profile's StatManager is spied out here to verify dispatch only, matching the "verify the
 * DSL routes to the right call" scope of the sibling CounterEventFactoryTest/EconomyCoinsEventFactoryTest.
 */
class StatModifyEventFactoryTest {

    private final StatModifyEventFactory factory = new StatModifyEventFactory();
    private StatManager statManager;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        PlayerManager playerManager = mock(PlayerManager.class);
        when(api.getPlayerManager()).thenReturn(playerManager);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        ValmoraProfile realProfile = new ValmoraProfile("Test");
        statManager = mock(StatManager.class);
        ValmoraProfile profile = spy(realProfile);
        when(profile.getStatManager()).thenReturn(statManager);

        ValmoraPlayer vPlayer = new ValmoraPlayer(uuid);
        vPlayer.addProfile(profile);
        when(playerManager.getSession(uuid)).thenReturn(vPlayer);

        VariableResolver resolver = mock(VariableResolver.class);
        ctx = mock(ExecutionContext.class);
        when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));
        when(ctx.getVariableResolver()).thenReturn(resolver);
    }

    @Test
    void getName_returnsStatModify() {
        assertEquals("stat_modify", factory.getName());
    }

    @Test
    void add_dispatchesToAddStat() {
        factory.compile(new String[]{"add", "strength", "10"}, EventOptions.DEFAULT).execute(ctx);
        verify(statManager).addStat(any(), eq("strength"), eq(10.0));
    }

    @Test
    void set_dispatchesToSetStat() {
        factory.compile(new String[]{"set", "strength", "42"}, EventOptions.DEFAULT).execute(ctx);
        verify(statManager).setStat(any(), eq("strength"), eq(42.0));
    }

    @Test
    void reset_dispatchesToResetStat() {
        factory.compile(new String[]{"reset", "strength"}, EventOptions.DEFAULT).execute(ctx);
        // No value arg -> defaults raw value to "0", but reset doesn't read it at all.
        verify(statManager).resetStat(any(), eq("strength"));
    }

    @Test
    void statIdIsLowercased() {
        factory.compile(new String[]{"set", "STRENGTH", "1"}, EventOptions.DEFAULT).execute(ctx);
        verify(statManager).setStat(any(), eq("strength"), eq(1.0));
    }

    @Test
    void dollarWrappedValue_resolvedThroughVariableResolver() {
        when(ctx.getVariableResolver().resolve(eq("$player.stat.DAMAGE$"), eq(ctx))).thenReturn(7.5);
        factory.compile(new String[]{"add", "strength", "$player.stat.DAMAGE$"}, EventOptions.DEFAULT).execute(ctx);
        verify(statManager).addStat(any(), eq("strength"), eq(7.5));
    }

    @Test
    void unrecognizedAction_isNoOp() {
        factory.compile(new String[]{"multiply", "strength", "2"}, EventOptions.DEFAULT).execute(ctx);
        verifyNoInteractions(statManager);
    }

    @Test
    void tooFewArgs_isNoOp() {
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"add"}, EventOptions.DEFAULT).execute(ctx));
    }

    @Test
    void noPlayerCaster_isNoOp() {
        when(ctx.getPlayerCaster()).thenReturn(Optional.empty());
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"set", "strength", "5"}, EventOptions.DEFAULT).execute(ctx));
        verifyNoInteractions(statManager);
    }
}
