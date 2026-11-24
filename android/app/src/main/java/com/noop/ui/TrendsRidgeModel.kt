package com.noop.ui

import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.MetricRegistry
import com.noop.metrics.MetricsV2
import com.noop.metrics.NormalRange
import com.noop.metrics.V2Change
import com.noop.ui.ridge.chart.Axis
import com.noop.ui.ridge.chart.SleepNight
import com.noop.ui.ridge.chart.heartAxis
import com.noop.ui.ridge.chart.isOutlier
import com.noop.ui.ridge.chart.percentChange
import com.noop.ui.ridge.chart.rollingMean
import com.noop.ui.ridge.chart.sleepHoursAxis
import com.noop.ui.ridge.chart.sleepNight
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The Ridge Trends page model (R4a): every value is read through [MetricRegistry] (Charge, Effort, Rest, RHR, HRV,
 * sleep duration and the v10 normal range), plus the stage minutes from the same daily rows. Nothing is re-scored;
 * the page only averages and windows what v10 stores. One slot per logical day, oldest first, so a missing day is a
 * gap (R3) and every chart shares the day key (R8).
 */

/** The page's range pills (R-02): they scope the daily charts only. */
internal enum class RidgeTrendsRange(val days: Int) { D7(7), D14(14), D30(30) }

/** Where a heart signal's latest 7-day mean sits against the normal range. */
internal enum class NormalPosition { ABOVE, BELOW, WITHIN, NO_BAND }

internal data class ChargeTrend(
    val values: List<Double?>,
    val mean7: List<Double?>,
    val average: Double?,
    val thisWeek: Double?,
    val lastWeek: Double?,
    val today: Double?,
    val effortAvg: Double?,
    val restAvg: Double?,
    val tracked: Int,
)

/**
 * Effort over the range (B2-2a-2): the daily series, its 7-day mean, the week's two 7-day means and today. [hardDays]
 * counts the days at or above [EffortBand.HIGH] (Effort ≥ 67, the Intensity card's High bound) — the app's existing
 * threshold, not a new number.
 */
internal data class EffortTrend(
    val values: List<Double?>,
    val mean7: List<Double?>,
    val average: Double?,
    val thisWeek: Double?,
    val lastWeek: Double?,
    val today: Double?,
    val hardDays: Int,
    val tracked: Int,
)

/**
 * One day's Calories (B2-2a-3): [base] is the resting-and-everyday portion — the imported `basalKcal` on a split day,
 * or the whole-day `activeKcalEst` on a strap-only day; [active] is the imported workout energy (null when the day
 * cannot be split, per checkpoint Q2). Both null means the day is untracked.
 */
internal data class CaloriesDay(
    val date: LocalDate,
    val base: Double?,
    val active: Double?,
    val isToday: Boolean,
) {
    val total: Double? get() = base?.let { it + (active ?: 0.0) }
}

internal data class CaloriesTrend(
    val days: List<CaloriesDay>,
    /** Mean whole-day burn (base + active) over the tracked full days, today excluded. */
    val totalAvg: Double?,
    /** Mean active burn over the same days (0 on days without a split). */
    val activeAvg: Double?,
    val todayTotal: Double?,
    /** Round(active / total) as a whole percent, null when no split or no tracked days. */
    val activeShare: Int?,
    val hasSplit: Boolean,
    val tracked: Int,
    val axis: Axis,
)

internal data class HeartSeries(
    val values: List<Double?>,
    val mean7: List<Double?>,
    val outlier: List<Boolean>,
    /** The personal normal range, null until v10 trusts the baseline (14 nights). */
    val band: ClosedFloatingPointRange<Double>?,
    val bandNights: Int,
    val latest7: Double?,
    val changePct: Int?,
    val position: NormalPosition,
    val axis: Axis,
)

internal data class HeartTrend(val rhr: HeartSeries, val hrv: HeartSeries, val tracked: Int)

internal data class SleepTrend(
    val nights: List<SleepNight>,
    /** Each night's one sleep need in hours (R9b, §7.6), drawn as a dashed line; empty without [V2Change.ONE_SLEEP_NEED]. */
    val needHours: List<Double?> = emptyList(),
    val axis: Axis,
    val avgAsleepMin: Double?,
    /** The board's "regularity" stat: the mean imported sleep_consistency (0–100) over tracked nights. */
    val regularity: Double? = null,
    val tracked: Int,
)

internal data class TrendsRidgeModel(
    val range: RidgeTrendsRange,
    val days: List<LocalDate>,
    val charge: ChargeTrend,
    val effort: EffortTrend,
    val calories: CaloriesTrend,
    val heart: HeartTrend,
    val sleep: SleepTrend,
)

/** Leading days read before the window: the 7-day mean of the first slot (6) and last week's mean (13). */
private const val LEAD = 13

internal fun buildTrendsRidge(
    registry: MetricRegistry,
    rows: Map<String, DailyMetric>,
    today: LocalDate,
    range: RidgeTrendsRange,
    basalByDay: Map<String, Double> = emptyMap(),
    activeByDay: Map<String, Double> = emptyMap(),
    /** Recorded energy of the user's logged workouts per day (every source, deduplicated), for strap-only days. */
    workoutKcalByDay: Map<String, Double> = emptyMap(),
    sleepConsistencyByDay: Map<String, Double> = emptyMap(),
): TrendsRidgeModel {
    val n = range.days
    val from = DayKey(today.minusDays((n - 1 + LEAD).toLong()))
    val to = DayKey(today)
    fun series(id: MetricId): List<Double?> = registry.series(id, from, to).map { it.value }
    fun visible(xs: List<Double?>) = xs.drop(LEAD)
    val days = (0 until n).map { today.minusDays((n - 1 - it).toLong()) }

    val charge = series(MetricId.CHARGE)
    val chargeTrend = ChargeTrend(
        values = visible(charge),
        mean7 = rollingMean(charge, lead = LEAD),
        average = visible(charge).meanOrNull(),
        thisWeek = rollingMean(charge).last(),
        lastWeek = rollingMean(charge)[charge.lastIndex - 7],
        today = charge.last(),
        effortAvg = visible(series(MetricId.EFFORT)).meanOrNull(),
        restAvg = visible(series(MetricId.REST)).meanOrNull(),
        tracked = visible(charge).count { it != null },
    )

    // B2-2a-2: Effort reuses the Charge card's week means over the chosen range.
    val effort = series(MetricId.EFFORT)
    val effortRoll = rollingMean(effort)
    val effortVisible = visible(effort)
    val effortTrend = EffortTrend(
        values = effortVisible,
        mean7 = effortRoll.drop(LEAD),
        average = effortVisible.meanOrNull(),
        thisWeek = effortRoll.last(),
        lastWeek = effortRoll[effortRoll.lastIndex - 7],
        today = effort.last(),
        hardDays = effortVisible.count { it != null && effortBand(it) >= EffortBand.HIGH },
        tracked = effortVisible.count { it != null },
    )

    // B2-2a-3: Calories splits the imported base/active, or falls back to the whole-day strap estimate (Q2).
    val calories = buildCalories(days, rows, basalByDay, activeByDay, workoutKcalByDay)

    val rhr = heartSeries(series(MetricId.RESTING_HR), registry.normalRange(MetricId.RESTING_HR, to), minSpan = 8.0, step = 1.0)
    val hrv = heartSeries(series(MetricId.HRV), registry.normalRange(MetricId.HRV, to), minSpan = 20.0, step = 2.0)
    val heartTracked = rhr.values.indices.count { rhr.values[it] != null || hrv.values[it] != null }

    val asleep = visible(series(MetricId.SLEEP_DURATION))
    val nights = days.mapIndexed { i, d ->
        val row = rows[d.toString()]
        sleepNight(asleep[i], row?.lightMin, row?.deepMin, row?.remMin)
    }
    val asleepMins = nights.mapNotNull {
        when (it) {
            is SleepNight.Staged -> it.asleepMin
            is SleepNight.Unstaged -> it.asleepMin
            SleepNight.Untracked -> null
        }
    }
    // R9b: the need line (§7.6), now that S0-3 picked the one need (R9a V2-1). Untracked nights keep their need.
    val needHours = if (MetricsV2.on(V2Change.ONE_SLEEP_NEED)) visible(series(MetricId.SLEEP_NEED)) else emptyList()
    // B2-2b-3: the board's "regularity" stat is the imported sleep_consistency (0–100) over the tracked nights;
    // an untracked night and a tracked night with no imported value both drop out of the mean.
    val regularity = nights.indices.mapNotNull { i ->
        if (nights[i] is SleepNight.Untracked) null else sleepConsistencyByDay[days[i].toString()]
    }.takeIf { it.isNotEmpty() }?.average()
    val sleep = SleepTrend(
        nights = nights,
        needHours = needHours,
        axis = sleepHoursAxis(asleepMins.map { it / 60.0 } + needHours.filterNotNull()),
        avgAsleepMin = asleepMins.takeIf { it.isNotEmpty() }?.average(),
        regularity = regularity,
        tracked = asleepMins.size,
    )

    return TrendsRidgeModel(range, days, chargeTrend, effortTrend, calories, HeartTrend(rhr, hrv, heartTracked), sleep)
}

/**
 * The Calories axis (B2-2a-3): 0 to `max(3000, ceil(max / 500)·500)`, with the board's 1k / 2k / 3k ticks.
 */
internal fun caloriesAxis(totals: List<Double?>): Axis {
    val hi = maxOf(3000.0, ceil((totals.filterNotNull().maxOrNull() ?: 0.0) / 500.0) * 500.0)
    return Axis(0.0, hi, listOf(1000.0, 2000.0, 3000.0).filter { it <= hi })
}

/**
 * Per checkpoint Q2, with the strap first (owner, 21 Nov): a day the strap has an estimate for draws it as base, with the day's
 * logged workouts as its active part when there are any (the owner's 20 Nov correction: a confirmed workout is active); a day
 * with no strap estimate takes the import's base / active split.
 */
private fun buildCalories(
    days: List<LocalDate>,
    rows: Map<String, DailyMetric>,
    basalByDay: Map<String, Double>,
    activeByDay: Map<String, Double>,
    workoutKcalByDay: Map<String, Double>,
): CaloriesTrend {
    val cells = days.map { d ->
        val key = d.toString()
        val basal = basalByDay[key]
        val est = rows[key]?.activeKcalEst
        when {
            // The strap always wins (owner, 21 Nov): a day the strap has an estimate for uses it, and the workouts the user
            // logged that day (confirmed detections are stored as "manual" rows) are the active part of it, never more
            // than the total. Their recorded energy is data, not a guess.
            est != null -> {
                val workout = workoutKcalByDay[key]?.takeIf { it > 0.0 }?.coerceAtMost(est)
                if (workout != null) CaloriesDay(d, est - workout, workout, isToday = false)
                else CaloriesDay(d, est, null, isToday = false)
            }
            // A day with no strap estimate: the import's own base / active split fills it.
            basal != null -> CaloriesDay(d, basal, activeByDay[key], isToday = false)
            else -> CaloriesDay(d, null, null, isToday = false)
        }
    }
    // The last slot is today (the range ends on the current day).
    val withToday = cells.dropLast(1) + cells.takeLast(1).map { it.copy(isToday = true) }
    val full = withToday.filter { !it.isToday && it.base != null }
    val totals = full.mapNotNull { it.total }
    val actives = full.map { it.active ?: 0.0 }
    val totalAvg = totals.takeIf { it.isNotEmpty() }?.average()
    val activeAvg = actives.takeIf { it.isNotEmpty() }?.average()
    val hasSplit = full.any { it.active != null }
    val activeShare = if (hasSplit && totalAvg != null && totalAvg > 0.0) {
        ((activeAvg ?: 0.0) / totalAvg * 100.0).roundToInt()
    } else null
    return CaloriesTrend(
        days = withToday,
        totalAvg = totalAvg,
        activeAvg = if (hasSplit) activeAvg else null,
        todayTotal = withToday.last().total,
        activeShare = activeShare,
        hasSplit = hasSplit,
        tracked = full.size,
        axis = caloriesAxis(totals),
    )
}

/**
 * One heart signal (§6): outliers beyond ±4 SD of the v10 baseline are drawn hollow and left out of the 7-day mean;
 * the normal band is v10's personal range (baseline ± [VitalBands.sigmaK] SD), shown only once it is trusted.
 */
private fun heartSeries(all: List<Double?>, normal: NormalRange?, minSpan: Double, step: Double): HeartSeries {
    val personal = normal?.takeIf { it.basis == NormalRange.Basis.PERSONAL }
    // R9a V2-4: the 60-night band carries its own mean and SD; v10's band is baseline ± sigmaK·SD.
    val baseline = personal?.let { it.mean ?: ((it.low + it.high) / 2) }
    val sigma = personal?.let { it.sd ?: ((it.high - it.low) / (2 * VitalBands.sigmaK)) }
    val outlierAll = all.map { v -> v != null && isOutlier(v, baseline, sigma) }
    val forMean = all.mapIndexed { i, v -> if (outlierAll[i]) null else v }
    val rolling = rollingMean(forMean)
    val latest7 = rolling.last()
    val prior7 = rolling[rolling.lastIndex - 7]
    val values = all.drop(LEAD)
    val mean7 = rolling.drop(LEAD)
    val band = personal?.let { it.low..it.high }
    val position = when {
        band == null || latest7 == null -> NormalPosition.NO_BAND
        latest7 > band.endInclusive -> NormalPosition.ABOVE
        latest7 < band.start -> NormalPosition.BELOW
        else -> NormalPosition.WITHIN
    }
    return HeartSeries(
        values = values,
        mean7 = mean7,
        outlier = outlierAll.drop(LEAD),
        band = band,
        bandNights = normal?.nights ?: 0,
        latest7 = latest7,
        changePct = percentChange(latest7, prior7),
        position = position,
        axis = heartAxis(values.filterNotNull() + mean7.filterNotNull(), band, minSpan, step),
    )
}

private fun List<Double?>.meanOrNull(): Double? = filterNotNull().takeIf { it.isNotEmpty() }?.average()
