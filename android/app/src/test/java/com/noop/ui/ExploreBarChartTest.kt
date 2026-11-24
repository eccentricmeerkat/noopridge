package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS-x2-4 item 20: Sleep debt is drawn as bars from zero; every other metric stays a line. */
class ExploreBarChartTest {
    @Test fun sleepDebtAndActiveCaloriesAreTheBarMetrics() {
        assertEquals(CatStyle.BAR, MetricCatalog.forKey("sleep_debt")?.style)
        assertEquals(CatStyle.BAR_DAILY, MetricCatalog.forKey("workout_kcal")?.style)
        val bars = MetricCatalog.entries.filter { it.style != CatStyle.LINE }.map { it.key }
        assertEquals(listOf("workout_kcal", "sleep_debt"), bars.sortedBy { if (it == "workout_kcal") 0 else 1 })
    }

    @Test fun emptyDaysAreFilledUpToToday() {
        val pts = listOf(SeriesPoint("2026-11-18", 300.0), SeriesPoint("2026-11-20", 450.0))
        val out = fillEmptyDays(pts, java.time.LocalDate.parse("2026-11-22"))
        assertEquals(listOf("2026-11-18", "2026-11-19", "2026-11-20", "2026-11-21", "2026-11-22"), out.map { it.day })
        assertEquals(listOf(300.0, 0.0, 450.0, 0.0, 0.0), out.map { it.value })
        assertEquals(emptyList<SeriesPoint>(), fillEmptyDays(emptyList(), java.time.LocalDate.parse("2026-11-22")))
    }

    @Test fun barsStartAtZeroWithHeadroom() {
        val (lo, hi) = exploreBarBounds(listOf(10.0, 45.0, 30.0), null)
        assertEquals(0.0, lo, 0.0)
        assertEquals(49.5, hi, 1e-9)
    }

    @Test fun theNormalBandCanSetTheTop() {
        val (_, hi) = exploreBarBounds(listOf(10.0), 0.0..60.0)
        assertEquals(66.0, hi, 1e-9)
    }

    @Test fun allZeroValuesStillHaveAnAxis() {
        val (lo, hi) = exploreBarBounds(listOf(0.0, 0.0), null)
        assertEquals(0.0, lo, 0.0)
        assertTrue(hi > lo)
    }
}
