package com.noop.ui

import com.noop.analytics.ReadinessEngine
import com.noop.data.DailyMetric
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.MetricRegistry
import com.noop.metrics.MetricsV2
import com.noop.metrics.NormalRange
import com.noop.metrics.V2Change
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Ridge Trends cards with fixed windows (R4b), which the range pills do not scope: Load & recovery (45 days),
 * Consistency (4 weeks) and Intensity (4 weeks). Values are v10's: daily Effort and Rest through the registry, the
 * load lines from v10's own acute:chronic rule, and workout minutes from each session's stored HR-zone split.
 */

/** Load & recovery shows the last 45 days (05 §3.5). */
internal const val LOAD_DAYS = 45

/** v10's "building fast" threshold (`ReadinessEngine.acwrSignal`); shaded days are at or above it (D-03, R4b). */
internal const val LOAD_RAMP_RATIO = 1.3

/** v10 needs this many Effort readings before it reports a ratio (`ReadinessEngine.minChronic`). */
internal const val LOAD_MIN_READINGS = 14

internal data class LoadTrend(
    val days: List<LocalDate>,
    val effort: List<Double?>,
    /** v10's acute load: the mean of the last 7 Effort readings up to that date. */
    val acute: List<Double?>,
    /** v10's chronic load: the mean of the last 28 Effort readings up to that date. */
    val chronic: List<Double?>,
    val ramp: List<Boolean>,
    /** Effort readings up to today, for the "needs 14" state. */
    val readings: Int,
) {
    private val last: Int get() = acute.indexOfLast { it != null }
    val latestAcute: Double? get() = acute.getOrNull(last)
    val latestChronic: Double? get() = chronic.getOrNull(last)
    val latestRatio: Double? get() = latestAcute?.let { a -> latestChronic?.takeIf { it > 0 }?.let { a / it } }
}

/**
 * One date's load is v10's rule evaluated on that date's own history: [ReadinessEngine.evaluate] over the rows up to
 * and including it. (Called with the whole history, the engine uses every row for any date; DIFFERENCES #25.)
 */
internal fun buildLoadTrend(registry: MetricRegistry, rows: List<DailyMetric>, today: LocalDate): LoadTrend {
    val days = (0 until LOAD_DAYS).map { today.minusDays((LOAD_DAYS - 1 - it).toLong()) }
    val sorted = rows.sortedBy { it.day }
    val effort = registry.series(MetricId.EFFORT, DayKey(days.first()), DayKey(today)).map { it.value }
    val loads = days.map { d ->
        val key = d.toString()
        if (sorted.none { it.day == key }) return@map null
        val upTo = sorted.filter { it.day <= key }
        ReadinessEngine.evaluate(upTo, today = key).signals.firstNotNullOfOrNull {
            it.evidence as? ReadinessEngine.Evidence.TrainingLoad
        }
    }
    val acute = loads.map { it?.acute }
    val chronic = loads.map { it?.chronic }
    return LoadTrend(
        days = days,
        effort = effort,
        acute = acute,
        chronic = chronic,
        ramp = loads.map { l -> l != null && l.chronic > 0 && l.acute / l.chronic >= LOAD_RAMP_RATIO },
        readings = sorted.count { it.day <= today.toString() && it.strain != null },
    )
}

/** The Consistency fill: Effort bands, WHOOP rescaled to 0–100 (D-07, R4b). */
internal enum class EffortBand { REST, MODERATE, HIGH, ALL_OUT }

internal fun effortBand(effort: Double): EffortBand = when {
    effort < 48.0 -> EffortBand.REST
    effort < 67.0 -> EffortBand.MODERATE
    effort < 86.0 -> EffortBand.HIGH
    else -> EffortBand.ALL_OUT
}

/** "Slept on target" in v10 terms: Rest at or above 85 (D-14, R4b). With [V2Change.SLEPT_ON_TARGET] on (R9a V2-3),
 *  a night is on target when asleep ≥ that night's one need (V2-1), as D-14 decided. */
internal const val REST_ON_TARGET = 85.0

internal data class ConsistencyDay(
    val date: LocalDate,
    /** Later in the current week: drawn empty with no outline. */
    val future: Boolean,
    val effort: Double?,
    val band: EffortBand?,
    /** null when Rest is untracked that night (left out of the denominator). */
    val onTarget: Boolean?,
)

internal data class ConsistencyTrend(
    /** 28 days, Monday-first, four weeks ending with the current one; column = week, row = weekday. */
    val cells: List<ConsistencyDay>,
    val elapsed: Int,
    val trained: Int,
    val trackedNights: Int,
    val onTarget: Int,
)

/** Monday of the week three weeks before today's, so the grid ends with the current week. */
internal fun fourWeekStart(today: LocalDate): LocalDate =
    today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()).minusWeeks(3)

/** The Consistency grid covers 12 weeks, Monday-first (B2-2b-5; the board's `nC`). */
internal const val CONSISTENCY_WEEKS = 12

/** Monday of the week (CONSISTENCY_WEEKS - 1) weeks before today's, so the grid ends with the current week. */
internal fun consistencyStart(today: LocalDate): LocalDate =
    today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        .minusWeeks((CONSISTENCY_WEEKS - 1).toLong())

internal fun buildConsistency(
    registry: MetricRegistry,
    today: LocalDate,
    v2: Set<V2Change> = MetricsV2.active,
): ConsistencyTrend {
    val start = consistencyStart(today)
    val end = start.plusDays((CONSISTENCY_WEEKS * 7 - 1).toLong())
    val effort = registry.series(MetricId.EFFORT, DayKey(start), DayKey(end)).map { it.value }
    val rest = registry.series(MetricId.REST, DayKey(start), DayKey(end)).map { it.value }
    val byNeed = MetricsV2.isOn(V2Change.SLEPT_ON_TARGET, v2)
    val asleep = if (byNeed) registry.series(MetricId.SLEEP_DURATION, DayKey(start), DayKey(end)).map { it.value } else emptyList()
    val need = if (byNeed) registry.series(MetricId.SLEEP_NEED, DayKey(start), DayKey(end)).map { it.value } else emptyList()
    val cells = (0 until CONSISTENCY_WEEKS * 7).map { i ->
        val d = start.plusDays(i.toLong())
        val future = d.isAfter(today)
        val e = if (future) null else effort[i]
        val onTarget = when {
            future -> null
            byNeed -> asleep[i]?.takeIf { it > 0.0 }?.let { a -> need[i]?.let { n -> a >= n * 60.0 } }
            else -> rest[i]?.let { it >= REST_ON_TARGET }
        }
        ConsistencyDay(d, future, e, e?.let(::effortBand), onTarget)
    }
    val past = cells.filterNot { it.future }
    return ConsistencyTrend(
        cells = cells,
        elapsed = past.size,
        trained = past.count { it.band != null && it.band != EffortBand.REST },
        trackedNights = past.count { it.onTarget != null },
        onTarget = past.count { it.onTarget == true },
    )
}

// MARK: - Other daily signals (B2-2b-4, C-TRN-SIG)

/** The Other daily signals sparklines cover the last 14 days. */
internal const val SIGNALS_DAYS = 14

internal enum class SignalTrend { RISING, FALLING, STEADY }

internal data class DailySignal(
    val values: List<Double?>,
    val latest: Double?,
    /** Stress: settled (Low band); Resp / SpO2: inside the metric's normal range; Skin temp: always false. */
    val inNormal: Boolean,
    /** Skin temp only: the direction of the last seven days; STEADY for the other three. */
    val trend: SignalTrend,
    /** Blood O2 only: the latest value is the strap estimate, not a calibrated or imported reading. */
    val isEstimate: Boolean = false,
    /** Blood O2 only (Fix 5): the day of the latest reading when it is older than the tile's window, so the tile can say "as of". */
    val asOf: LocalDate? = null,
)

/** The latest Blood oxygen reading (calibrated, imported or the strap estimate) on or before a day, however old up to the look-back. */
internal data class LatestSpo2(val value: Double, val day: LocalDate, val estimate: Boolean)

/**
 * The newest Blood oxygen reading within [lookbackDays] of [today], through the registry, so the Trends tile and the Vital signs
 * page show the same figure Insights charts (they used to read only the last 14 days or today, and went blank when the latest
 * reading was older, while Insights still drew it).
 */
internal fun latestSpo2(registry: MetricRegistry, today: LocalDate, lookbackDays: Int = 180): LatestSpo2? {
    val pts = registry.series(MetricId.SPO2, DayKey(today.minusDays(lookbackDays.toLong())), DayKey(today))
    val p = pts.lastOrNull { it.value != null } ?: return null
    return LatestSpo2(p.value!!, p.day.date, p.provenanceNote == com.noop.metrics.ValueNote.STRAP_ESTIMATE)
}

internal data class SignalsTrend(
    val stress: DailySignal,
    val resp: DailySignal,
    val skinTemp: DailySignal,
    val spo2: DailySignal,
)

internal fun buildSignals(registry: MetricRegistry, today: LocalDate): SignalsTrend {
    val from = DayKey(today.minusDays((SIGNALS_DAYS - 1).toLong()))
    val to = DayKey(today)
    fun series(id: MetricId): List<Double?> = registry.series(id, from, to).map { it.value }
    fun tile(xs: List<Double?>, inNormal: Boolean, trend: SignalTrend = SignalTrend.STEADY) =
        DailySignal(xs, xs.lastOrNull { it != null }, inNormal, trend)

    val stress = series(MetricId.STRESS)
    val resp = series(MetricId.RESP_RATE)
    val skin = series(MetricId.SKIN_TEMP_DEV)
    val spo2Points = registry.series(MetricId.SPO2, from, to)
    val spo2 = spo2Points.map { it.value }
    // BS-x2-2 item 9: the tile says "Estimate" only when the latest value is the strap estimate.
    val spo2IsEstimate = spo2Points.lastOrNull { it.value != null }?.provenanceNote == com.noop.metrics.ValueNote.STRAP_ESTIMATE

    val latestStress = stress.lastOrNull { it != null }
    val respNormal = registry.normalRange(MetricId.RESP_RATE, to)
    val spo2Normal = registry.normalRange(MetricId.SPO2, to)
    val respLatest = resp.lastOrNull { it != null }
    val spo2Latest = spo2.lastOrNull { it != null }
    fun within(range: NormalRange?, v: Double?): Boolean = v != null && range != null && v >= range.low && v <= range.high

    // Skin temp: rising/falling over the last week, with a 0.05 °C dead band.
    val skinLatest = skin.lastOrNull { it != null }
    val skinPrior = skin.dropLast(7).lastOrNull { it != null }
    val skinTrend = when {
        skinLatest == null || skinPrior == null -> SignalTrend.STEADY
        skinLatest - skinPrior > 0.05 -> SignalTrend.RISING
        skinLatest - skinPrior < -0.05 -> SignalTrend.FALLING
        else -> SignalTrend.STEADY
    }

    return SignalsTrend(
        stress = tile(stress, latestStress != null && StressBand.forScore(latestStress) == StressBand.Low),
        resp = tile(resp, within(respNormal, respLatest)),
        skinTemp = tile(skin, false, skinTrend),
        spo2 = if (spo2Latest != null) tile(spo2, within(spo2Normal, spo2Latest)).copy(isEstimate = spo2IsEstimate)
        else latestSpo2(registry, today)?.let { l ->
            // Nothing in the last 14 days: show the newest reading older than that, dated, as Insights charts it.
            DailySignal(spo2, l.value, within(spo2Normal, l.value), SignalTrend.STEADY, isEstimate = l.estimate, asOf = l.day)
        } ?: tile(spo2, false),
    )
}


// MARK: - Synthesis calendar (B2-2a-6, C-TRN-SYN)

/** The Synthesis calendar covers 20 weeks, Monday-first (the board's `nSW`). */
internal const val SYNTHESIS_WEEKS = 20

/** The three share buckets' Charge boundaries (checkpoint Q3): raw Charge < 40 / 40–69 / ≥ 70, not the stage floors. */
internal const val SYNTHESIS_STEADY_FLOOR = 40.0
internal const val SYNTHESIS_STRONG_FLOOR = 70.0

internal data class SynthesisCell(
    val date: LocalDate,
    /** A day in the future half of the current week: drawn invisible. */
    val future: Boolean,
    /** The 0–4 Synthesis stage (`SYNTHESIS_BAND_FLOORS`), null when Charge is untracked. */
    val band: Int?,
)

internal data class SynthesisTrend(
    val cells: List<SynthesisCell>,
    /** One short month name per week column, blank where it repeats the previous column. */
    val monthLabels: List<String>,
    val tracked: Int,
    /** Whole percents over tracked days, adjusted to sum to 100 (checkpoint Q3). */
    val strongOrPeak: Int,
    val steady: Int,
    val lowOrDepleted: Int,
)

/** A Charge score's 0–4 Synthesis stage (`SYNTHESIS_BAND_FLOORS`), the cell's colour and tooltip name. */
private fun synthesisStage(score: Double): Int =
    SYNTHESIS_BAND_FLOORS.indexOfLast { score.coerceIn(0.0, 100.0) >= it }.coerceAtLeast(0)

/** Three whole percents over tracked days, rounded and adjusted so they sum to 100 (largest remainder). */
private fun wholeShares(counts: List<Int>): List<Int> {
    val total = counts.sum()
    if (total == 0) return counts.map { 0 }
    val fractions = counts.map { it * 100.0 / total }
    val floor = fractions.map { it.toInt() }
    var remainder = 100 - floor.sum()
    val order = fractions.indices.sortedByDescending { fractions[it] - floor[it] }
    val result = floor.toMutableList()
    for (i in order) {
        if (remainder <= 0) break
        result[i] += 1
        remainder -= 1
    }
    return result
}

internal fun buildSynthesis(registry: MetricRegistry, today: LocalDate): SynthesisTrend {
    val start = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        .minusWeeks((SYNTHESIS_WEEKS - 1).toLong())
    val end = start.plusDays((SYNTHESIS_WEEKS * 7 - 1).toLong())
    val charge = registry.series(MetricId.CHARGE, DayKey(start), DayKey(end)).map { it.value }
    val cells = (0 until SYNTHESIS_WEEKS * 7).map { i ->
        val d = start.plusDays(i.toLong())
        val future = d.isAfter(today)
        SynthesisCell(d, future, if (future) null else charge[i]?.let(::synthesisStage))
    }
    val tracked = charge.filterIndexed { i, v -> v != null && !start.plusDays(i.toLong()).isAfter(today) }
        .filterNotNull()
    val (lowOrDepleted, steady, strongOrPeak) = wholeShares(
        listOf(
            tracked.count { it < SYNTHESIS_STEADY_FLOOR },
            tracked.count { it >= SYNTHESIS_STEADY_FLOOR && it < SYNTHESIS_STRONG_FLOOR },
            tracked.count { it >= SYNTHESIS_STRONG_FLOOR },
        ),
    ).let { Triple(it[0], it[1], it[2]) }
    val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val months = (0 until SYNTHESIS_WEEKS).map { start.plusWeeks(it.toLong()).format(monthFmt) }
    val labels = months.mapIndexed { i, m -> if (i > 0 && m == months[i - 1]) "" else m }
    return SynthesisTrend(
        cells = cells,
        monthLabels = labels,
        tracked = tracked.size,
        strongOrPeak = strongOrPeak,
        steady = steady,
        lowOrDepleted = lowOrDepleted,
    )
}

/** One workout's contribution to Intensity: its logical day, minutes, and v10's stored zone split (Z1…Z5, %). */

/** One workout's contribution to Intensity: its logical day, minutes, and v10's stored zone split (Z1…Z5, %). */
internal data class ZonedSession(val day: LocalDate, val minutes: Double, val zonePercents: List<Double>?)

internal data class IntensityWeek(val start: LocalDate, val low: Double, val moderate: Double, val high: Double) {
    val total: Double get() = low + moderate + high
}

internal data class IntensityTrend(val weeks: List<IntensityWeek>, val sessions: Int, val sessionsWithZones: Int) {
    val low: Double get() = weeks.sumOf { it.low }
    val moderate: Double get() = weeks.sumOf { it.moderate }
    val high: Double get() = weeks.sumOf { it.high }
    val total: Double get() = low + moderate + high
}

/**
 * Weekly minutes by intensity over the four Consistency weeks (05 §5): Low = Z1–2, Moderate = Z3, High = Z4–5, from
 * each workout's stored zone split times its duration, as the Workouts screen's zone bar ([zoneSummary]). A session
 * with no zone split adds nothing; nothing is estimated from daily Effort.
 */
internal fun buildIntensity(sessions: List<ZonedSession>, today: LocalDate): IntensityTrend {
    val start = fourWeekStart(today)
    val inWindow = sessions.filter { !it.day.isBefore(start) && !it.day.isAfter(today) }
    val weeks = (0 until 4).map { w ->
        val ws = start.plusWeeks(w.toLong())
        var low = 0.0
        var mod = 0.0
        var high = 0.0
        inWindow.filter { !it.day.isBefore(ws) && it.day.isBefore(ws.plusWeeks(1)) }.forEach { s ->
            val p = s.zonePercents ?: return@forEach
            if (s.minutes <= 0.0) return@forEach
            low += s.minutes * (p[0] + p[1]) / 100.0
            mod += s.minutes * p[2] / 100.0
            high += s.minutes * (p[3] + p[4]) / 100.0
        }
        IntensityWeek(ws, low, mod, high)
    }
    return IntensityTrend(weeks, inWindow.size, inWindow.count { it.zonePercents != null && it.minutes > 0.0 })
}

// MARK: - Charge vs earlier Effort (R-08, 05 §3 secondary insight)

/** The days the drill-down pairs over, and the fewest paired days it shows a correlation for. */
internal const val SCATTER_DAYS = 60
internal const val SCATTER_MIN_PAIRS = 21

/** One lag's scatter: each day's Charge against the Effort [lag] days before it, with Pearson r over the pairs. */
internal data class ChargeEffortScatter(
    val lag: Int,
    /** (Effort lag days before, Charge on the day), on the stored 0–100 Effort axis. */
    val points: List<Pair<Double, Double>>,
    /** Null under [SCATTER_MIN_PAIRS] pairs, or when either side doesn't vary. */
    val r: Double?,
) {
    val n: Int get() = points.size
}

/**
 * R-08 (B2 testing: Rest, not Charge): for each of the last [SCATTER_DAYS] days, its Rest (no carry) against the Effort [lag] days earlier, both from
 * the registry; days missing either value are left out. 05 §3: lags 1–3 (0 removed), shown from [SCATTER_MIN_PAIRS] pairs.
 */
internal fun buildChargeEffortScatter(registry: MetricRegistry, today: LocalDate, lag: Int): ChargeEffortScatter {
    val start = today.minusDays((SCATTER_DAYS - 1).toLong())
    val effort = registry.series(MetricId.EFFORT, DayKey(start.minusDays(lag.toLong())), DayKey(today)).map { it.value }
    val charge = registry.series(MetricId.REST, DayKey(start), DayKey(today)).map { it.value }
    val points = charge.indices.mapNotNull { i -> charge[i]?.let { c -> effort[i]?.let { e -> e to c } } }
    return ChargeEffortScatter(lag, points, if (points.size >= SCATTER_MIN_PAIRS) pearsonR(points) else null)
}

/** Pearson r over the pairs; null with fewer than 3 pairs or when either variable has no variance. */
internal fun pearsonR(xy: List<Pair<Double, Double>>): Double? {
    if (xy.size < 3) return null
    val mx = xy.sumOf { it.first } / xy.size
    val my = xy.sumOf { it.second } / xy.size
    var sxx = 0.0; var syy = 0.0; var sxy = 0.0
    for ((x, y) in xy) { sxx += (x - mx) * (x - mx); syy += (y - my) * (y - my); sxy += (x - mx) * (y - my) }
    if (sxx <= 0.0 || syy <= 0.0) return null
    return (sxy / kotlin.math.sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
}

internal data class TrendsFixedCards(
    val load: LoadTrend,
    val intensity: IntensityTrend,
    val consistency: ConsistencyTrend,
    /** R-08: the Charge vs earlier Effort drill-down, by lag (1, 2, 3 days). */
    val scatter: Map<Int, ChargeEffortScatter> = emptyMap(),
    /** B2-2a-6: the 20-week Synthesis heat grid. */
    val synthesis: SynthesisTrend,
    /** B2-2b-4: the four daily-signal sparkline tiles. */
    val signals: SignalsTrend,
)

/** Reads the workouts and builds the three fixed-window cards off the main thread. */
internal suspend fun loadTrendsFixedCards(
    vm: AppViewModel,
    days: List<DailyMetric>,
    restByDay: Map<String, Double>,
): TrendsFixedCards = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
    val today = logicalDayNow()
    val registry = com.noop.metrics.V10MetricRegistry(
        com.noop.metrics.V10MetricInputs(
            days = days, restByDay = restByDay, today = today,
            // R9a V2-3: the one need Consistency's moons measure against reads the age (#27 rules).
            age = ProfileStore.from(vm.getApplication<android.app.Application>()).knownAge,
        ),
    )
    val zone = java.time.ZoneId.systemDefault()
    val from = fourWeekStart(today).atStartOfDay(zone).toEpochSecond()
    val workouts = runCatching {
        com.noop.ui.WorkoutEditing.dedupCrossSource(
            vm.repo.workoutsAllSources(vm.deviceId, from, java.time.Instant.now().epochSecond),
        )
    }.getOrDefault(emptyList())
    val sessions = workouts.map { w ->
        // The workout page falls back to zones derived from the strap's own HR when no split was imported; do the
        // same here, or every strap-recorded session reads as "no heart-rate zones".
        val percents = parseZonePercents(w.zonesJSON) ?: runCatching {
            vm.workoutZoneMinutes(w.startTs, w.endTs, w.source, w.deviceId)
        }.getOrNull()?.let { mins ->
            val total = mins.sum()
            if (total > 0.0) mins.map { it / total * 100.0 } else null
        }
        ZonedSession(
            day = logicalDay(java.time.Instant.ofEpochSecond(w.startTs).atZone(zone)),
            minutes = (w.durationS ?: (w.endTs - w.startTs).toDouble()) / 60.0,
            zonePercents = percents,
        )
    }
    TrendsFixedCards(
        load = buildLoadTrend(registry, days, today),
        intensity = buildIntensity(sessions, today),
        consistency = buildConsistency(registry, today),
        scatter = (1..3).associateWith { buildChargeEffortScatter(registry, today, it) },
        synthesis = buildSynthesis(registry, today),
        signals = buildSignals(registry, today),
    )
}
