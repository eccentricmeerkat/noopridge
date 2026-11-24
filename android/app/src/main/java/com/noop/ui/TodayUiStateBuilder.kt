package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.MetricValue
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import java.time.LocalDate

/**
 * Everything [buildTodayUiState] needs, gathered by `TodayScreen` from its view-model flows, prefs and async
 * loads. Plain data: no Context, no clock reads, no localisation, so the builder is a pure function a JVM test
 * can call (C-01 part 2, R3c-1).
 *
 * [todayDate] is the LOGICAL today (`logicalDayNow()`, rolls at 04:00); [localCalendarDate] is the plain local
 * calendar date. The carry-over upper bound needs both (#547). [restByDay] is the resolved `sleep_performance`
 * series (imported wins) the Rest orb and tile read.
 */
internal data class TodayInputs(
    val days: List<DailyMetric>,
    val today: DailyMetric?,
    val todayDate: LocalDate,
    val localCalendarDate: LocalDate,
    val selectedDayOffset: Int,
    val liveTodayStrain: Double?,
    val hrvBaselineEpoch: Double,
    val restByDay: Map<String, Double> = emptyMap(),
    /** The SpO₂ strap estimate by day, Blood Oxygen's last fallback (#1599, R9b). */
    val spo2CandidateByDay: Map<String, Double> = emptyMap(),
    val spo2DeviceUnsupported: Boolean = false,
    val spo2EstimateOn: Boolean = true,
)

/**
 * The derived Today read-out: which day is on screen, the row it shows, the resolved Effort, the Charge
 * calibration count, the carry-over rows (#543, and the per-field vitals carries), and the score state.
 * Every surface that shows one of these reads it from here, so the hero, tiles and cards cannot disagree.
 *
 * R1b (J-01): [charge], [effort] and [rest] are the registry's [MetricValue]s for the selected day, and the
 * fields the surfaces read (`recoveryCalibration`, `lastScoredRecoveryDay`, `effortForDay`, `restScore`) are
 * taken from them, so the orb, tile, Synthesis and breakdown all read one value per metric.
 */
internal data class TodayUiState(
    val selectedDay: LocalDate,
    val selectedDayKey: String,
    val isToday: Boolean,
    val displayMetric: DailyMetric?,
    val effortForDay: Double?,
    val recoveryCalibration: Int?,
    val carryOverTodayKey: String,
    val lastScoredRecoveryDay: DailyMetric?,
    val lastVitalsDay: DailyMetric?,
    val lastSpo2Day: DailyMetric?,
    val lastSkinTempDay: DailyMetric?,
    val lastHrvDay: DailyMetric?,
    val lastRestingHrDay: DailyMetric?,
    val lastRespDay: DailyMetric?,
    val scoreState: ScoreState,
    val charge: MetricValue,
    val effort: MetricValue,
    val rest: MetricValue,
    val restScore: Double?,
    /** R9b: the registry's HRV, resting HR, respiratory rate and SpO₂ for the selected day, the values the Key Metrics
     *  vitals tiles show ([vitalTileValues] is held equal to these by `TodayRegistryIdentityTest`). */
    val vitals: Map<MetricId, MetricValue> = emptyMap(),
)

/**
 * The pure Today state builder (C-01 part 2). Each derivation is the expression `TodayScreen` used inline,
 * moved verbatim, so behaviour is unchanged; the comments on why each rule exists stay with it. Since R1b, Charge,
 * Effort and Rest come from [V10MetricRegistry], whose today path reproduces those same expressions (J-01f);
 * `TodayRegistryIdentityTest` holds the two equal.
 */
internal fun buildTodayUiState(i: TodayInputs): TodayUiState {
    val isToday = i.selectedDayOffset == 0
    // #860: a fresh launch lands on today; past offsets count back from the logical-day anchor.
    val selectedDay = i.todayDate.minusDays(i.selectedDayOffset.toLong())
    // The key the day-scoped read-outs key on. At offset 0 it follows the resolver's `today?.day` so it tracks
    // the row actually surfaced, including the non-UTC pre-04:00 case (#304). Past offsets use the logical key.
    val selectedDayKey = if (isToday) i.today?.day ?: selectedDay.toString() else selectedDay.toString()
    val historicalMetric = i.days.lastOrNull { it.day == selectedDayKey }
    val displayMetric = if (isToday) i.today ?: historicalMetric else historicalMetric
    // #547: the LATER of the logical today and the local calendar day bounds every carry, so a just-after-
    // midnight carry of yesterday survives while a future-dated row (bad strap clock) is excluded.
    val carryOverTodayKey = maxOf(i.todayDate.toString(), i.localCalendarDate.toString())
    // R1b: one registry per state. On today the registry's "today" is the selected key (the resolver's row, #304)
    // and that row wins for its day; off today it is the logical today, which a past offset can never equal.
    val registry = V10MetricRegistry(
        V10MetricInputs(
            days = i.days,
            restByDay = i.restByDay,
            today = if (isToday) java.time.LocalDate.parse(selectedDayKey) else i.todayDate,
            hrvBaselineEpoch = i.hrvBaselineEpoch,
            todayRow = if (isToday) displayMetric else null,
            carryBound = carryOverTodayKey,
            liveTodayStrain = i.liveTodayStrain,
            spo2CandidateByDay = i.spo2CandidateByDay,
            spo2DeviceUnsupported = i.spo2DeviceUnsupported,
            spo2EstimateOn = i.spo2EstimateOn,
        ),
    )
    val dayKey = DayKey.of(selectedDayKey)
    val charge = registry.value(MetricId.CHARGE, dayKey)
    val effort = registry.value(MetricId.EFFORT, dayKey)
    val rest = registry.value(MetricId.REST, dayKey)
    // #1001: Effort resolved ONCE for the whole screen, never below the day's already-earned value.
    val effortForDay = effort.value
    // PR #85: "calibrating, N of 4 nights" while the HRV baseline is below the seed gate; epoch-aware (Bug B).
    // Today only: the registry sets progress only on its today.
    val recoveryCalibration: Int? = charge.progress?.done
    // #543: the ONE prior scored row every recovery-derived read-out carries over from, on today only: the row
    // the registry's Charge carried from.
    val lastScoredRecoveryDay = charge.carriedFrom?.let { from ->
        i.days.lastOrNull { it.recovery != null && it.day == from.toString() }
    }
    // Per-field vitals carries (#543 follow-up, #1331, #1842, #1844), strictly prior, future-clock safe.
    val vitalsBound = maxOf(displayMetric?.day ?: "", carryOverTodayKey)
    fun carry(row: (List<DailyMetric>, String) -> DailyMetric?): DailyMetric? =
        if (isToday) row(i.days, vitalsBound) else null
    val scoreState: ScoreState = if (isToday) {
        scoreStateForToday(
            todayRecovery = displayMetric?.recovery,
            calibratingNights = recoveryCalibration,
            carriedDay = lastScoredRecoveryDay,
            today = carryOverTodayKey,
        )
    } else {
        ScoreState.Scored(displayMetric?.recovery ?: 0.0)
    }
    return TodayUiState(
        selectedDay = selectedDay,
        selectedDayKey = selectedDayKey,
        isToday = isToday,
        displayMetric = displayMetric,
        effortForDay = effortForDay,
        recoveryCalibration = recoveryCalibration,
        carryOverTodayKey = carryOverTodayKey,
        lastScoredRecoveryDay = lastScoredRecoveryDay,
        lastVitalsDay = carry(::lastVitalsRow),
        lastSpo2Day = carry(::lastSpo2Row),
        lastSkinTempDay = carry(::lastSkinTempReadingRow),
        lastHrvDay = carry(::lastHrvRow),
        lastRestingHrDay = carry(::lastRestingHrRow),
        lastRespDay = carry(::lastRespRow),
        scoreState = scoreState,
        charge = charge,
        effort = effort,
        rest = rest,
        restScore = rest.value,
        vitals = listOf(MetricId.HRV, MetricId.RESTING_HR, MetricId.RESP_RATE, MetricId.SPO2)
            .associateWith { registry.value(it, dayKey) },
    )
}
