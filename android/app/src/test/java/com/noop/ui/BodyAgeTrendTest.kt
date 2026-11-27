package com.noop.ui

import com.noop.analytics.VitalityEngine
import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * RS-1-3 item 8: Pace of aging (the Healthspan white paper's 30-day vs 6-month projection on NOOP's body-age engine).
 * Synthetic histories recover SEVERAL injected changes, in both directions, not one matched value (AGENTS.md).
 */
class BodyAgeTrendTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val age = 40.0

    /** 200 days at a steady baseline; the last 30 days' resting HR is [recentRhr]. */
    private fun history(recentRhr: Int, days: Int = 200, baseRhr: Int = 65): List<DailyMetric> = (0 until days).map { back ->
        DailyMetric(
            deviceId = "d", day = today.minusDays(back.toLong()).toString(),
            totalSleepMin = 450.0, restingHr = if (back < 30) recentRhr else baseRhr, avgHrv = 40.0, steps = 7000,
        )
    }

    /** What the method should give for a resting-HR change alone: 1 + its years over half a year (HRV norm drift aside). */
    private fun expected(recentRhr: Int, baseRhr: Int = 65): Double {
        val ln = (recentRhr - baseRhr) / 10.0 * 0.100
        return (1.0 + VitalityEngine.yearsOf(ln) / 0.5).coerceIn(-1.0, 3.0)
    }

    @Test fun steadyInputsAgeAtRealTime() {
        assertEquals(1.0, BodyAgeTrendModel.paceAt(history(65), today, age)!!, 0.05)
    }

    @Test fun recoversSeveralInjectedImprovementsAndWorsenings() {
        // Better recent months pace under 1x, much better below 0x, worse over 1x, much worse clamps at 3x.
        listOf(64, 62, 60, 58, 55, 67, 70, 75, 90).forEach { rhr ->
            val got = BodyAgeTrendModel.paceAt(history(rhr), today, age)!!
            assertEquals("resting HR $rhr", expected(rhr), got, 0.05)
        }
        assertTrue(BodyAgeTrendModel.paceAt(history(58), today, age)!! < 0.0)
        assertEquals(3.0, BodyAgeTrendModel.paceAt(history(90), today, age)!!, 0.0)
        assertEquals(-1.0, BodyAgeTrendModel.paceAt(history(40, baseRhr = 80), today, age)!!, 0.0)
    }

    @Test fun underThirtyDaysOfHistoryHasNoPace() {
        assertNull(BodyAgeTrendModel.paceAt(history(65, days = 20), today, age))
    }

    @Test fun lastMonthChipAndSentence() {
        val t = BodyAgeTrendModel.build(history(55), today, age, { age }, null, null)
        // Last month the whole window was steady (1x); now it is far under, so: slower.
        assertEquals(PaceTrend.SLOWER, BodyAgeTrendModel.versusLastMonth(t))
        assertEquals(PaceTrend.STEADY, BodyAgeTrendModel.versusLastMonth(t.copy(pace = 1.02, paceLastMonth = 1.0)))
        assertEquals(PaceTrend.FASTER, BodyAgeTrendModel.versusLastMonth(t.copy(pace = 1.4, paceLastMonth = 1.0)))
        assertNull(BodyAgeTrendModel.versusLastMonth(t.copy(paceLastMonth = null)))
        assertEquals(10, BodyAgeTrendModel.monthsPerYear(0.8))
        assertEquals(0.45f, BodyAgeTrendModel.scaleFraction(0.8), 1e-6f)
    }

    @Test fun rowsCarryBothAveragesAndTheFactorsYears() {
        val t = BodyAgeTrendModel.build(history(55), today, age, { age }, vo2Avg6 = 44.0, vo2Avg30 = 46.0)
        val rhr = t.rows.first { it.factor == TrendFactor.RESTING_HR }
        assertEquals(55.0, rhr.avg30, 0.0)
        assertEquals(65.0, rhr.avg6, 0.0)       // median of 182 days, 152 of them at 65
        assertEquals(0.0, rhr.years!!, 1e-9)    // the 6-month average sits on the reference: no years either way
        val vo2 = t.rows.first { it.factor == TrendFactor.VO2_MAX }
        assertNull(vo2.years)                   // not a body-age input yet (D7)
        assertEquals(TrendFactor.VO2_MAX, t.rows.first().factor)
    }

    @Test fun aFactorWithNoDataIsLeftOutNotZero() {
        val noSteps = history(65).map { it.copy(steps = null) }
        val t = BodyAgeTrendModel.build(noSteps, today, age, { age }, null, null)
        assertTrue(t.rows.none { it.factor == TrendFactor.STEPS || it.factor == TrendFactor.VO2_MAX })
    }

    @Test fun regularityIsTheMeanOfWeeklyRegularitiesSoWindowLengthDoesNotPenalise() {
        // Alternating 6 h / 9 h nights: every 7-night run has the same spread, so 30 and 182 days agree.
        val rows = (0 until 182).map { DailyMetric(deviceId = "d", day = today.minusDays(it.toLong()).toString(), totalSleepMin = if (it % 2 == 0) 360.0 else 540.0) }
        val r30 = BodyAgeTrendModel.regularity(BodyAgeTrendModel.window(rows, today, 30))!!
        val r182 = BodyAgeTrendModel.regularity(BodyAgeTrendModel.window(rows, today, 182))!!
        assertEquals(r182, r30, 0.02)
    }

    @Test fun theChartReadsTheStoredWeeklyBodyAgeInsideSixMonths() {
        val stored = mapOf(
            today.minusWeeks(30).toString() to 41.0,   // older than 26 weeks: left out
            today.minusWeeks(20).toString() to 38.0,
            today.minusWeeks(2).toString() to 36.5,
            today.minusWeeks(10).toString() to 37.2,
            today.plusDays(3).toString() to 30.0,      // future-dated: left out
        )
        val pts = BodyAgeTrendModel.weekly(stored, today, ageAt = { 40.0 + (it.toEpochDay() - today.toEpochDay()) / 365.2425 })
        assertEquals(listOf(38.0, 37.2, 36.5), pts.map { it.bodyAge })
        assertTrue(pts.zipWithNext().all { (a, b) -> b.day.isAfter(a.day) && b.calendarAge > a.calendarAge })
        assertEquals(pts, BodyAgeTrendModel.build(history(65), today, age, { 40.0 + (it.toEpochDay() - today.toEpochDay()) / 365.2425 }, null, null, stored).weekly)
    }
}
