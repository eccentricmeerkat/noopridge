package com.noop.ui

import com.noop.analytics.VitalityEngine
import com.noop.data.DailyMetric
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R5-2 (E-01): the Baseline model shows only what v10 stores, with v10's uncertainty. */
class BaselineModelTest {

    private val today = LocalDate.of(2026, 11, 11) // a Wednesday; the last Saturday is 7 Nov

    private fun day(back: Int) = today.minusDays(back.toLong()).toString()

    private fun model(
        rows: List<DailyMetric> = emptyList(),
        weekly: Map<String, Map<String, Double>> = emptyMap(),
        range: BaselineRange = BaselineRange.D90,
        earliest: LocalDate? = null,
    ): BaselineModel {
        val inputs = V10MetricInputs(days = rows.sortedBy { it.day }, today = today, age = 40, computedSeries = weekly)
        return buildBaseline(V10MetricRegistry(inputs), today, range, chronoAge = 40, maxHrMeasured = false, earliest = earliest)
    }

    @Test
    fun noBodyAgeUntilV10StoresOne() {
        assertNull(model().bodyAge)
    }

    @Test
    fun bodyAgeIsTheLatestWeeklyValueWithV10sBand() {
        val b = model(weekly = mapOf("body_age" to mapOf("2026-10-31" to 37.0, "2026-11-07" to 35.6), "vitality" to mapOf("2026-11-07" to 61.0))).bodyAge!!
        assertEquals(35.6, b.bodyAge, 0.0)
        assertEquals(VitalityEngine.bandYears, b.bandYears, 0.0)
        assertEquals(LocalDate.of(2026, 11, 7), b.updated)
        assertEquals(4, b.yearsUnder)
        assertEquals(61.0, b.vitality!!, 0.0)
    }

    @Test
    fun monthlyVo2NeedsTwoEstimatesInTheMonth() {
        // October has two estimates, November one: only October gets a monthly mean (§10).
        val vo2 = mapOf("2026-10-10" to 44.0, "2026-10-24" to 46.0, "2026-11-07" to 47.0)
        val v = model(weekly = mapOf("vo2max_est" to vo2)).vo2
        val oct15 = v.days.indexOf(LocalDate.of(2026, 10, 15))
        val nov7 = v.days.indexOf(LocalDate.of(2026, 11, 7))
        assertEquals(45.0, v.monthly[oct15]!!, 1e-9)
        assertNull(v.monthly[nov7])
        assertEquals(47.0, v.estimates[nov7]!!, 0.0)
        assertEquals(3, v.estimates.count { it != null })
        assertEquals(47.0, v.latest!!, 0.0)
    }

    @Test
    fun longRunLineNeedsHalfTheWindow() {
        // 14 nights of RHR: under 15 of 30, so no 30-day line yet; 20 nights draws one.
        val few = model((0 until 14).map { DailyMetric("d", day(it), restingHr = 55) }).rhr
        assertNull(few.latest30)
        val enough = model((0 until 20).map { DailyMetric("d", day(it), restingHr = 55) }).rhr
        assertEquals(55.0, enough.latest30!!, 1e-9)
    }

    @Test
    fun twoYearsStaysUnderFourHundredPoints() {
        val rows = (0 until 730).map { DailyMetric("d", day(it), restingHr = 50 + it % 9, avgHrv = 40.0 + it % 11) }
        val m = model(rows, range = BaselineRange.ALL, earliest = today.minusDays(729))
        assertEquals(730, m.spanDays)
        assertEquals(2, m.bucketDays)
        assertTrue(m.rhr.nightly.size <= 400)
        assertEquals(m.slots.size, m.rhr.nightly.size)
        assertEquals(m.slots.size, m.hrv.mean30.size)
        assertNotNull(m.hrv.band)
    }

    @Test
    fun allReachesBackToTheEarliestDayWithinBounds() {
        assertEquals(90, BaselineRange.D90.spanDays(today.minusDays(900), today))
        assertEquals(90, BaselineRange.ALL.spanDays(null, today))
        assertEquals(90, BaselineRange.ALL.spanDays(today.minusDays(10), today))
        assertEquals(201, BaselineRange.ALL.spanDays(today.minusDays(200), today))
        assertEquals(3650, BaselineRange.ALL.spanDays(today.minusDays(9000), today))
    }

    @Test
    fun nextBodyAgeUpdateIsTheSaturdayAfterToday() {
        assertEquals(LocalDate.of(2026, 11, 14), nextBodyAgeUpdate(today))                    // Wed -> Sat
        assertEquals(LocalDate.of(2026, 11, 14), nextBodyAgeUpdate(LocalDate.of(2026, 11, 8))) // Sun -> Sat
        assertEquals(LocalDate.of(2026, 11, 14), nextBodyAgeUpdate(LocalDate.of(2026, 11, 13))) // Fri -> Sat
        assertEquals(LocalDate.of(2026, 11, 21), nextBodyAgeUpdate(LocalDate.of(2026, 11, 14))) // Sat -> next Sat
    }

    @Test
    fun hrrTrendWordFollowsTheFittedLine() {
        fun trend(vararg d: Int?): HrrTrend {
            val days = (0 until d.size).map { today.minusDays((d.size - it).toLong()) }
            return HrrTrend(days, d.map { it?.toDouble() }, d.count { it != null }, d.lastOrNull { it != null }, null)
        }
        assertEquals(HrrDirection.IMPROVING, hrrDirection(trend(24, null, 28, null, 31, 34)))
        assertEquals(HrrDirection.DECLINING, hrrDirection(trend(34, 31, null, 28, 24)))
        assertEquals(HrrDirection.STEADY, hrrDirection(trend(30, 31, 29, 30, 31)))
        assertNull(hrrDirection(trend(24, 30, 34)))               // under four sessions: no word, no line
        assertNull(hrrTrendLine(trend(24, 30, 34)))
        val line = hrrTrendLine(trend(null, 20, 22, null, 26, 28, null))!!
        assertNull(line[0]); assertNull(line[6])                   // only between the first and last session
        assertEquals(20.0, line[1]!!, 1e-9); assertEquals(28.0, line[5]!!, 1e-9) // exactly linear input is fitted exactly
        assertEquals(24.0, line[3]!!, 1e-9)                        // and the gap day is filled so the line is one run
    }

    @Test
    fun hrrKeepsTheDaysLastSessionAndTheMedian() {
        val days = (0 until 30).map { today.minusDays((29 - it).toLong()) }
        val sessions = listOf(
            HrrSession(today.minusDays(40), 0, 50),        // before the range
            HrrSession(today.minusDays(10), 100, 20),
            HrrSession(today.minusDays(3), 200, 30),
            HrrSession(today.minusDays(3), 300, 34),       // same day, later: shown
        )
        val t = buildHrr(sessions, days)
        assertEquals(3, t.sessions)
        assertEquals(34, t.latest)
        assertEquals(30, t.typical)
        assertEquals(34.0, t.drops[days.indexOf(today.minusDays(3))]!!, 0.0)
        assertEquals(2, t.drops.count { it != null })
    }

    @Test
    fun driversAreV10sFactorsLargestFirst() {
        val d = buildDrivers(
            listOf(
                VitalityEngine.Contribution("rhr", "Resting heart rate", -0.10),
                VitalityEngine.Contribution("sleep", "Sleep duration", 0.0),
                VitalityEngine.Contribution("steps", "Daily steps", 0.20),
            ),
        )
        assertEquals(listOf("steps", "rhr", "sleep"), d.map { it.key })
        assertEquals(listOf(1, -1, 0), d.map { it.direction })
        assertEquals(1.0, d.first().share, 1e-9)
        assertEquals(-0.5, d[1].share, 1e-9)
        assertTrue(buildDrivers(emptyList()).isEmpty())
    }

    @Test
    fun driverYearsAddUpToTheEnginesOwnBodyAgeShift() {
        val inputs = VitalityEngine.Inputs(
            chronoAge = 40.0, restingHR = 55.0, vo2max = 47.0, expectedVO2max = 42.0,
            sleepHours = 7.0, sleepConsistency = 0.6, rmssd = 44.0, rmssdNorm = 33.0, steps = 8500.0,
        )
        val result = VitalityEngine.compute(inputs)!!
        val drivers = buildDrivers(VitalityEngine.contributions(inputs))
        // Unclamped here, so the factors' years sum to body age minus calendar age, to the last digit.
        assertEquals(result.bodyAge - result.chronoAge, drivers.sumOf { it.years }, 1e-9)
        assertTrue(drivers.first { it.key == "vo2max" }.years < 0)   // fitter than expected: younger
        assertTrue(drivers.first { it.key == "consistency" }.years > 0) // below the 75% reference: older
    }

    @Test
    fun signedYearsUsesATrueMinusAndOneDecimal() {
        val us = java.util.Locale.US
        assertEquals("\u22122.6", signedYears(-2.6, us))
        assertEquals("+0.6", signedYears(0.6, us))
        assertEquals("0.0", signedYears(0.04, us))
        assertEquals("\u22126.0", signedYears(-5.96, us))
    }
}
