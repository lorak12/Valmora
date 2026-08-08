package org.nakii.valmora.module.calendar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.time.Phase;
import org.nakii.valmora.module.time.Season;
import org.nakii.valmora.module.time.TimeManager;
import org.nakii.valmora.module.time.TimeSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link CalendarCommand} — added per docs/IMPLEMENTATION_BACKLOG.md cross-cutting
 *  "Missing admin commands/tooling" item (no command surface existed for the Calendar module). */
public class CalendarCommandTest {

    private Valmora plugin;
    private CalendarEventModule calendarModule;
    private TimeManager timeManager;
    private CalendarCommand command;
    private CommandSender sender;
    private Command bukkitCommand;

    private boolean onStartFired;
    private boolean onEndFired;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        calendarModule = mock(CalendarEventModule.class);
        timeManager = mock(TimeManager.class);
        sender = mock(CommandSender.class);
        bukkitCommand = mock(Command.class);

        when(plugin.getCalendarEventModule()).thenReturn(calendarModule);
        when(plugin.getTimeManager()).thenReturn(timeManager);
        when(sender.hasPermission("valmora.admin")).thenReturn(true);

        command = new CalendarCommand(plugin);
        onStartFired = false;
        onEndFired = false;
    }

    private CalendarEventDefinition definition(String id) {
        CompiledEvent onStart = ctx -> onStartFired = true;
        CompiledEvent onEnd = ctx -> onEndFired = true;
        CompiledEvent recurring = ctx -> {};
        return new CalendarEventDefinition(id, Season.SUMMER, Phase.EARLY, 1, 30, onStart, onEnd, recurring);
    }

    @Test
    void noPermissionDenies() {
        when(sender.hasPermission("valmora.admin")).thenReturn(false);
        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"list"});
        verify(sender).sendMessage(argThat((net.kyori.adventure.text.Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("don't have permission")));
        verify(calendarModule, never()).getDefinitions();
    }

    @Test
    void noArgsShowsUsage() {
        command.onCommand(sender, bukkitCommand, "calendar", new String[]{});
        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(calendarModule, never()).getDefinitions();
    }

    @Test
    void listShowsRegisteredEvents() {
        CalendarEventDefinition def = definition("harvest_fest");
        when(calendarModule.getDefinitions()).thenReturn(List.of(def));
        when(calendarModule.getActiveEventIds()).thenReturn(new java.util.HashSet<>(List.of("harvest_fest")));

        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"list"});

        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void previewUnknownEventReportsError() {
        when(calendarModule.getDefinition("nope")).thenReturn(null);
        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"preview", "nope"});
        verify(sender).sendMessage(argThat((net.kyori.adventure.text.Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("Unknown calendar event")));
    }

    @Test
    void previewMissingArgReportsUsage() {
        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"preview"});
        verify(sender).sendMessage(argThat((net.kyori.adventure.text.Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("Usage:")));
        verify(calendarModule, never()).getDefinition(anyString());
    }

    @Test
    void previewKnownEventReportsActiveState() {
        CalendarEventDefinition def = definition("harvest_fest");
        when(calendarModule.getDefinition("harvest_fest")).thenReturn(def);
        TimeSnapshot snapshot = new TimeSnapshot(6, 0, 5, Phase.EARLY, Season.SUMMER, 1, 5, "EARLY", "SUMMER");
        when(timeManager.getSnapshot()).thenReturn(snapshot);

        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"preview", "harvest_fest"});

        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void forceStartAddsToActiveAndFiresOnStart() {
        CalendarEventDefinition def = definition("harvest_fest");
        when(calendarModule.getDefinition("harvest_fest")).thenReturn(def);
        java.util.Set<String> active = new java.util.HashSet<>();
        when(calendarModule.getActiveEventIds()).thenReturn(active);

        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"force-start", "harvest_fest"});

        assertTrue(active.contains("harvest_fest"));
        assertTrue(onStartFired);
        assertFalse(onEndFired);
    }

    @Test
    void forceEndRemovesFromActiveAndFiresOnEnd() {
        CalendarEventDefinition def = definition("harvest_fest");
        when(calendarModule.getDefinition("harvest_fest")).thenReturn(def);
        java.util.Set<String> active = new java.util.HashSet<>(List.of("harvest_fest"));
        when(calendarModule.getActiveEventIds()).thenReturn(active);

        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"force-end", "harvest_fest"});

        assertFalse(active.contains("harvest_fest"));
        assertTrue(onEndFired);
        assertFalse(onStartFired);
    }

    @Test
    void unknownSubcommandShowsUsage() {
        command.onCommand(sender, bukkitCommand, "calendar", new String[]{"bogus"});
        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void tabCompleteSuggestsSubcommandsAtFirstArg() {
        List<String> result = command.onTabComplete(sender, bukkitCommand, "calendar", new String[]{"fo"});
        assertTrue(result.contains("force-start"));
        assertTrue(result.contains("force-end"));
        assertFalse(result.contains("list"));
    }

    @Test
    void tabCompleteSuggestsEventIdsAtSecondArg() {
        CalendarEventDefinition def = definition("harvest_fest");
        when(calendarModule.getDefinitions()).thenReturn(List.of(def));
        List<String> result = command.onTabComplete(sender, bukkitCommand, "calendar", new String[]{"preview", "har"});
        assertEquals(List.of("harvest_fest"), result);
    }

    @Test
    void tabCompleteSkipsEventIdsForListSubcommand() {
        List<String> result = command.onTabComplete(sender, bukkitCommand, "calendar", new String[]{"list", "x"});
        assertTrue(result.isEmpty());
    }
}
