package com.noop.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** BS-x2-3 item 16: the picker's "No data" tag follows the last 90 days of each metric's series. */
class MetricDataTagsTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun pt(daysAgo: Long, v: Double = 1.0) = SeriesPoint(today.minusDays(daysAgo).toString(), v)

    @Test fun emptySeriesHasNoData() = assertFalse(hasRecentData(emptyList(), today))

    @Test fun anyReadingCountsHoweverOld() {
        assertTrue(hasRecentData(listOf(pt(0)), today))
        assertTrue(hasRecentData(listOf(pt(91), pt(400)), today))   // the chart widens to show these, so no tag
    }

    @Test fun aReadingInTheFutureDoesNotCount() = assertFalse(hasRecentData(listOf(pt(-3)), today))

    @Test fun nonFiniteReadingsDoNotCount() = assertFalse(hasRecentData(listOf(pt(1, Double.NaN)), today))

    @Test fun anEmptyAndAPopulatedCatalogue() = runBlocking {
        val keys = listOf("hrv", "avg_hr", "weight", "boom")
        val populated = mapOf("hrv" to listOf(pt(2)), "weight" to emptyList(), "avg_hr" to listOf(pt(1)))
        val tagged = metricsWithoutRecentData(keys, today) { k -> if (k == "boom") error("read failed") else populated[k].orEmpty() }
        assertEquals(setOf("weight", "boom"), tagged)
        val allEmpty = metricsWithoutRecentData(keys, today) { emptyList() }
        assertEquals(keys.toSet(), allEmpty)
    }
}
