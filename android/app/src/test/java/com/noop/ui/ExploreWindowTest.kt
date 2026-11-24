package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** R9d (G-04, V2-10): Explore's windows are calendar days back from the latest reading, and a window under two
 *  readings widens. v10 counted readings, so "M" on a weekly series was 30 weeks and nothing ever widened. */
class ExploreWindowTest {

    private val last = LocalDate.of(2026, 11, 7)
    private fun weekly(weeks: Int) = (weeks - 1 downTo 0).map { SeriesPoint(last.minusWeeks(it.toLong()).toString(), 40.0 + it) }
    private fun daily(days: Int) = (days - 1 downTo 0).map { SeriesPoint(last.minusDays(it.toLong()).toString(), 50.0 + it % 9) }

    @Test
    fun aMonthIsThirtyDaysNotThirtyReadings() {
        val w = weekly(40)
        assertEquals(30, w.windowFor(ExploreRange.Month, calendar = false).size)     // v10: 30 weeks
        assertEquals(5, w.windowFor(ExploreRange.Month, calendar = true).size)       // 30 days: 5 Saturdays
        // A daily series is unchanged.
        val d = daily(60)
        assertEquals(d.windowFor(ExploreRange.Month, calendar = false), d.windowFor(ExploreRange.Month, calendar = true))
    }

    @Test
    fun aWindowUnderTwoReadingsWidens() {
        val w = weekly(40)
        assertEquals(ExploreRange.Month, exploreEffectiveRange(w, ExploreRange.Week, calendar = true))
        assertEquals(ExploreRange.Week, exploreEffectiveRange(w, ExploreRange.Week, calendar = false))   // v10 never widened
        assertEquals(ExploreRange.Week, exploreEffectiveRange(daily(60), ExploreRange.Week, calendar = true))
        // One reading in all: nothing to widen to.
        assertEquals(ExploreRange.Week, exploreEffectiveRange(weekly(1), ExploreRange.Week, calendar = true))
    }
}
