package org.nakii.valmora.module.script.variable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.providers.TimeVariableProvider;
import org.nakii.valmora.module.time.Phase;
import org.nakii.valmora.module.time.Season;
import org.nakii.valmora.module.time.TimeManager;
import org.nakii.valmora.module.time.TimeSnapshot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("scripting")
class TimeVariableProviderTest {

    private TimeVariableProvider provider;
    private TimeManager timeManager;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        timeManager = mock(TimeManager.class);
        context = mock(ExecutionContext.class);
        when(api.getTimeManager()).thenReturn(timeManager);
        ValmoraAPI.setProvider(api);

        provider = new TimeVariableProvider();
    }

    private void withSnapshot(TimeSnapshot snap) {
        when(timeManager.getSnapshot()).thenReturn(snap);
    }

    private TimeSnapshot makeSnap(int hour, int minute, long totalDays) {
        return new TimeSnapshot(hour, minute, 1, Phase.EARLY, Season.SPRING, 1, totalDays, "Early", "Spring");
    }

    @Test
    void testResolve_hour_returnsHour() {
        withSnapshot(makeSnap(14, 30, 5));
        assertEquals(14, provider.resolve(new String[]{"hour"}, context));
    }

    @Test
    void testResolve_minute_returnsMinute() {
        withSnapshot(makeSnap(14, 30, 5));
        assertEquals(30, provider.resolve(new String[]{"minute"}, context));
    }

    @Test
    void testResolve_isDay_daytimeHour_returnsTrue() {
        withSnapshot(makeSnap(12, 0, 0));
        assertEquals(true, provider.resolve(new String[]{"is_day"}, context));
    }

    @Test
    void testResolve_isDay_nightHour_returnsFalse() {
        withSnapshot(makeSnap(22, 0, 0));
        assertEquals(false, provider.resolve(new String[]{"is_day"}, context));
    }

    @Test
    void testResolve_season_returnsSeasonName() {
        withSnapshot(TimeSnapshot.EPOCH);
        assertEquals("Spring", provider.resolve(new String[]{"season"}, context));
    }

    @Test
    void testResolve_phase_returnsPhaseName() {
        withSnapshot(TimeSnapshot.EPOCH);
        assertEquals("Early", provider.resolve(new String[]{"phase"}, context));
    }

    @Test
    void testResolve_year_returnsYear() {
        withSnapshot(TimeSnapshot.EPOCH);
        assertEquals(1, provider.resolve(new String[]{"year"}, context));
    }

    @Test
    void testResolve_totalDays_returnsTotalDays() {
        withSnapshot(makeSnap(6, 0, 100));
        assertEquals(100L, provider.resolve(new String[]{"total_days"}, context));
    }

    @Test
    void testResolve_totalMinutes_calculatesCorrectly() {
        // totalDays=1, hour=6, minute=30 → 1*24*60 + 6*60 + 30 = 1440 + 360 + 30 = 1830
        withSnapshot(makeSnap(6, 30, 1));
        Object result = provider.resolve(new String[]{"total_minutes"}, context);
        assertEquals(1830L, result);
    }

    @Test
    void testResolve_emptyPath_returnsNull() {
        assertNull(provider.resolve(new String[]{}, context));
    }

    @Test
    void testResolve_unknownKey_returnsNull() {
        withSnapshot(TimeSnapshot.EPOCH);
        assertNull(provider.resolve(new String[]{"foobar"}, context));
    }

    @Test
    void testResolve_nullTimeManager_returnsNull() {
        ValmoraAPI api2 = mock(ValmoraAPI.class);
        when(api2.getTimeManager()).thenReturn(null);
        ValmoraAPI.setProvider(api2);
        assertNull(provider.resolve(new String[]{"hour"}, context));
    }

    @Test
    void testNamespace_isTime() {
        assertEquals("time", provider.getNamespace());
    }
}
