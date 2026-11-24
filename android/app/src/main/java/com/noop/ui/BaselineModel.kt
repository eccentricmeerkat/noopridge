package com.noop.ui

import com.noop.analytics.VitalityEngine
import com.noop.analytics.Vo2Norms
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.MetricRegistry
import com.noop.metrics.NormalRange
import com.noop.ui.ridge.chart.Axis
import com.noop.ui.ridge.chart.heartAxis
import com.noop.ui.ridge.chart.rollingMean
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The Ridge Baseline page model (R5-2, E-01, `Baseline.dc.html`): the slow-moving markers v10 stores, read through
 * the registry. Body age and fitness age are v10's weekly engine outputs (Saturday-keyed); VO₂ max is each stored
 * estimate; the long-run heart lines are 30-day means of the nightly values. Nothing here is scored anew.
 */

/**
 * The Baseline range pills; 30 days is too short for slow markers and is shown disabled (UX-BAS-4). [ALL] (B2-3-1,
 * the board's "All") spans from the earliest stored day to today, so its [days] is a sentinel: use [spanDays].
 */
internal enum class BaselineRange(val days: Int) {
    D90(90), D180(180), D365(365), ALL(0);

    /** Days the range covers ending [today]: [ALL] reaches back to [earliest], never under 90 or over 10 years. */
    fun spanDays(earliest: LocalDate?, today: LocalDate): Int =
        if (this != ALL) days
        else earliest?.let { (java.time.temporal.ChronoUnit.DAYS.between(it, today).toInt() + 1).coerceIn(90, 3650) } ?: 90
}

/** The Saturday after [today]: v10's body age is a weekly, Saturday-keyed output (B2-3-1 "Next update"). */
internal fun nextBodyAgeUpdate(today: LocalDate): LocalDate =
    today.plusDays(((java.time.DayOfWeek.SATURDAY.value - today.dayOfWeek.value + 6) % 7 + 1).toLong())

/** The long-run line needs half of its 30 nights (a default, R5-2; §6 asks 4 of 7 for the 7-day line). */
internal const val LONG_RUN_WINDOW = 30
internal const val LONG_RUN_MIN = 15

internal data class BodyAge(
    val bodyAge: Double,
    val vitality: Double?,
    val chronoAge: Int,
    /** v10's fixed uncertainty ([VitalityEngine.bandYears]); never a narrower range (UX-BAS-2). */
    val bandYears: Double,
    val updated: LocalDate?,
) {
    /** Calendar age minus body age, whole years: positive is younger. */
    val yearsUnder: Int get() = Math.round(chronoAge - bodyAge).toInt()
}

internal data class Vo2Trend(
    val days: List<LocalDate>,
    /** Each stored estimate on its own day, null elsewhere. */
    val estimates: List<Double?>,
    /** The calendar-month mean on each day of a month with at least two estimates (§10), else null. */
    val monthly: List<Double?>,
    val latest: Double?,
    val axis: Axis,
    /** The 25th–75th percentile for the reader's age and sex ([Vo2Norms]), null with no age entered. */
    val typical: ClosedFloatingPointRange<Double>? = null,
)

internal data class LongRunSeries(
    /** One slot per bucket of [bucketDays] nights: the bucket's mean nightly value. */
    val nightly: List<Double?>,
    val mean30: List<Double?>,
    val band: ClosedFloatingPointRange<Double>?,
    val latest30: Double?,
    val position: NormalPosition,
    val axis: Axis,
)

internal data class BaselineModel(
    val range: BaselineRange,
    /** Days the range covers (equals [BaselineRange.days] except for [BaselineRange.ALL]). */
    val spanDays: Int,
    /** The first day of each slot (one slot per [bucketDays] days, so a series stays within R7's 400 points). */
    val slots: List<LocalDate>,
    val bucketDays: Int,
    val bodyAge: BodyAge?,
    val fitnessAge: Double?,
    val vo2: Vo2Trend,
    val maxHr: Double?,
    val maxHrMeasured: Boolean,
    /** No age entered (R9a V2-7): the age-derived markers ask for one. */
    val needsAge: Boolean = false,
    val rhr: LongRunSeries,
    val hrv: LongRunSeries,
)

/** R7: at most 400 points per series; nightly series over two years are drawn as 2-night means. */
internal fun bucketDaysFor(days: Int): Int = ceil(days / 400.0).toInt().coerceAtLeast(1)

internal fun buildBaseline(
    registry: MetricRegistry,
    today: LocalDate,
    range: BaselineRange,
    /** Null when no age was ever entered (R9a V2-7, #27); body age then needs one, as VO₂ max and fitness age. */
    chronoAge: Int?,
    maxHrMeasured: Boolean,
    /** The earliest stored day, which [BaselineRange.ALL] reaches back to. */
    earliest: LocalDate? = null,
    /** The profile sex ("male", "female", "nonbinary"), which picks the VO₂ norm table. */
    sex: String = "male",
): BaselineModel {
    val n = range.spanDays(earliest, today)
    val start = today.minusDays((n - 1).toLong())
    val days = (0 until n).map { start.plusDays(it.toLong()) }
    val bucket = bucketDaysFor(n)
    val slots = days.chunked(bucket).map { it.first() }
    val to = DayKey(today)

    val body = registry.value(MetricId.BODY_AGE, to)
    val bodyAge = if (chronoAge == null) null else body.value?.let { ba ->
        BodyAge(
            bodyAge = ba,
            vitality = registry.value(MetricId.VITALITY, to).value,
            chronoAge = chronoAge,
            bandYears = VitalityEngine.bandYears,
            updated = (body.carriedFrom ?: to).date,
        )
    }

    val vo2Raw = registry.series(MetricId.VO2_MAX, DayKey(start), to).map { it.value }
    val monthGroups = days.indices.groupBy { YearMonth.from(days[it]) }
    val monthly = MutableList<Double?>(n) { null }
    for ((_, idx) in monthGroups) {
        val xs = idx.mapNotNull { vo2Raw[it] }
        if (xs.size >= 2) idx.forEach { monthly[it] = xs.average() }
    }
    val vo2Values = vo2Raw.filterNotNull()
    val vo2Axis = if (vo2Values.isEmpty()) Axis(30.0, 50.0, listOf(30.0, 40.0, 50.0)) else {
        val lo = floor((vo2Values.min() - 2) / 5) * 5
        val hi = ceil((vo2Values.max() + 2) / 5) * 5
        Axis(lo, hi, generateSequence(lo) { it + 5 }.takeWhile { it <= hi + 1e-9 }.toList())
    }

    fun longRun(id: MetricId, minSpan: Double, step: Double): LongRunSeries {
        val lead = LONG_RUN_WINDOW - 1
        val all = registry.series(id, DayKey(start.minusDays(lead.toLong())), to).map { it.value }
        val rolled = rollingMean(all, window = LONG_RUN_WINDOW, minN = LONG_RUN_MIN, lead = lead)
        val nightly = all.drop(lead)
        fun bucketed(xs: List<Double?>) = xs.chunked(bucket).map { c -> c.filterNotNull().takeIf { it.isNotEmpty() }?.average() }
        val nb = bucketed(nightly)
        val mb = rolled.chunked(bucket).map { it.last() }
        val normal = registry.normalRange(id, to)?.takeIf { it.basis == NormalRange.Basis.PERSONAL }
        val band = normal?.let { it.low..it.high }
        val latest = rolled.last()
        val position = when {
            band == null || latest == null -> NormalPosition.NO_BAND
            latest > band.endInclusive -> NormalPosition.ABOVE
            latest < band.start -> NormalPosition.BELOW
            else -> NormalPosition.WITHIN
        }
        return LongRunSeries(nb, mb, band, latest, position, heartAxis(nb.filterNotNull() + mb.filterNotNull(), band, minSpan, step))
    }

    return BaselineModel(
        range = range,
        spanDays = n,
        slots = slots,
        bucketDays = bucket,
        bodyAge = bodyAge,
        fitnessAge = registry.value(MetricId.FITNESS_AGE, to).value,
        vo2 = Vo2Trend(
            days, vo2Raw, monthly, registry.value(MetricId.VO2_MAX, to).value, vo2Axis,
            typical = chronoAge?.let { Vo2Norms.typicalRange(it, sex) },
        ),
        maxHr = registry.value(MetricId.MAX_HR, to).value,
        maxHrMeasured = maxHrMeasured,
        needsAge = chronoAge == null,
        rhr = longRun(MetricId.RESTING_HR, minSpan = 8.0, step = 1.0),
        hrv = longRun(MetricId.HRV, minSpan = 20.0, step = 2.0),
    )
}

/** One measured session: its logical day and v10's 1-minute heart-rate drop ([com.noop.analytics.HeartRateRecovery]). */
internal data class HrrSession(val day: LocalDate, val endTs: Long, val drop1: Int)

internal data class HrrTrend(
    val days: List<LocalDate>,
    /** The day's last measured session, null elsewhere. */
    val drops: List<Double?>,
    val sessions: Int,
    val latest: Int?,
    /** The median drop over the range's sessions. */
    val typical: Int?,
)

/** HR recovery over [days]: only sessions v10 could measure (its intensity and coverage gates) appear. */
internal fun buildHrr(sessions: List<HrrSession>, days: List<LocalDate>): HrrTrend {
    val inRange = sessions.filter { it.day in days.first()..days.last() }.sortedBy { it.endTs }
    val byDay = inRange.associateBy { it.day }
    val drops = inRange.map { it.drop1 }.sorted()
    val typical = drops.takeIf { it.isNotEmpty() }?.let {
        val n = it.size
        if (n % 2 == 1) it[n / 2] else Math.round((it[n / 2 - 1] + it[n / 2]) / 2.0).toInt()
    }
    return HrrTrend(days, days.map { d -> byDay[d]?.drop1?.toDouble() }, inRange.size, inRange.lastOrNull()?.drop1, typical)
}

/** Which way heart-rate recovery is heading across the range's sessions (B2-3-4). */
internal enum class HrrDirection { IMPROVING, STEADY, DECLINING }

/** The fewest measured sessions a trend line and its word are drawn from. */
internal const val HRR_TREND_MIN_SESSIONS = 4

/** The change in the fitted line (bpm of drop, first session to last) that counts as a direction, not noise. */
internal const val HRR_TREND_MIN_CHANGE = 3.0

/**
 * The least-squares line through the measured drops (x = the day's slot), valued on every slot from the first to the
 * last session and null elsewhere, so it draws as one dashed run. Null under [HRR_TREND_MIN_SESSIONS] sessions or when
 * every session fell on one slot.
 */
internal fun hrrTrendLine(t: HrrTrend): List<Double?>? {
    val pts = t.drops.mapIndexedNotNull { i, v -> v?.let { i.toDouble() to it } }
    if (pts.size < HRR_TREND_MIN_SESSIONS) return null
    val mx = pts.map { it.first }.average(); val my = pts.map { it.second }.average()
    val sxx = pts.sumOf { (it.first - mx) * (it.first - mx) }
    if (sxx == 0.0) return null
    val slope = pts.sumOf { (it.first - mx) * (it.second - my) } / sxx
    val first = pts.first().first.toInt(); val last = pts.last().first.toInt()
    return List(t.drops.size) { i -> if (i in first..last) my + slope * (i - mx) else null }
}

/** Improving or declining when the fitted line moves by [HRR_TREND_MIN_CHANGE] bpm or more across the sessions, else steady. */
internal fun hrrDirection(t: HrrTrend): HrrDirection? {
    val line = hrrTrendLine(t)?.filterNotNull() ?: return null
    val change = line.last() - line.first()
    return when {
        change >= HRR_TREND_MIN_CHANGE -> HrrDirection.IMPROVING
        change <= -HRR_TREND_MIN_CHANGE -> HrrDirection.DECLINING
        else -> HrrDirection.STEADY
    }
}

/** One body-age driver: v10's factor [key], its signed share of the largest factor (-1…1), and its direction. */
internal data class Driver(val key: String, val share: Double, val direction: Int, val years: Double)

/** A signed one-decimal year figure with a true minus ("−2.6", "+0.6"), as the board's drivers list shows it. */
internal fun signedYears(years: Double, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val tenths = Math.round(years * 10).toInt()
    val body = String.format(locale, "%.1f", kotlin.math.abs(tenths) / 10.0)
    return when {
        tenths < 0 -> "\u2212$body"
        tenths > 0 -> "+$body"
        else -> body
    }
}

/**
 * The drivers as v10 explains them: helping (negative log-hazard), holding back (positive) or neutral, largest
 * first. The share sizes a bar; [Driver.years] is the factor's own part of the body-age shift ([VitalityEngine.yearsOf]).
 */
internal fun buildDrivers(contribs: List<VitalityEngine.Contribution>): List<Driver> {
    val maxAbs = contribs.maxOfOrNull { kotlin.math.abs(it.lnHazard) }?.takeIf { it > 0 } ?: return emptyList()
    return contribs.sortedByDescending { kotlin.math.abs(it.lnHazard) }.map { c ->
        val direction = when {
            kotlin.math.abs(c.lnHazard) < 0.005 -> 0
            c.lnHazard < 0 -> -1
            else -> 1
        }
        Driver(c.key, c.lnHazard / maxAbs, direction, VitalityEngine.yearsOf(c.lnHazard))
    }
}
