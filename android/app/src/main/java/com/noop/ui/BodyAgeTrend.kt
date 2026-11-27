package com.noop.ui

import com.noop.analytics.VitalityEngine
import com.noop.data.DailyMetric
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/*
 * RS-1-3 item 8 (Baseline.dc.html: the Pace of aging card and the Trend View sheet). Pure, so every number is tested
 * without a screen. Behind V2Change.BODY_AGE_PACE; the displayed body age itself is unchanged.
 *
 * Pace of aging follows the method published in WHOOP's Healthspan white paper (2025, "What is Pace of Aging?"): assume
 * the last 30 days' averages are kept for six months, and compare that projected age with the current one, which reads
 * six months of data. A rise of exactly six months is 1.0x; the scale runs -1.0x to 3.0x. NOOP applies that method to
 * its OWN body-age engine (VitalityEngine, the same inputs and recipe the weekly body age uses); no WHOOP constants:
 *
 *     A6   = body age from the last 182 days' inputs, at today's age
 *     A30  = body age from the last 30 days' inputs, at today's age + 0.5
 *     pace = clamp((A30 - A6) / 0.5, -1, 3)
 *
 * The body age vs calendar age chart reads the STORED weekly body age (the Body age card's own rows), not a recompute.
 *
 * The input recipe is the weekly body age's (IntelligenceEngine, vitalityContributions): median resting HR and HRV, mean
 * sleep hours and steps. Sleep regularity is the mean of 7-night regularities, so a 182-day window is not marked down
 * against a 30-day one only for being longer (a coefficient of variation grows with the window).
 */

internal object BodyAgeTrendModel {
    const val SIX_MONTHS_DAYS = 182
    const val MONTH_DAYS = 30
    const val WEEKS = 26
    const val PACE_MIN = -1.0
    const val PACE_MAX = 3.0
    /** Under this many weekly points the age chart says it needs more data. */
    const val MIN_CHART_WEEKS = 8
    /** Within this of last month's pace, the chip says Steady. */
    const val STEADY_BAND = 0.05

    private fun median(a: List<Double>): Double? {
        if (a.isEmpty()) return null
        val s = a.sorted(); val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** The rows whose day falls in the [n] calendar days ending on [end]. */
    fun window(days: List<DailyMetric>, end: LocalDate, n: Int): List<DailyMetric> {
        val from = end.minusDays((n - 1).toLong()).toString()
        val to = end.toString()
        return days.filter { it.day in from..to }.sortedBy { it.day }
    }

    /** The mean of the 7-night regularities across [rows] (each run of 7 rows, the last one possibly shorter). */
    fun regularity(rows: List<DailyMetric>): Double? {
        val nights = rows.mapNotNull { it.totalSleepMin }.map { it / 60.0 }.filter { it > 0 }
        val weekly = nights.chunked(7).mapNotNull { VitalityEngine.sleepConsistency(it) }
        return if (weekly.isEmpty()) null else weekly.average()
    }

    /** The body-age engine's inputs over [rows], by the weekly body age's recipe, at [age]. */
    fun inputsOf(rows: List<DailyMetric>, age: Double): VitalityEngine.Inputs {
        val nights = rows.mapNotNull { it.totalSleepMin }.map { it / 60.0 }.filter { it > 0 }
        val steps = rows.mapNotNull { it.steps }.map { it.toDouble() }
        return VitalityEngine.Inputs(
            chronoAge = age,
            restingHR = median(rows.mapNotNull { it.restingHr }.map { it.toDouble() }),
            sleepHours = nights.takeIf { it.isNotEmpty() }?.average(),
            sleepConsistency = regularity(rows),
            rmssd = median(rows.mapNotNull { it.avgHrv }),
            rmssdNorm = VitalityEngine.rmssdNorm(age),
            steps = steps.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** Pace of aging as of [asOf] (rows up to that day), or null without 30 days of history or enough inputs. */
    fun paceAt(days: List<DailyMetric>, asOf: LocalDate, age: Double): Double? {
        val upTo = days.filter { it.day <= asOf.toString() }
        val earliest = upTo.minOfOrNull { it.day }?.let { LocalDate.parse(it) } ?: return null
        if (earliest.isAfter(asOf.minusDays((MONTH_DAYS - 1).toLong()))) return null
        val a6 = VitalityEngine.compute(inputsOf(window(upTo, asOf, SIX_MONTHS_DAYS), age))?.bodyAge ?: return null
        val a30 = VitalityEngine.compute(inputsOf(window(upTo, asOf, MONTH_DAYS), age + 0.5))?.bodyAge ?: return null
        return ((a30 - a6) / 0.5).coerceIn(PACE_MIN, PACE_MAX)
    }

    /** The last [WEEKS] Saturdays on or before [today], oldest first. */
    fun saturdays(today: LocalDate): List<LocalDate> {
        val last = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        return (WEEKS - 1 downTo 0).map { last.minusWeeks(it.toLong()) }
    }

    /**
     * The chart's points: the STORED weekly body age (the same rows the Body age card shows, so the chart can never
     * disagree with the number above it) from the last [WEEKS] weeks, oldest first, each against the calendar age on its
     * day ([ageAt]). No new computation: a week the weekly run did not score has no point.
     */
    fun weekly(storedBodyAge: Map<String, Double>, today: LocalDate, ageAt: (LocalDate) -> Double): List<AgePoint> {
        val from = today.minusWeeks(WEEKS.toLong())
        return storedBodyAge.mapNotNull { (k, v) -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to v } }
            .filter { (d, _) -> d.isAfter(from) && !d.isAfter(today) }
            .sortedBy { it.first }
            .map { (d, v) -> AgePoint(d, v, ageAt(d)) }
    }

    fun build(
        days: List<DailyMetric>,
        today: LocalDate,
        engineAge: Double,
        ageAt: (LocalDate) -> Double,
        vo2Avg6: Double?,
        vo2Avg30: Double?,
        storedBodyAge: Map<String, Double> = emptyMap(),
    ): BodyAgeTrend {
        val pace = paceAt(days, today, engineAge)
        val lastMonth = paceAt(days, today.minusDays(MONTH_DAYS.toLong()), engineAge)
        val weeklyPaces = saturdays(today).mapNotNull { paceAt(days, it, engineAge) }
        val six = inputsOf(window(days, today, SIX_MONTHS_DAYS), engineAge)
        val month = inputsOf(window(days, today, MONTH_DAYS), engineAge)
        val years = VitalityEngine.contributions(six).associate { it.key to VitalityEngine.yearsOf(it.lnHazard) }
        val rows = buildList {
            fun add(f: TrendFactor, six: Double?, month: Double?, years: Double?) {
                if (six != null && month != null) add(TrendRow(f, month, six, month, years))
            }
            // The board's order (VO2 max, resting HR, HRV, activity, regularity), then sleep duration, the engine's sixth.
            // VO2 max is not a body-age input today (D7): its row shows the value and no years.
            add(TrendFactor.VO2_MAX, vo2Avg6, vo2Avg30, null)
            add(TrendFactor.RESTING_HR, six.restingHR, month.restingHR, years["rhr"])
            add(TrendFactor.HRV, six.rmssd, month.rmssd, years["hrv"])
            add(TrendFactor.STEPS, six.steps, month.steps, years["steps"])
            add(TrendFactor.SLEEP_REGULARITY, six.sleepConsistency?.times(100), month.sleepConsistency?.times(100), years["consistency"])
            add(TrendFactor.SLEEP_DURATION, six.sleepHours, month.sleepHours, years["sleep"])
        }
        return BodyAgeTrend(
            pace = pace,
            paceLastMonth = lastMonth,
            pace6mAvg = weeklyPaces.takeIf { it.isNotEmpty() }?.average(),
            rows = rows,
            weekly = weekly(storedBodyAge, today, ageAt),
        )
    }

    /** "Slower", "Faster" or "Steady" against last month's pace; null without both. */
    fun versusLastMonth(t: BodyAgeTrend): PaceTrend? {
        val now = t.pace ?: return null
        val then = t.paceLastMonth ?: return null
        return when {
            abs(now - then) <= STEADY_BAND -> PaceTrend.STEADY
            now < then -> PaceTrend.SLOWER
            else -> PaceTrend.FASTER
        }
    }

    /** The board's sentence: months of body age added (or, below 0x, taken off) per calendar year at [pace]. */
    fun monthsPerYear(pace: Double): Int = Math.round(pace * 12).toInt()

    /** Where [pace] sits on the -1x to 3x scale, 0 to 1. */
    fun scaleFraction(pace: Double): Float = ((pace - PACE_MIN) / (PACE_MAX - PACE_MIN)).toFloat().coerceIn(0f, 1f)
}

internal enum class PaceTrend { SLOWER, STEADY, FASTER }

internal data class AgePoint(val day: LocalDate, val bodyAge: Double, val calendarAge: Double)

/**
 * The body-age factors as the sheet shows them: each row's bar ends, and how good a value is (0 bad to 1 good) for the
 * bar's 9-step colour ramp. Resting HR is better low, sleep duration best near 7.5 h (the engine's own optimum).
 */
internal enum class TrendFactor(val lo: Double, val hi: Double) {
    VO2_MAX(15.0, 70.0),
    RESTING_HR(40.0, 80.0),
    HRV(20.0, 80.0),
    SLEEP_REGULARITY(0.0, 100.0),
    SLEEP_DURATION(4.0, 10.0),
    STEPS(0.0, 15000.0);

    fun goodness(v: Double): Double = when (this) {
        RESTING_HR -> (hi - v) / (hi - lo)
        SLEEP_DURATION -> 1.0 - ((abs(v - 7.5) - 0.5).coerceAtLeast(0.0) / 2.5)
        STEPS -> v / 11000.0
        else -> (v - lo) / (hi - lo)
    }.coerceIn(0.0, 1.0)

    /** Where [v] sits on the bar, 0 to 1. */
    fun fraction(v: Double): Float = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
}

/** One Trend View row: today's value (the 30-day average), the two averages for the markers, and its age impact. */
internal data class TrendRow(val factor: TrendFactor, val now: Double, val avg6: Double, val avg30: Double, val years: Double?)

internal data class BodyAgeTrend(
    val pace: Double?,
    val paceLastMonth: Double?,
    val pace6mAvg: Double?,
    val rows: List<TrendRow>,
    val weekly: List<AgePoint>,
)
