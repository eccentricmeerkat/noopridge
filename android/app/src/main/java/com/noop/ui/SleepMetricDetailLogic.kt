package com.noop.ui

import androidx.compose.ui.graphics.Color
import com.noop.analytics.RestScorer
import com.noop.analytics.SleepDebt
import com.noop.data.DailyMetric
import com.noop.metrics.MetricsV2
import com.noop.metrics.V2Change
import com.noop.metrics.oneSleepNeedHours
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class SleepMetricRange(val label: String, val days: Long?) {
    WEEK("W", 7), MONTH("M", 30), THREE_MONTH("3M", 90),
    SIX_MONTH("6M", 180), YEAR("1Y", 365), ALL("ALL", null),
}

internal data class SleepMetricSpec(
    val title: String,
    val unit: String,
    val color: Color,
    val format: (Double) -> String,
)

internal fun sleepMetricSpec(key: String): SleepMetricSpec = when (key) {
    "performance"     -> SleepMetricSpec("Rest", "%", Palette.restColor) { "${it.roundToInt()}" }
    "efficiency"      -> SleepMetricSpec("Sleep Efficiency", "%", Palette.statusPositive) { "${it.roundToInt()}" }
    "consistency"     -> SleepMetricSpec("Consistency", "%", Palette.metricCyan) { "${it.roundToInt()}" }
    "hours_vs_needed" -> SleepMetricSpec("Hours vs Needed", "%", Palette.restColor) { "${it.roundToInt()}" }
    "restorative"     -> SleepMetricSpec("Restorative", "%", Palette.sleepREM) { "${it.roundToInt()}" }
    "respiratory"     -> SleepMetricSpec("Respiratory Rate", "rpm", Palette.metricPurple) { String.format(Locale.US, "%.1f", it) }
    "sleep_debt"      -> SleepMetricSpec("Sleep Debt", "min", Palette.metricRose) { "${it.roundToInt()}" }   // #691: minutes, not decimal hours
    else              -> SleepMetricSpec(key, "", Palette.accent) { "${it.roundToInt()}" }
}

internal fun buildSleepMetricPoints(
    days: List<DailyMetric>,
    key: String,
    imported: ImportedSleepSeries = ImportedSleepSeries(),
    napSleepMinByDay: Map<String, Double> = emptyMap(),
    // R9a V2-1 (S0-3): Hours vs needed and debt measure against the one need, with this age.
    oneNeed: Boolean = MetricsV2.on(V2Change.ONE_SLEEP_NEED),
    oneNeedAge: Int? = null,
    // R9c (#54): with V2-9 on, Consistency's page plots the tile's per-night bedtime score, not a duration spread.
    consistencyByNight: List<Pair<String, Double>>? = null,
): List<Pair<String, Double>> {
    if (key == "consistency" && consistencyByNight != null && MetricsV2.on(V2Change.CONSISTENCY_PER_NIGHT)) {
        return consistencyByNight
    }
    val asleepByDay = if (oneNeed) days.associate { it.day to it.totalSleepMin } else emptyMap()
    fun oneNeedMin(day: String): Double = oneSleepNeedHours(asleepByDay, LocalDate.parse(day), oneNeedAge) * 60.0
    val needMin = max(450.0, days.mapNotNull { it.totalSleepMin?.takeIf { m -> m > 0.0 } }.average().let { if (it.isNaN()) 480.0 else it })
    if (key == "sleep_debt") {
        val debtNeedMin = if (oneNeed && days.isNotEmpty()) oneNeedMin(days.last().day) else RestScorer.personalizedNeedHours(
            days.mapNotNull { it.totalSleepMin?.let { minutes -> minutes / 60.0 } }, null,
        ) * 60.0
        return SleepDebt.debtSeries(
            series = days.map { day ->
                day.day to SleepDebt.creditedSleepMin(
                    day.totalSleepMin,
                    napSleepMinByDay[day.day] ?: 0.0,
                )
            },
            needHours = debtNeedMin / 60.0,
            importedDebtMin = imported.debtMin,
        )
    }
    return days.mapNotNull { d ->
        val v: Double? = when (key) {
            // The Rest detail graph reads the REAL resolved Rest composite per day — the same single
            // source of truth the Today Rest score uses (RestScorer.restFromDaily, the composite the
            // sleep_performance series carries) — not a local hours-vs-need approximation. Keeps the
            // graph and the score in agreement. (#614 follow-up)
            "performance" -> RestScorer.restFromDaily(d)?.takeIf { it in 0.0..100.0 }
            "efficiency"  -> d.efficiency?.let { if (it <= 1.0) it * 100.0 else it }
            "consistency" -> {
                val idx = days.indexOf(d)
                val lo = max(0, idx - 13)
                val window = days.subList(lo, idx + 1).mapNotNull { it.totalSleepMin?.takeIf { m -> m > 0.0 } }
                if (window.size < 3) null else {
                    val m = window.average()
                    val sd = kotlin.math.sqrt(window.sumOf { (it - m) * (it - m) } / window.size)
                    (100.0 * (1.0 - sd / 90.0)).coerceIn(0.0, 100.0)
                }
            }
            "hours_vs_needed" -> d.totalSleepMin?.takeIf { it > 0.0 }?.let {
                minOf(100.0, it / (if (oneNeed) oneNeedMin(d.day) else needMin) * 100.0)
            }
            "restorative" -> {
                val dp = d.deepMin ?: return@mapNotNull null
                val rm = d.remMin ?: return@mapNotNull null
                val sl = d.totalSleepMin ?: return@mapNotNull null
                if (sl > 0.0) (dp + rm) / sl * 100.0 else null
            }
            "respiratory" -> d.respRateBpm
            else          -> null
        }
        v?.takeIf { it.isFinite() }?.let { d.day to it }
    }
}

internal fun filterSleepMetricPoints(
    points: List<Pair<String, Double>>,
    range: SleepMetricRange,
): List<Pair<String, Double>> {
    val windowDays = range.days ?: return points
    val latestDate = points.lastOrNull()?.first?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return points.takeLast(windowDays.toInt())
    val cutoff = latestDate.minusDays(windowDays - 1)
    val filtered = points.filter { (day, _) ->
        runCatching { LocalDate.parse(day) }.getOrNull()?.let { !it.isBefore(cutoff) } ?: false
    }
    return filtered.ifEmpty { points.takeLast(windowDays.toInt()) }
}
