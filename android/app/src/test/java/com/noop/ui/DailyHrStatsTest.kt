package com.noop.ui

import com.noop.data.HrBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** BS-x2-3 item 18: Average and Max heart rate come from each day's samples. */
class DailyHrStatsTest {
    private val utc = ZoneId.of("UTC")
    private fun start(day: String) = LocalDate.parse(day).atStartOfDay(utc).toEpochSecond()
    private fun day(d: String, n: Int, avg: Double, max: Double = avg + 20.0, from: Int = 0) =
        (0 until n).map { HrBucket(start(d) + (from + it) * 300L, avg, avg - 5.0, max) }

    @Test fun averageIsTheMeanOfTheDaysBuckets() {
        val out = dailyHrStats(day("2026-11-20", 100, 70.0) + day("2026-11-21", 100, 80.0), HrDayStat.AVERAGE, utc)
        assertEquals(listOf("2026-11-20", "2026-11-21"), out.map { it.day })
        assertEquals(70.0, out[0].value, 1e-9)
        assertEquals(80.0, out[1].value, 1e-9)
    }

    @Test fun maxIsTheDaysHighestSample() {
        val b = day("2026-11-20", 50, 70.0) + HrBucket(start("2026-11-20") + 60_000, 90.0, 85.0, 171.0)
        assertEquals(171.0, dailyHrStats(b, HrDayStat.MAX, utc).single().value, 1e-9)
    }

    @Test fun aDayWithUnderAnHourOfDataGetsNoValue() {
        assertTrue(dailyHrStats(day("2026-11-20", 11, 70.0), HrDayStat.AVERAGE, utc).isEmpty())
        assertEquals(1, dailyHrStats(day("2026-11-20", 12, 70.0), HrDayStat.AVERAGE, utc).size)
    }

    @Test fun daysFollowTheLocalZone() {
        val plus4 = ZoneId.of("Asia/Dubai")
        // 22:00 UTC on the 20th is 02:00 on the 21st in Dubai.
        val buckets = (0 until 20).map { HrBucket(start("2026-11-20") + 22 * 3600L + it * 300L, 60.0, 55.0, 70.0) }
        assertEquals("2026-11-21", dailyHrStats(buckets, HrDayStat.AVERAGE, plus4).single().day)
        assertEquals("2026-11-20", dailyHrStats(buckets, HrDayStat.AVERAGE, utc).single().day)
    }

    @Test fun restingBelowAverageBelowMaxHolds() {
        val b = day("2026-11-20", 60, 75.0, max = 160.0)
        val avg = dailyHrStats(b, HrDayStat.AVERAGE, utc).single().value
        val max = dailyHrStats(b, HrDayStat.MAX, utc).single().value
        assertTrue(58.0 < avg && avg < max)
    }
}
