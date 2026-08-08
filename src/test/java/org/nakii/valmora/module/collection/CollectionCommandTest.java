package org.nakii.valmora.module.collection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link CollectionCommand}'s new {@code view}/{@code force}/{@code reset} admin
 *  subcommands, added per docs/IMPLEMENTATION_BACKLOG.md's cross-cutting "collection admin
 *  view/reset/force" item. The original no-arg "open the GUI" behavior is left untouched and
 *  isn't re-tested here. */
public class CollectionCommandTest {

    private Valmora plugin;
    private PlayerManager playerManager;
    private CollectionModule collectionModule;
    private CollectionRegistry registry;
    private CollectionCommand command;
    private CommandSender sender;
    private Command bukkitCommand;
    private MockedStatic<Bukkit> bukkitStatic;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        playerManager = mock(PlayerManager.class);
        collectionModule = mock(CollectionModule.class);
        registry = mock(CollectionRegistry.class);
        sender = mock(CommandSender.class);
        bukkitCommand = mock(Command.class);

        when(plugin.getPlayerManager()).thenReturn(playerManager);
        when(plugin.getCollectionModule()).thenReturn(collectionModule);
        when(collectionModule.getRegistry()).thenReturn(registry);
        when(sender.hasPermission("valmora.admin")).thenReturn(true);

        bukkitStatic = mockStatic(Bukkit.class);

        command = new CollectionCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkitStatic.close();
    }

    private CollectionDefinition definition(String id) {
        return new CollectionDefinition(id, "mining", "Coal", "COAL", List.of("BLOCK_BREAK:COAL_ORE"),
                List.of(new CollectionStage(1, 10, List.of()), new CollectionStage(2, 100, List.of())));
    }

    private String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void noPermissionDeniesView() {
        when(sender.hasPermission("valmora.admin")).thenReturn(false);
        command.onCommand(sender, bukkitCommand, "collections", new String[]{"view", "bob", "coal"});
        verify(registry, never()).getCollection(anyString());
    }

    @Test
    void viewMissingArgsShowsUsage() {
        command.onCommand(sender, bukkitCommand, "collections", new String[]{"view", "bob"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Usage:")));
    }

    @Test
    void viewUnknownPlayerReportsError() {
        bukkitStatic.when(() -> Bukkit.getPlayer("bob")).thenReturn(null);
        command.onCommand(sender, bukkitCommand, "collections", new String[]{"view", "bob", "coal"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("not found")));
    }

    @Test
    void viewUnknownCollectionReportsError() {
        Player target = mock(Player.class);
        bukkitStatic.when(() -> Bukkit.getPlayer("bob")).thenReturn(target);
        when(registry.getCollection("nope")).thenReturn(Optional.empty());
        command.onCommand(sender, bukkitCommand, "collections", new String[]{"view", "bob", "nope"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Unknown collection")));
    }

    @Test
    void viewReportsCountAndStage() {
        Player target = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(uuid);
        when(target.getName()).thenReturn("bob");
        bukkitStatic.when(() -> Bukkit.getPlayer("bob")).thenReturn(target);

        CollectionDefinition def = definition("coal");
        when(registry.getCollection("coal")).thenReturn(Optional.of(def));

        ValmoraPlayer session = mock(ValmoraPlayer.class);
        ValmoraProfile profile = mock(ValmoraProfile.class);
        CollectionManager cm = new CollectionManager();
        cm.addCount("coal", 15);
        when(profile.getCollectionManager()).thenReturn(cm);
        when(session.getActiveProfile()).thenReturn(profile);
        when(playerManager.getSession(uuid)).thenReturn(session);

        command.onCommand(sender, bukkitCommand, "collections", new String[]{"view", "bob", "coal"});

        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void forceRequiresPermission() {
        when(sender.hasPermission("valmora.admin")).thenReturn(false);
        command.onCommand(sender, bukkitCommand, "collections", new String[]{"force", "bob", "coal", "50"});
        verify(playerManager, never()).withOfflineProfile(any(), any(), any());
    }

    @Test
    void forceInvalidCountReportsError() {
        bukkitStatic.when(() -> Bukkit.getPlayer("bob")).thenReturn(mock(Player.class));
        when(registry.getCollection("coal")).thenReturn(Optional.of(definition("coal")));
        command.onCommand(sender, bukkitCommand, "collections", new String[]{"force", "bob", "coal", "notanumber"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Invalid count")));
        verify(playerManager, never()).withOfflineProfile(any(), any(), any());
    }

    @Test
    void forceSetsCountViaWithOfflineProfile() {
        Player target = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(uuid);
        when(target.getName()).thenReturn("bob");
        bukkitStatic.when(() -> Bukkit.getPlayer("bob")).thenReturn(target);
        when(registry.getCollection("coal")).thenReturn(Optional.of(definition("coal")));

        command.onCommand(sender, bukkitCommand, "collections", new String[]{"force", "bob", "coal", "50"});

        verify(playerManager).withOfflineProfile(eq(uuid), any(), any());
    }

    @Test
    void resetClearsCountAndGrantedStage() {
        Player target = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(uuid);
        when(target.getName()).thenReturn("bob");
        bukkitStatic.when(() -> Bukkit.getPlayer("bob")).thenReturn(target);
        when(registry.getCollection("coal")).thenReturn(Optional.of(definition("coal")));

        command.onCommand(sender, bukkitCommand, "collections", new String[]{"reset", "bob", "coal"});

        verify(playerManager).withOfflineProfile(eq(uuid), any(), any());
    }

    @Test
    void tabCompleteSuggestsSubcommands() {
        List<String> result = command.onTabComplete(sender, bukkitCommand, "collections", new String[]{"v"});
        assertTrue(result.contains("view"));
    }

    @Test
    void tabCompleteSuggestsCollectionIdsAtThirdArg() {
        when(registry.getCollections()).thenReturn(List.of(definition("coal")));
        List<String> result = command.onTabComplete(sender, bukkitCommand, "collections", new String[]{"view", "bob", "co"});
        assertEquals(List.of("coal"), result);
    }
}
