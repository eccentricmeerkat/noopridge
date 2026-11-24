package com.noop.ui

import com.noop.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** R9c: the Sleep page's night date, its typical bed/wake labels, and per-night consistency. */
class SleepR9cTest {

    private val dubai = ZoneId.of("Asia/Dubai")
    private fun ts(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).atZone(dubai).toEpochSecond()

    private fun withZone(block: () -> Unit) {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(dubai))
        try { block() } finally { TimeZone.setDefault(old) }
    }

    @Test
    fun aNightIsNamedByTheEveningItBelongsTo() {
        // Owner's phone: asleep 02:07, woke 06:17 on Sat 14 Nov. The navigator says Fri 13; the header now agrees.
        assertEquals(LocalDate.of(2026, 11, 13), nightOfWake(ts(14, 6, 17), dubai))
        assertEquals(LocalDate.of(2026, 11, 12), nightOfWake(ts(13, 7, 28), dubai))
    }

    @Test
    fun typicalTimesKeepTheirMinutes() {
        assertEquals("22:54", hourOfDayLabel(-1.1f))
        assertEquals("06:30", hourOfDayLabel(6.5f))
        assertEquals("00:00", hourOfDayLabel(-0.0001f))
        assertEquals("00:00", hourOfDayLabel(23.9999f))
    }

    @Test
    fun consistencyScoresOneBedtimePerNight() = withZone {
        val nights = (1..12).map { d ->
            SleepSession(deviceId = "my-whoop", startTs = ts(d, 22, 20 + d % 3 * 10), endTs = ts(d + 1, 6, 30))
        }
        // Night 12→13 split by a 20-minute wake, and an extra evening block on the 13th.
        val split = listOf(
            SleepSession(deviceId = "my-whoop", startTs = ts(12, 22, 0), endTs = ts(13, 3, 20)),
            SleepSession(deviceId = "my-whoop", startTs = ts(13, 3, 40), endTs = ts(13, 7, 30)),
            SleepSession(deviceId = "my-whoop", startTs = ts(13, 19, 0), endTs = ts(13, 20, 10)),
        )
        val sessions = nights.dropLast(1) + split
        val perBlock = consistencySeries(sessions).latest!!
        val perNight = consistencySeriesPerNight(sessions, null).latest!!
        assertTrue("per night $perNight vs per block $perBlock", perNight > perBlock + 20)
        assertTrue(perNight > 70.0)
    }

    @Test
    fun theDetailPagePlotsTheTilesPerNightScores() = withZone {
        val sessions = (1..10).map { d ->
            SleepSession(deviceId = "my-whoop", startTs = ts(d, 22, 10 + d % 4 * 10), endTs = ts(d + 1, 6, 40))
        }
        val points = consistencyPointsPerNight(sessions, null)
        assertEquals(8, points.size)
        assertEquals("2026-11-11", points.last().first)
        assertEquals(consistencySeriesPerNight(sessions, null).latest!!, points.last().second, 1e-9)
    }
}
