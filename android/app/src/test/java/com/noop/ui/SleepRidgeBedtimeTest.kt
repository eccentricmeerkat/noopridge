package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** B2-4b-3: the nights behind the board's Bedtime & Wake card. */
class SleepRidgeBedtimeTest {
    private val zone: ZoneId = ZoneOffset.UTC

    /** A night going to bed at [bed] (hours, evening on the day before) and waking at [wake] on [day]. */
    private fun span(day: LocalDate, bed: Double, wake: Double): Pair<Long, Long> {
        val midnight = day.atStartOfDay(zone).toEpochSecond()
        val onset = if (bed >= 12) midnight - ((24 - bed) * 3600).toLong() else midnight + (bed * 3600).toLong()
        return onset to midnight + (wake * 3600).toLong()
    }

    @Test
    fun typicalIsTheMeanAndOffTargetIsMoreThanFortyFiveMinutesFromIt() {
        val d0 = LocalDate.of(2026, 11, 1)
        // Bedtimes 22:00 x4, 23:30 (a 90 min drift from the mean ~22:18): off target; wake 6:00 throughout.
        val spans = listOf(22.0, 22.0, 22.0, 22.0, 23.5).mapIndexed { i, b -> span(d0.plusDays(i.toLong()), b, 6.0) }
        val bw = buildBedWake(spans, zone)!!
        assertEquals(5, bw.nights.size)
        assertEquals(22 * 60 + 18 - 24 * 60, bw.typicalBedMin)      // mean of 4 x 22:00 and 23:30 = 22:18, folded to −102
        assertEquals(6 * 60, bw.typicalWakeMin)
        assertEquals(listOf(false, false, false, false, true), bw.nights.map { it.offTarget })
        assertEquals(1, bw.offCount)
        assertEquals(LocalDate.of(2026, 11, 1), bw.nights.first().wakeDate)
        assertTrue(bw.nights.all { it.bedMin < 0 }) // evening bedtimes are negative minutes, above the wake times
    }

    @Test
    fun afterMidnightBedtimesStayPositiveAndNearbyNightsAreOnTarget() {
        val d0 = LocalDate.of(2026, 11, 1)
        // 00:30 is +30 min, 23:15 is −45 and 23:45 is −15. The mean is −10, so every night is within 45 minutes of it.
        val bw = buildBedWake(listOf(span(d0, 0.5, 7.0), span(d0.plusDays(1), 23.25, 6.0), span(d0.plusDays(2), 23.75, 6.0)), zone)!!
        assertEquals(listOf(30, -45, -15), bw.nights.map { it.bedMin })
        assertEquals(-10, bw.typicalBedMin)
        assertEquals(0, bw.offCount)
        // Move one bedtime to 02:00 (+120): it is now far from the mean, and only that night is off target.
        val far = buildBedWake(listOf(span(d0, 2.0, 8.0), span(d0.plusDays(1), 23.25, 6.0), span(d0.plusDays(2), 23.25, 6.0), span(d0.plusDays(3), 23.25, 6.0)), zone)!!
        assertEquals(listOf(true, false, false, false), far.nights.map { it.offTarget })
    }

    @Test
    fun needsThreeNights() {
        val d0 = LocalDate.of(2026, 11, 1)
        assertNull(buildBedWake(listOf(span(d0, 22.0, 6.0), span(d0.plusDays(1), 22.0, 6.0)), zone))
        assertNull(buildBedWake(emptyList(), zone))
    }
}
