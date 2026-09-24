package org.nakii.valmora.module.combat;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.MockedStatic;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link TotemProtectionService} — the VANILLA_CONTROL_AUDIT.md §9 fix making Totem of
 * Undying actually interrupt Valmora's virtual-health death instead of being silently bypassed.
 * Uses MockBukkit for a real player/inventory/event-dispatch — constructing real
 * {@code ItemStack}s (and reading a real inventory's contents) needs a live Material registry
 * that plain Mockito mocking of {@code Bukkit} statics can't provide (see the module's other
 * {@code @Tag("mockbukkit")} tests for the same reason).
 */
@Tag("mockbukkit")
public class TotemProtectionServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private PlayerManager playerManager;
    private MockedStatic<ValmoraAPI> apiStatic;

    private PlayerMock player;
    private PlayerInventory inventory;
    private PlayerState state;
    private StatManager stats;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("ValmoraTest");
        player = server.addPlayer("Steve");
        inventory = player.getInventory();

        ValmoraAPI api = mock(ValmoraAPI.class);
        SystemStats systemStats = mock(SystemStats.class);
        when(systemStats.getHealth()).thenReturn("health");
        when(api.getSystemStats()).thenReturn(systemStats);
        playerManager = mock(PlayerManager.class);
        when(api.getPlayerManager()).thenReturn(playerManager);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        state = mock(PlayerState.class);
        stats = mock(StatManager.class);
        when(stats.getStat("health")).thenReturn(20.0);
    }

    @AfterEach
    void tearDown() {
        apiStatic.close();
        MockBukkit.unmock();
    }

    private ItemStack itemOf(Material material) {
        return new ItemStack(material, 1);
    }

    @Test
    void nonFatalDamageReturnsFalseAndLeavesInventoryUntouched() {
        inventory.setItemInMainHand(itemOf(Material.TOTEM_OF_UNDYING)); // even holding one, shouldn't matter
        when(state.getCurrentHealth()).thenReturn(10.0);

        assertFalse(TotemProtectionService.tryProtect(player, state, stats, 5.0));

        assertEquals(1, inventory.getItemInMainHand().getAmount()); // untouched
    }

    @Test
    void fatalDamageWithoutTotemReturnsFalse() {
        when(state.getCurrentHealth()).thenReturn(5.0);
        inventory.setItemInMainHand(itemOf(Material.AIR));
        inventory.setItemInOffHand(itemOf(Material.AIR));

        assertFalse(TotemProtectionService.tryProtect(player, state, stats, 10.0));

        verifyNoInteractions(playerManager);
    }

    @Test
    void fatalDamageWithMainHandTotemConsumesOneAndSurvives() {
        when(state.getCurrentHealth()).thenReturn(5.0);
        inventory.setItemInMainHand(itemOf(Material.TOTEM_OF_UNDYING));
        inventory.setItemInOffHand(itemOf(Material.AIR));

        boolean result = TotemProtectionService.tryProtect(player, state, stats, 10.0);

        assertTrue(result);
        // Bukkit's ItemStack#setAmount(0) doesn't change the Material — it stays TOTEM_OF_UNDYING
        // at amount 0 (an "empty" stack), same as vanilla's own post-consume item state.
        assertEquals(0, inventory.getItemInMainHand().getAmount());
        // Zeroed out then healed to the 1-HP-out-of-20 equivalent (maxHealth 20 / 20 = 1.0).
        verify(state).reduceHealth(5.0);
        verify(state).heal(1.0, stats);
        verify(playerManager).syncVisualHealth(player, state, stats);
    }

    @Test
    void fatalDamageWithOffHandTotemConsumesFromOffHand() {
        when(state.getCurrentHealth()).thenReturn(5.0);
        inventory.setItemInMainHand(itemOf(Material.AIR));
        inventory.setItemInOffHand(itemOf(Material.TOTEM_OF_UNDYING));

        assertTrue(TotemProtectionService.tryProtect(player, state, stats, 10.0));

        assertEquals(0, inventory.getItemInOffHand().getAmount());
        assertEquals(Material.AIR, inventory.getItemInMainHand().getType()); // untouched
    }

    @Test
    void multipleTotemsInStackOnlyConsumesOne() {
        when(state.getCurrentHealth()).thenReturn(5.0);
        inventory.setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING, 3));
        inventory.setItemInOffHand(itemOf(Material.AIR));

        assertTrue(TotemProtectionService.tryProtect(player, state, stats, 10.0));

        assertEquals(Material.TOTEM_OF_UNDYING, inventory.getItemInMainHand().getType());
        assertEquals(2, inventory.getItemInMainHand().getAmount());
    }

    /** Cancels every EntityResurrectEvent it sees — proves another plugin can veto the totem save. */
    static class CancelResurrectListener implements Listener {
        @EventHandler
        public void onResurrect(EntityResurrectEvent event) {
            event.setCancelled(true);
        }
    }

    @Test
    void cancelledResurrectEventFallsThroughToDeath() {
        plugin.getServer().getPluginManager().registerEvents(new CancelResurrectListener(), plugin);

        when(state.getCurrentHealth()).thenReturn(5.0);
        inventory.setItemInMainHand(itemOf(Material.TOTEM_OF_UNDYING));
        inventory.setItemInOffHand(itemOf(Material.AIR));

        boolean result = TotemProtectionService.tryProtect(player, state, stats, 10.0);

        assertFalse(result);
        assertEquals(1, inventory.getItemInMainHand().getAmount()); // never consumed
        verify(state, never()).heal(anyDouble(), any());
        verifyNoInteractions(playerManager);
    }
}
