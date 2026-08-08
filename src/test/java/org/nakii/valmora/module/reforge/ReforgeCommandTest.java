package org.nakii.valmora.module.reforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.item.Rarity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link ReforgeCommand} — added per docs/IMPLEMENTATION_BACKLOG.md's Reforge module
 *  "expanded reforge tooling (list/preview/force/reset)" item (no command surface existed before). */
public class ReforgeCommandTest {

    private Valmora plugin;
    private ReforgeModule reforgeModule;
    private ReforgeCommand command;
    private CommandSender sender;
    private Command bukkitCommand;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        reforgeModule = mock(ReforgeModule.class);
        sender = mock(CommandSender.class);
        bukkitCommand = mock(Command.class);

        when(plugin.getReforgeModule()).thenReturn(reforgeModule);
        when(sender.hasPermission("valmora.admin")).thenReturn(true);

        command = new ReforgeCommand(plugin);
    }

    private ReforgeDefinition definition(String id) {
        Map<Rarity, Map<String, Double>> byRarity = new EnumMap<>(Rarity.class);
        byRarity.put(Rarity.COMMON, Map.of("strength", 5.0));
        return new ReforgeDefinition(id, "Sharp", List.of(ItemType.SWORD), byRarity, false, 1.0);
    }

    private String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** A plain Mockito ItemStack mock — avoids constructing a real ItemStack, which needs a live
     *  Bukkit item registry not available in this plain-JVM unit test. */
    private ItemStack mockItem() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        return item;
    }

    @Test
    void noPermissionDenies() {
        when(sender.hasPermission("valmora.admin")).thenReturn(false);
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{"list"});
        verify(reforgeModule, never()).getDefinitions();
    }

    @Test
    void noArgsShowsUsage() {
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
        verify(reforgeModule, never()).getDefinitions();
    }

    @Test
    void listShowsRegisteredReforges() {
        when(reforgeModule.getDefinitions()).thenReturn(List.of(definition("sharp")));
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{"list"});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void previewUnknownReportsError() {
        when(reforgeModule.getDefinition("nope")).thenReturn(null);
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{"preview", "nope"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Unknown reforge")));
    }

    @Test
    void previewInvalidRarityReportsError() {
        when(reforgeModule.getDefinition("sharp")).thenReturn(definition("sharp"));
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{"preview", "sharp", "bogus"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Invalid rarity")));
    }

    @Test
    void previewValidShowsBonuses() {
        when(reforgeModule.getDefinition("sharp")).thenReturn(definition("sharp"));
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{"preview", "sharp", "common"});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void forceRequiresPlayer() {
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{"force", "sharp"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Only a player")));
    }

    @Test
    void forceAppliesReforgeToHeldItem() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack held = mockItem();
        ItemStack result = mockItem();
        when(player.hasPermission("valmora.admin")).thenReturn(true);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(held);
        when(reforgeModule.forceApplyReforge(held, "sharp")).thenReturn(result);

        command.onCommand(player, bukkitCommand, "reforge", new String[]{"force", "sharp"});

        verify(inv).setItemInMainHand(result);
        verify(player).sendMessage(argThat((Component c) -> plain(c).contains("Applied reforge")));
    }

    @Test
    void forceInvalidReforgeReportsError() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack held = mockItem();
        when(player.hasPermission("valmora.admin")).thenReturn(true);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(held);
        when(reforgeModule.forceApplyReforge(held, "bogus")).thenReturn(null);

        command.onCommand(player, bukkitCommand, "reforge", new String[]{"force", "bogus"});

        verify(inv, never()).setItemInMainHand(any());
        verify(player).sendMessage(argThat((Component c) -> plain(c).contains("Unknown reforge id")));
    }

    @Test
    void resetRequiresPlayer() {
        command.onCommand(sender, bukkitCommand, "reforge", new String[]{"reset"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Only a player")));
    }

    @Test
    void resetStripsReforgeFromHeldItem() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack held = mockItem();
        ItemStack result = mockItem();
        when(player.hasPermission("valmora.admin")).thenReturn(true);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(held);
        when(reforgeModule.resetReforge(held)).thenReturn(result);

        command.onCommand(player, bukkitCommand, "reforge", new String[]{"reset"});

        verify(inv).setItemInMainHand(result);
        verify(player).sendMessage(argThat((Component c) -> plain(c).contains("Reforge reset")));
    }

    @Test
    void tabCompleteSuggestsSubcommands() {
        List<String> result = command.onTabComplete(sender, bukkitCommand, "reforge", new String[]{"f"});
        assertTrue(result.contains("force"));
        assertFalse(result.contains("list"));
    }

    @Test
    void tabCompleteSuggestsReforgeIdsForPreviewAndForce() {
        when(reforgeModule.getDefinitions()).thenReturn(List.of(definition("sharp")));
        assertEquals(List.of("sharp"), command.onTabComplete(sender, bukkitCommand, "reforge", new String[]{"preview", "sh"}));
        assertEquals(List.of("sharp"), command.onTabComplete(sender, bukkitCommand, "reforge", new String[]{"force", "sh"}));
    }

    @Test
    void tabCompleteSuggestsRaritiesForPreviewThirdArg() {
        List<String> result = command.onTabComplete(sender, bukkitCommand, "reforge", new String[]{"preview", "sharp", "COM"});
        assertTrue(result.contains("COMMON"));
    }
}
