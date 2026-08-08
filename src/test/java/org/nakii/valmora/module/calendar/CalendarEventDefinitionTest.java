package org.nakii.valmora.module.calendar;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.time.Phase;
import org.nakii.valmora.module.time.Season;
import org.nakii.valmora.module.time.TimeSnapshot;

import static org.junit.jupiter.api.Assertions.*;

/** Covers {@link CalendarEventDefinition#isActive} — the core match logic driving every calendar
 *  event trigger — previously untested directly (only exercised indirectly via
 *  `CalendarCommandTest`). Per docs/IMPLEMENTATION_BACKLOG.md's cross-cutting "unit tests for
 *  untested modules" item. */
public class CalendarEventDefinitionTest {

    private static final CompiledEvent NOOP = ctx -> {};

    private TimeSnapshot snapshot(Phase phase, Season season, int dayInPhase) {
        return new TimeSnapshot(12, 0, dayInPhase, phase, season, 1, 0, phase.name(), season.name());
    }

    @Test
    void matchesExactSeasonPhaseAndDayWindow() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "harvest_fest", Season.AUTUMN, Phase.MID, 5, 10, NOOP, NOOP, NOOP);

        assertTrue(def.isActive(snapshot(Phase.MID, Season.AUTUMN, 5)));
        assertTrue(def.isActive(snapshot(Phase.MID, Season.AUTUMN, 7)));
        assertTrue(def.isActive(snapshot(Phase.MID, Season.AUTUMN, 10)));
    }

    @Test
    void outsideDayWindowDoesNotMatch() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "harvest_fest", Season.AUTUMN, Phase.MID, 5, 10, NOOP, NOOP, NOOP);

        assertFalse(def.isActive(snapshot(Phase.MID, Season.AUTUMN, 4)));
        assertFalse(def.isActive(snapshot(Phase.MID, Season.AUTUMN, 11)));
    }

    @Test
    void wrongSeasonDoesNotMatchEvenIfPhaseAndDayMatch() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "harvest_fest", Season.AUTUMN, Phase.MID, 5, 10, NOOP, NOOP, NOOP);

        assertFalse(def.isActive(snapshot(Phase.MID, Season.SPRING, 7)));
    }

    @Test
    void wrongPhaseDoesNotMatchEvenIfSeasonAndDayMatch() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "harvest_fest", Season.AUTUMN, Phase.MID, 5, 10, NOOP, NOOP, NOOP);

        assertFalse(def.isActive(snapshot(Phase.EARLY, Season.AUTUMN, 7)));
    }

    @Test
    void nullSeasonMatchesAnySeason() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "always_mid", null, Phase.MID, 1, 30, NOOP, NOOP, NOOP);

        assertTrue(def.isActive(snapshot(Phase.MID, Season.SPRING, 15)));
        assertTrue(def.isActive(snapshot(Phase.MID, Season.WINTER, 15)));
        assertFalse(def.isActive(snapshot(Phase.EARLY, Season.SPRING, 15)));
    }

    @Test
    void nullPhaseMatchesAnyPhase() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "always_autumn", Season.AUTUMN, null, 1, 30, NOOP, NOOP, NOOP);

        assertTrue(def.isActive(snapshot(Phase.EARLY, Season.AUTUMN, 1)));
        assertTrue(def.isActive(snapshot(Phase.LATE, Season.AUTUMN, 30)));
        assertFalse(def.isActive(snapshot(Phase.EARLY, Season.WINTER, 1)));
    }

    @Test
    void nullSeasonAndPhaseWithFullDayWindowAlwaysMatches() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "always_on", null, null, 1, 30, NOOP, NOOP, NOOP);

        assertTrue(def.isActive(snapshot(Phase.LATE, Season.WINTER, 30)));
        assertTrue(def.isActive(snapshot(Phase.EARLY, Season.SPRING, 1)));
    }

    @Test
    void gettersExposeConstructorArguments() {
        CalendarEventDefinition def = new CalendarEventDefinition(
                "id", Season.SUMMER, Phase.LATE, 3, 20, NOOP, NOOP, NOOP);

        assertEquals("id", def.getId());
        assertEquals(Season.SUMMER, def.getSeason());
        assertEquals(Phase.LATE, def.getPhase());
        assertEquals(3, def.getDayStart());
        assertEquals(20, def.getDayEnd());
        assertSame(NOOP, def.getOnStart());
        assertSame(NOOP, def.getOnEnd());
        assertSame(NOOP, def.getRecurringDaily());
    }
}
