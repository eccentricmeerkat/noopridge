package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** R6-1 (F-02): the debt chart shows v10's per-day debt, gaps for nights without sleep, on max(120 min, worst). */
class SleepRidgeTest {

    private val last = LocalDate.of(2026, 11, 11)

    private fun day(back: Int) = last.minusDays(back.toLong()).toString()

    @Test
    fun fourteenNightsWithGapsNotZeros() {
        val debt = (0 until 20).associate { day(it) to 30.0 + it }
        val tracked = (0 until 20).filter { it != 2 }.map { day(it) }.toSet()
        val c = buildDebtChart(debt, tracked, last)
        assertEquals(DEBT_NIGHTS, c.days.size)
        assertEquals(last, c.days.last())
        // Two nights back has a stored debt but no recorded sleep: a gap.
        assertNull(c.debtMin[DEBT_NIGHTS - 1 - 2])
        assertEquals(13, c.trackedNights)
        assertEquals(30.0, c.lastNight!!, 0.0)
        assertEquals(43.0, c.worst!!, 0.0)
    }

    @Test
    fun scaleNeverBelowTwoHours() {
        val small = buildDebtChart(mapOf(day(0) to 20.0), setOf(day(0)), last)
        assertEquals(DEBT_SCALE_FLOOR_MIN, small.scaleMax, 0.0)
        val big = buildDebtChart(mapOf(day(0) to 250.0), setOf(day(0)), last)
        assertEquals(250.0, big.scaleMax, 0.0)
    }

    /** B2-4a-2: the burnt-orange bar is the worst night, only if it has debt, and never a gap. */
    @Test
    fun worstNightIsTheOneTheBoardDrawsOrange() {
        val debt = mapOf(day(0) to 20.0, day(1) to 90.0, day(2) to 90.0, day(3) to 40.0)
        val tracked = (0..3).map { day(it) }.toSet()
        val c = buildDebtChart(debt, tracked, last)
        assertEquals(DEBT_NIGHTS - 1 - 2, c.worstIndex) // day(1) and day(2) tie: the earlier night, day(2), wins
        // No debt anywhere: nothing is singled out.
        assertNull(buildDebtChart(mapOf(day(0) to 0.0, day(1) to 0.0), setOf(day(0), day(1)), last).worstIndex)
        // A gap is never the worst, however its stored debt reads.
        assertEquals(DEBT_NIGHTS - 1, buildDebtChart(mapOf(day(0) to 10.0, day(1) to 500.0), setOf(day(0)), last).worstIndex)
    }


    /** B2-4a-3: the newest night's asleep hours over the newest need; the true share, never capped. */
    @Test
    fun hoursVsNeededReadsTheNewestNightAndNeed() {
        val h = hoursVsNeeded(listOf(6.0, 7.3), listOf(8.0, 8.1), asleepMinFallback = 0.0)
        assertEquals(7.3, h.sleptH, 0.0)
        assertEquals(8.1, h.neededH, 0.0)
        assertEquals(90, h.percent) // 7.3 / 8.1 = 90.1%
        assertEquals(105, hoursVsNeeded(listOf(8.4), listOf(8.0), 0.0).percent) // past the need reads over 100
        // No trend rows: the stages' asleep minutes stand in, and the need falls back to 8 h.
        val fallback = hoursVsNeeded(emptyList(), emptyList(), asleepMinFallback = 360.0)
        assertEquals(6.0, fallback.sleptH, 0.0)
        assertEquals(8.0, fallback.neededH, 0.0)
        assertEquals(75, fallback.percent)
        // A zero or missing need never divides by zero.
        assertEquals(8.0, hoursVsNeeded(listOf(7.0), listOf(0.0), 0.0).neededH, 0.0)
    }

    /** B2-4a-4: tonight's window is the next window's earliest edge to its deadline; null when the alarm is off. */
    @Test
    fun tonightWakeIsTheNextWindowOrNothing() {
        assertEquals(TonightWake(6 * 60 + 30, 7 * 60), tonightWake(true, 6 * 60 + 30, 30))
        assertEquals(TonightWake(23 * 60 + 50, 20), tonightWake(true, 23 * 60 + 50, 30)) // wraps past midnight
        assertNull(tonightWake(false, 6 * 60 + 30, 30))
    }

    @Test
    fun clockOfMinutesFollowsTheChosenClock() {
        assertEquals("06:30", clockOfMinutes(6 * 60 + 30, true, java.util.Locale.US))
        assertEquals("6:30 AM", clockOfMinutes(6 * 60 + 30, false, java.util.Locale.US))
        assertEquals("00:20", clockOfMinutes(20, true, java.util.Locale.US))
        assertEquals("11:50 PM", clockOfMinutes(23 * 60 + 50, false, java.util.Locale.US))
    }
}
