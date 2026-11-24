package com.noop.metrics

import com.noop.analytics.RestScorer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/*
 * MetricsV2Calc.kt — the pure calculations behind the R9a corrections ([V2Change]). Each is a small function the
 * registry calls, unit-tested on the JVM; none reads storage.
 */

// MARK: - V2-1 · one sleep need (S0-3)

/** The nights the one need looks back over, ending with the night itself: the routine scoring run's window. */
internal const val ONE_NEED_WINDOW_DAYS = 21

/**
 * The one sleep need (hours) for [day]: [RestScorer.personalizedNeedHours] over the asleep hours of the
 * [ONE_NEED_WINDOW_DAYS] calendar days ending on [day], with [age]. Rest scores against the same estimator over
 * the same window (`IntelligenceEngine.routineRestNeedHours`); this reads the daily rows so any past day's need can
 * be rebuilt (DIFFERENCES #19). [asleepMinByDay] maps `yyyy-MM-dd` to asleep minutes.
 */
internal fun oneSleepNeedHours(asleepMinByDay: Map<String, Double?>, day: LocalDate, age: Int?): Double {
    val nightly = (ONE_NEED_WINDOW_DAYS - 1 downTo 0).mapNotNull { back ->
        asleepMinByDay[day.minusDays(back.toLong()).toString()]?.takeIf { it > 0.0 }?.let { it / 60.0 }
    }
    return RestScorer.personalizedNeedHours(nightly, age)
}

// MARK: - V2-4 · the 60-night normal band (D-05, J-04)

internal const val NORMAL_BAND_NIGHTS = 60
internal const val NORMAL_BAND_MIN_NIGHTS = 14
internal const val NORMAL_BAND_OUTLIER_SD = 4.0

/** The narrowest band each signal draws (R9a-c): RHR 1 bpm and HRV 2 ms from the spec; resp and skin by default. */
internal fun normalBandMinWidth(id: MetricId): Double = when (id) {
    MetricId.RESTING_HR -> 1.0
    MetricId.HRV -> 2.0
    MetricId.RESP_RATE -> 0.4
    MetricId.SKIN_TEMP_DEV -> 0.2
    else -> 0.0
}

/**
 * Mean ± 1 SD of the last [NORMAL_BAND_NIGHTS] tracked nights in [history] (oldest first; null = untracked, not
 * counted). Values beyond ± [NORMAL_BAND_OUTLIER_SD] SD of the raw window are dropped and the band recomputed. Under
 * [NORMAL_BAND_MIN_NIGHTS] tracked nights the band is not personal yet: null, and the caller reports the count.
 * The band is never narrower than [minWidth].
 */
internal fun normalBand60(history: List<Double?>, minWidth: Double): NormalRange? {
    val window = history.filterNotNull().takeLast(NORMAL_BAND_NIGHTS)
    if (window.size < NORMAL_BAND_MIN_NIGHTS) return null
    val (rawMean, rawSd) = meanSd(window)
    val kept = if (rawSd > 0.0) window.filter { abs(it - rawMean) <= NORMAL_BAND_OUTLIER_SD * rawSd } else window
    val (mean, sd) = meanSd(kept)
    val half = max(sd, minWidth / 2.0)
    return NormalRange(mean - half, mean + half, NormalRange.Basis.PERSONAL, window.size, mean = mean, sd = sd)
}

/** Mean and sample standard deviation (n − 1; 0 for one value). */
private fun meanSd(xs: List<Double>): Pair<Double, Double> {
    val mean = xs.average()
    if (xs.size < 2) return mean to 0.0
    val variance = xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1)
    return mean to sqrt(variance)
}

/** The count of tracked nights the band is built from, for "9 of 14" before it is personal. */
internal fun normalBandNights(history: List<Double?>): Int = history.count { it != null }.coerceAtMost(NORMAL_BAND_NIGHTS)

// MARK: - V2-8 · one logical day (J-02)

/** The hour the logical day rolls (v10's `LOGICAL_DAY_ROLLOVER_HOUR`): before 04:00 local is the previous day. */
internal const val DAY_ROLLOVER_HOUR = 4

/**
 * The logical day [instant] belongs to, in the [zone] in effect when it was recorded: its local date-time minus
 * [DAY_ROLLOVER_HOUR] hours. A 23- or 25-hour DST day keeps exactly one key, and a sample recorded in another zone
 * keeps that zone's day (travel does not re-key history).
 */
fun dayKeyFor(instant: Instant, zone: ZoneId): DayKey =
    DayKey(instant.atZone(zone).toLocalDateTime().minusHours(DAY_ROLLOVER_HOUR.toLong()).toLocalDate())

/** Today's logical day key in the device zone. */
fun dayKeyNow(zone: ZoneId = ZoneId.systemDefault()): DayKey = dayKeyFor(Instant.now(), zone)
