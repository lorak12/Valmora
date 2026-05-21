package org.nakii.valmora.module.script.event;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.impl.GiveEvent;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("scripting")
class GiveEventCompilationTest {

    private GiveEvent factory;

    @BeforeEach
    void setUp() {
        factory = new GiveEvent();
    }

    @Test
    void testGetName_returnsGive() {
        assertEquals("give", factory.getName());
    }

    @Test
    void testCompile_noArgs_returnsNoOpEvent() {
        CompiledEvent event = factory.compile(new String[]{}, EventOptions.DEFAULT);
        assertNotNull(event);
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.getPlayerCaster()).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> event.execute(ctx));
    }

    @Test
    void testCompile_invalidMaterial_returnsNoOpEvent() {
        try (MockedStatic<Material> matMock = mockStatic(Material.class)) {
            matMock.when(() -> Material.matchMaterial("NOT_REAL")).thenReturn(null);
            CompiledEvent event = factory.compile(new String[]{"NOT_REAL:1"}, EventOptions.DEFAULT);
            assertNotNull(event);
            ExecutionContext ctx = mock(ExecutionContext.class);
            when(ctx.getPlayerCaster()).thenReturn(Optional.empty());
            assertDoesNotThrow(() -> event.execute(ctx));
        }
    }

    @Test
    void testCompile_validMaterial_givesItemToPlayer() {
        try (MockedStatic<Material> matMock = mockStatic(Material.class);
             MockedConstruction<ItemStack> itemStacks = mockConstruction(ItemStack.class, (mock, ctx2) -> {
                 when(mock.getType()).thenReturn((Material) ctx2.arguments().get(0));
                 when(mock.getAmount()).thenReturn((Integer) ctx2.arguments().get(1));
             })) {
            matMock.when(() -> Material.matchMaterial("STONE")).thenReturn(Material.STONE);

            CompiledEvent event = factory.compile(new String[]{"STONE:3"}, EventOptions.DEFAULT);

            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            ExecutionContext ctx = mock(ExecutionContext.class);
            when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));

            event.execute(ctx);

            assertEquals(1, itemStacks.constructed().size());
            ItemStack created = itemStacks.constructed().get(0);
            verify(inventory).addItem(created);
            assertEquals(Material.STONE, created.getType());
            assertEquals(3, created.getAmount());
        }
    }

    @Test
    void testCompile_noAmountSuffix_defaultsTo1() {
        try (MockedStatic<Material> matMock = mockStatic(Material.class);
             MockedConstruction<ItemStack> itemStacks = mockConstruction(ItemStack.class, (mock, ctx2) -> {
                 when(mock.getType()).thenReturn((Material) ctx2.arguments().get(0));
                 when(mock.getAmount()).thenReturn((Integer) ctx2.arguments().get(1));
             })) {
            matMock.when(() -> Material.matchMaterial("STONE")).thenReturn(Material.STONE);

            CompiledEvent event = factory.compile(new String[]{"STONE"}, EventOptions.DEFAULT);

            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            ExecutionContext ctx = mock(ExecutionContext.class);
            when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));

            event.execute(ctx);

            assertEquals(1, itemStacks.constructed().size());
            assertEquals(1, itemStacks.constructed().get(0).getAmount());
        }
    }

    @Test
    void testCompile_nonNumericAmount_defaultsTo1() {
        try (MockedStatic<Material> matMock = mockStatic(Material.class);
             MockedConstruction<ItemStack> itemStacks = mockConstruction(ItemStack.class, (mock, ctx2) -> {
                 when(mock.getType()).thenReturn((Material) ctx2.arguments().get(0));
                 when(mock.getAmount()).thenReturn((Integer) ctx2.arguments().get(1));
             })) {
            matMock.when(() -> Material.matchMaterial("STONE")).thenReturn(Material.STONE);

            CompiledEvent event = factory.compile(new String[]{"STONE:abc"}, EventOptions.DEFAULT);

            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            ExecutionContext ctx = mock(ExecutionContext.class);
            when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));

            event.execute(ctx);

            assertEquals(1, itemStacks.constructed().size());
            assertEquals(1, itemStacks.constructed().get(0).getAmount());
        }
    }

    @Test
    void testCompile_notifyOption_sendsMessage() {
        try (MockedStatic<Material> matMock = mockStatic(Material.class);
             MockedConstruction<ItemStack> ignored = mockConstruction(ItemStack.class)) {
            matMock.when(() -> Material.matchMaterial("STONE")).thenReturn(Material.STONE);

            CompiledEvent event = factory.compile(new String[]{"STONE:1"}, new EventOptions(0, true));

            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            ExecutionContext ctx = mock(ExecutionContext.class);
            when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));

            event.execute(ctx);

            verify(player).sendMessage(anyString());
        }
    }
}
