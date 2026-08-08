package org.nakii.valmora.module.item;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.item.impl.ChargeJumpTracker;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link ChargeJumpListener} — the release half of Spring Boots' "To the Moon!" (see
 *  docs/IMPLEMENTATION_BACKLOG.md's item-mechanic-engine CHARGE_JUMP item). Charge-start is
 *  handled by the pre-existing SNEAK ability-trigger pipeline, not this listener. */
public class ChargeJumpListenerTest {

    private final ChargeJumpListener listener = new ChargeJumpListener();

    @Test
    void sneakStartIsIgnored() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        ChargeJumpTracker.startCharge(uuid, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));

        PlayerToggleSneakEvent event = mock(PlayerToggleSneakEvent.class);
        when(event.isSneaking()).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);

        listener.onSneakToggle(event);

        assertTrue(ChargeJumpTracker.isCharging(uuid)); // untouched — start is handled elsewhere
        ChargeJumpTracker.cancelCharge(uuid);
    }

    @Test
    void unsneakWithNoChargeInProgressDoesNothing() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerToggleSneakEvent event = mock(PlayerToggleSneakEvent.class);
        when(event.isSneaking()).thenReturn(false);
        when(event.getPlayer()).thenReturn(player);

        listener.onSneakToggle(event);

        verify(player, never()).setVelocity(any());
    }

    @Test
    void unsneakWithChargeInProgressLaunchesPlayer() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getVelocity()).thenReturn(new Vector(1, 0, 1));
        ChargeJumpTracker.startCharge(uuid, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));

        PlayerToggleSneakEvent event = mock(PlayerToggleSneakEvent.class);
        when(event.isSneaking()).thenReturn(false);
        when(event.getPlayer()).thenReturn(player);

        listener.onSneakToggle(event);

        verify(player).setVelocity(argThat(v -> v.getX() == 1 && v.getZ() == 1 && v.getY() >= 0.4 && v.getY() < 0.5));
        assertFalse(ChargeJumpTracker.isCharging(uuid)); // consumed
    }

    @Test
    void quitCancelsAnyInProgressCharge() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        ChargeJumpTracker.startCharge(uuid, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));

        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onQuit(event);

        assertFalse(ChargeJumpTracker.isCharging(uuid));
    }
}
