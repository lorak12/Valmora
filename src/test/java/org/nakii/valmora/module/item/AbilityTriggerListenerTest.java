package org.nakii.valmora.module.item;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.util.Keys;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link AbilityTriggerListener}'s ON_TELEPORT and EQUIP/UNEQUIP dispatch — wired per
 *  docs/IMPLEMENTATION_BACKLOG.md's "ON_DAMAGE_TAKEN and ON_TELEPORT ability triggers"/
 *  "EQUIP/UNEQUIP ability triggers" items (both were enumerated but never dispatched). ON_KILL/
 *  SNEAK/ON_SHOOT/ON_HIT were already-existing dispatch paths, left untouched and untested here.
 *  ON_DAMAGE_TAKEN is dispatched from {@code CombatListener} instead (see docs's "damage-taken
 *  hook in the combat pipeline" note) and is exercised indirectly, not unit-tested directly here —
 *  it shares the exact same {@code AbilityExecutor.fire}/{@code fireHeld} calls this test already
 *  covers the shape of. */
public class AbilityTriggerListenerTest {

    private AbilityTriggerListener listener;
    private ValmoraAPI api;
    private ItemManager itemManager;
    private ItemRegistry itemRegistry;
    private MockedStatic<ValmoraAPI> apiStatic;

    @BeforeEach
    void setUp() {
        listener = new AbilityTriggerListener();
        api = mock(ValmoraAPI.class);
        itemManager = mock(ItemManager.class);
        itemRegistry = mock(ItemRegistry.class);
        when(api.getItemManager()).thenReturn(itemManager);
        when(itemManager.getItemRegistry()).thenReturn(itemRegistry);

        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);
    }

    @AfterEach
    void tearDown() {
        apiStatic.close();
    }

    private ItemStack itemWithId(String itemId) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING)).thenReturn(itemId);
        return item;
    }

    private Player playerWithEmptyInventory() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(mock(ItemStack.class)); // no meta -> fireHeld no-ops
        when(inv.getArmorContents()).thenReturn(new ItemStack[4]); // all null -> fireArmor no-ops
        return player;
    }

    @Test
    void armorChangeFiresUnequipForOldItemAndEquipForNewItem() {
        ItemDefinition oldDef = mock(ItemDefinition.class);
        ItemDefinition newDef = mock(ItemDefinition.class);
        when(itemRegistry.getItem("old_helmet")).thenReturn(Optional.of(oldDef));
        when(itemRegistry.getItem("new_helmet")).thenReturn(Optional.of(newDef));
        when(oldDef.getAbilities()).thenReturn(null);
        when(newDef.getAbilities()).thenReturn(null);

        Player player = mock(Player.class);
        PlayerArmorChangeEvent event = mock(PlayerArmorChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        ItemStack oldItem = itemWithId("old_helmet");
        ItemStack newItem = itemWithId("new_helmet");
        when(event.getOldItem()).thenReturn(oldItem);
        when(event.getNewItem()).thenReturn(newItem);

        listener.onArmorChange(event);

        verify(itemRegistry).getItem("old_helmet");
        verify(itemRegistry).getItem("new_helmet");
    }

    @Test
    void armorChangeIgnoresNullOldOrNewItem() {
        Player player = mock(Player.class);
        PlayerArmorChangeEvent event = mock(PlayerArmorChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getOldItem()).thenReturn(null);
        when(event.getNewItem()).thenReturn(null);

        assertDoesNotThrow(() -> listener.onArmorChange(event));
        verify(itemRegistry, never()).getItem(anyString());
    }

    @Test
    void armorChangeIgnoresItemWithNoMeta() {
        Player player = mock(Player.class);
        ItemStack noMeta = mock(ItemStack.class); // hasItemMeta() unstubbed -> defaults to false

        PlayerArmorChangeEvent event = mock(PlayerArmorChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getOldItem()).thenReturn(noMeta);
        when(event.getNewItem()).thenReturn(null);

        assertDoesNotThrow(() -> listener.onArmorChange(event));
        verify(itemRegistry, never()).getItem(anyString());
    }

    @Test
    void armorChangeIgnoresVanillaItemWithNoItemIdTag() {
        Player player = mock(Player.class);
        ItemStack vanilla = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(vanilla.hasItemMeta()).thenReturn(true);
        when(vanilla.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING)).thenReturn(null);

        PlayerArmorChangeEvent event = mock(PlayerArmorChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getOldItem()).thenReturn(vanilla);
        when(event.getNewItem()).thenReturn(null);

        assertDoesNotThrow(() -> listener.onArmorChange(event));
        verify(itemRegistry, never()).getItem(anyString());
    }

    @Test
    void teleportFiresOnHeldItemAndArmorWithoutThrowing() {
        Player player = playerWithEmptyInventory();
        Location from = mock(Location.class);
        Location to = mock(Location.class);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

        assertDoesNotThrow(() -> listener.onTeleport(event));

        // Held item had no meta and armor was all-null, so nothing should have resolved an item id.
        verify(itemRegistry, never()).getItem(anyString());
    }

    @Test
    void teleportResolvesArmorAbilities() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(mock(ItemStack.class));
        ItemStack boots = itemWithId("boots_of_speed");
        when(inv.getArmorContents()).thenReturn(new ItemStack[]{boots, null, null, null});

        ItemDefinition def = mock(ItemDefinition.class);
        when(def.getAbilities()).thenReturn(null);
        when(itemRegistry.getItem("boots_of_speed")).thenReturn(Optional.of(def));

        Location from = mock(Location.class);
        Location to = mock(Location.class);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

        listener.onTeleport(event);

        verify(itemRegistry).getItem("boots_of_speed");
    }
}
