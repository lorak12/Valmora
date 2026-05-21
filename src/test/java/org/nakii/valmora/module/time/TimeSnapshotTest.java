package org.nakii.valmora.module.time;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TimeSnapshotTest {

    private TimeSnapshot snap(int hour, int minute) {
        return new TimeSnapshot(hour, minute, 1, Phase.EARLY, Season.SPRING, 1, 0, "Early", "Spring");
    }

    @Test
    void testIsDay_hour6_returnsTrue() {
        assertTrue(snap(6, 0).isDay());
    }

    @Test
    void testIsDay_hour17_returnsTrue() {
        assertTrue(snap(17, 59).isDay());
    }

    @Test
    void testIsDay_hour18_returnsFalse() {
        assertFalse(snap(18, 0).isDay());
    }

    @Test
    void testIsDay_hour5_returnsFalse() {
        assertFalse(snap(5, 59).isDay());
    }

    @Test
    void testIsDay_midnight_returnsFalse() {
        assertFalse(snap(0, 0).isDay());
    }

    @Test
    void testTimeOfDayEmote_daytime_returnsSun() {
        assertEquals("☀", snap(12, 0).timeOfDayEmote());
        assertEquals("☀", snap(6, 0).timeOfDayEmote());
        assertEquals("☀", snap(17, 59).timeOfDayEmote());
    }

    @Test
    void testTimeOfDayEmote_evening_returnsMoon() {
        assertEquals("☾", snap(18, 0).timeOfDayEmote());
        assertEquals("☾", snap(21, 59).timeOfDayEmote());
    }

    @Test
    void testTimeOfDayEmote_lateNight_returnsStar() {
        assertEquals("✦", snap(22, 0).timeOfDayEmote());
        assertEquals("✦", snap(23, 59).timeOfDayEmote());
    }

    @Test
    void testTimeOfDayEmote_earlyMorning_returnsMoon() {
        // Hours 0-5: not daytime, not >= 22, so hour < 22 branch returns moon
        assertEquals("☾", snap(0, 0).timeOfDayEmote());
        assertEquals("☾", snap(5, 0).timeOfDayEmote());
    }

    @Test
    void testFormattedTime_paddedCorrectly() {
        assertEquals("06:05", snap(6, 5).formattedTime());
        assertEquals("00:00", snap(0, 0).formattedTime());
        assertEquals("23:59", snap(23, 59).formattedTime());
        assertEquals("12:30", snap(12, 30).formattedTime());
    }

    @Test
    void testEpoch_hasExpectedValues() {
        TimeSnapshot epoch = TimeSnapshot.EPOCH;
        assertEquals(6, epoch.hour());
        assertEquals(0, epoch.minute());
        assertEquals(Phase.EARLY, epoch.phase());
        assertEquals(Season.SPRING, epoch.season());
        assertEquals(1, epoch.year());
        assertEquals(0, epoch.totalDays());
        assertTrue(epoch.isDay());
    }

    @Test
    void testTimeOfDayMiniColor_daytime_returnsYellow() {
        assertEquals("<yellow>", snap(12, 0).timeOfDayMiniColor());
    }

    @Test
    void testTimeOfDayMiniColor_evening_returnsGray() {
        assertEquals("<gray>", snap(20, 0).timeOfDayMiniColor());
    }

    @Test
    void testTimeOfDayMiniColor_lateNight_returnsDarkGray() {
        assertEquals("<dark_gray>", snap(23, 0).timeOfDayMiniColor());
    }
}
