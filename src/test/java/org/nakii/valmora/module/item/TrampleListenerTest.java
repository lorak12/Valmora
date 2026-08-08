package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
import org.nakii.valmora.module.item.impl.CancelTrampleMechanic;
import org.nakii.valmora.util.Keys;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link TrampleListener} — added to wire the CANCEL_TRAMPLE mechanic per
 *  docs/IMPLEMENTATION_BACKLOG.md's item-mechanic-engine item (Rancher's Boots' "Farmer's Grace"). */
public class TrampleListenerTest {

    private TrampleListener listener;
    private ValmoraAPI api;
    private ItemManager itemManager;
    private ItemRegistry itemRegistry;
    private MockedStatic<ValmoraAPI> apiStatic;
    private Player player;
    private PlayerInventory inv;

    @BeforeEach
    void setUp() {
        listener = new TrampleListener();
        api = mock(ValmoraAPI.class);
        itemManager = mock(ItemManager.class);
        itemRegistry = mock(ItemRegistry.class);
        when(api.getItemManager()).thenReturn(itemManager);
        when(itemManager.getItemRegistry()).thenReturn(itemRegistry);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        player = mock(Player.class);
        inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
    }

    @AfterEach
    void tearDown() {
        apiStatic.close();
    }

    private PlayerInteractEvent physicalFarmlandEvent() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.FARMLAND);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.PHYSICAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private ItemStack bootsWithId(String itemId) {
        ItemStack boots = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(boots.hasItemMeta()).thenReturn(true);
        when(boots.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING)).thenReturn(itemId);
        return boots;
    }

    private ItemDefinition definitionWithCancelTrample() {
        ConfiguredMechanic mechanic = new ConfiguredMechanic(new CancelTrampleMechanic(), new org.bukkit.configuration.MemoryConfiguration());
        AbilityDefinition ability = mock(AbilityDefinition.class);
        when(ability.getMechanics()).thenReturn(java.util.List.of(mechanic));
        Map<String, AbilityDefinition> abilities = new LinkedHashMap<>();
        abilities.put("farmers_grace", ability);
        ItemDefinition def = mock(ItemDefinition.class);
        when(def.getAbilities()).thenReturn(abilities);
        return def;
    }

    @Test
    void ignoresNonPhysicalActions() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);

        listener.onPhysicalInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(itemRegistry, never()).getItem(anyString());
    }

    @Test
    void ignoresPhysicalOnNonFarmlandBlock() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE_PRESSURE_PLATE);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.PHYSICAL);
        when(event.getClickedBlock()).thenReturn(block);

        listener.onPhysicalInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void ignoresPhysicalWithNullClickedBlock() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.PHYSICAL);
        when(event.getClickedBlock()).thenReturn(null);

        assertDoesNotThrow(() -> listener.onPhysicalInteract(event));
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void doesNotCancelWhenNoBootsWorn() {
        when(inv.getBoots()).thenReturn(null);
        PlayerInteractEvent event = physicalFarmlandEvent();

        listener.onPhysicalInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void doesNotCancelWhenBootsHaveNoItemIdTag() {
        ItemStack vanillaBoots = mock(ItemStack.class);
        when(vanillaBoots.hasItemMeta()).thenReturn(false);
        when(inv.getBoots()).thenReturn(vanillaBoots);
        PlayerInteractEvent event = physicalFarmlandEvent();

        listener.onPhysicalInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(itemRegistry, never()).getItem(anyString());
    }

    @Test
    void doesNotCancelWhenBootsHaveNoCancelTrampleAbility() {
        ItemStack boots = bootsWithId("plain_boots");
        when(inv.getBoots()).thenReturn(boots);
        ItemDefinition def = mock(ItemDefinition.class);
        when(def.getAbilities()).thenReturn(Map.of());
        when(itemRegistry.getItem("plain_boots")).thenReturn(Optional.of(def));

        PlayerInteractEvent event = physicalFarmlandEvent();
        listener.onPhysicalInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void cancelsWhenBootsCarryCancelTrampleMechanic() {
        ItemStack boots = bootsWithId("rancher_boots");
        when(inv.getBoots()).thenReturn(boots);
        ItemDefinition def = definitionWithCancelTrample();
        when(itemRegistry.getItem("rancher_boots")).thenReturn(Optional.of(def));

        PlayerInteractEvent event = physicalFarmlandEvent();
        listener.onPhysicalInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void unknownItemIdDoesNotCancel() {
        ItemStack boots = bootsWithId("nonexistent_item");
        when(inv.getBoots()).thenReturn(boots);
        when(itemRegistry.getItem("nonexistent_item")).thenReturn(Optional.empty());

        PlayerInteractEvent event = physicalFarmlandEvent();
        listener.onPhysicalInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
    }
}
