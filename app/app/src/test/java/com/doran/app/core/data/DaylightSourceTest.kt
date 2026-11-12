package com.doran.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class DaylightSourceTest {
    @Test
    fun settingWinsOverEverything() {
        assertTrue(DaylightSource.decide(Preferences.SCREEN_DAY, headlightOn = true, clockDay = false))
        assertFalse(DaylightSource.decide(Preferences.SCREEN_NIGHT, headlightOn = false, clockDay = true))
    }

    @Test
    fun autoFollowsHeadlightBeforeClock() {
        assertFalse(DaylightSource.decide(Preferences.SCREEN_AUTO, headlightOn = true, clockDay = true))
        assertTrue(DaylightSource.decide(Preferences.SCREEN_AUTO, headlightOn = false, clockDay = false))
    }

    @Test
    fun autoFallsBackToClockWithoutHeadlight() {
        assertTrue(DaylightSource.decide(Preferences.SCREEN_AUTO, headlightOn = null, clockDay = true))
        assertFalse(DaylightSource.decide(Preferences.SCREEN_AUTO, headlightOn = null, clockDay = false))
    }

    @Test
    fun clockDayIsSevenToNineteen() {
        assertFalse(DaylightSource.isDayAt(at(6, 59)))
        assertTrue(DaylightSource.isDayAt(at(7, 0)))
        assertTrue(DaylightSource.isDayAt(at(18, 59)))
        assertFalse(DaylightSource.isDayAt(at(19, 0)))
    }

    @Test
    fun waitsOnlyUntilNextSwitch() {
        assertEquals(Duration.ofHours(4), DaylightSource.untilNextSwitch(at(15, 0)))
        assertEquals(Duration.ofHours(12), DaylightSource.untilNextSwitch(at(19, 0)))
        assertEquals(Duration.ofMinutes(30), DaylightSource.untilNextSwitch(at(6, 30)))
    }

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 11, 12, hour, minute)
}
