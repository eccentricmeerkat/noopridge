package com.noop.metrics

import com.noop.analytics.Baselines
import com.noop.analytics.HrZones
import com.noop.analytics.MetricCfg
import com.noop.analytics.ReadinessEngine
import com.noop.analytics.RestScorer
import com.noop.analytics.ScoreConfidence
import com.noop.analytics.SleepDebt
import com.noop.analytics.StrainScorer
import com.noop.analytics.TrainingLoadEngine
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.ui.StressBand
import com.noop.ui.StressModel
import com.noop.ui.SYNTHESIS_BAND_FLOORS
import com.noop.ui.chargeConfidenceTier
import com.noop.ui.freshRestScore
import com.noop.ui.lastScoredRecoveryDay
import com.noop.ui.lastRespRow
import com.noop.ui.lastSpo2Row
import com.noop.ui.hoursVsNeededPct
import com.noop.ui.recoveryCalibrationNights
import com.noop.ui.sleepDebtNeedMin
import com.noop.ui.sleepTabNeedMin
import java.time.LocalDate

/**
 * The v10 inputs the registry reads: the same rows the screens already hold.
 *
 * [days] is the merged daily history (`WhoopRepository.daysMerged`), oldest first. [restByDay] is the stored
 * `sleep_performance` series Today and Sleep show as Rest. [storedStress] is the stored `stress` series
 * [StressModel] folds in. [today] is the local day.
 *
 * R1 (J-01) adds what the other v10 surfaces read:
 * - [age] (whole years, null when unknown) and [maxHrOverride] (Settings), for max HR;
 * - [restNeedHours], the need the latest routine scoring run used for Rest (`computeHabitualSleep`, 21-day
 *   window). v10 does not store it per day, so it is today's value only;
 * - [importedNeedMin] / [importedDebtMin] (WHOOP export, win per day) and [napSleepMinByDay], as the Sleep tab;
 * - [caloriesByDay], Today's resolved active energy (the strap's estimate first, then the imported figure, #616 and owner 21 Nov);
 * - [computedSeries], the weekly engine series by key (`vo2max_est`, `fitness_age`, `vitality`, `body_age`);
 * - [importedStepsByDay] (`stepsForDay`: the larger Apple / Health Connect count) and [stepsEstimateByDay]
 *   (`steps_est`, resolved), the Steps tile's fallbacks after the strap's own count (#107/#150).
 *
 * R1b (Today reads through the registry) adds what Today's own derivation uses, so the two are identical:
 * - [todayRow], the resolver's row for today (`AppViewModel.today`), which wins over [days] for its day (#304);
 * - [carryBound], the #547 carry upper bound (the later of the logical and the calendar today);
 * - [liveTodayStrain], the in-progress Effort, never shown below the stored value (#1001).
 */
data class V10MetricInputs(
    val days: List<DailyMetric>,
    val restByDay: Map<String, Double> = emptyMap(),
    val storedStress: Map<String, Double> = emptyMap(),
    val today: LocalDate,
    val hrvBaselineEpoch: Double = 0.0,
    val age: Int? = null,
    val maxHrOverride: Double? = null,
    val restNeedHours: Double? = null,
    val importedNeedMin: Map<String, Double> = emptyMap(),
    val importedDebtMin: Map<String, Double> = emptyMap(),
    val napSleepMinByDay: Map<String, Double> = emptyMap(),
    val caloriesByDay: Map<String, Double> = emptyMap(),
    val computedSeries: Map<String, Map<String, Double>> = emptyMap(),
    val importedStepsByDay: Map<String, Int> = emptyMap(),
    val stepsEstimateByDay: Map<String, Double> = emptyMap(),
    val todayRow: DailyMetric? = null,
    val carryBound: String? = null,
    val liveTodayStrain: Double? = null,
    /** The SpO₂ strap estimate by day (`AppViewModel.spo2CandidateByDay`), Blood Oxygen's last fallback (#1599). */
    val spo2CandidateByDay: Map<String, Double> = emptyMap(),
    /** J-06 (R9b): the selected strap is a WHOOP 4.0, which gives NOOP no SpO₂ stream (no v18 aux, #103). */
    val spo2DeviceUnsupported: Boolean = false,
    /** J-06 (R9b): the SpO₂ strap estimate is switched on (`NoopPrefs.spo2CandidateDisplay`, default off). */
    val spo2EstimateOn: Boolean = true,
    /** RS-3c: each night's local bed and wake times by wake day, from the Sleep tab's main-night selector. */
    val bedWakeByDay: Map<String, com.noop.ui.SleepScore.BedWake> = emptyMap(),
    /** The R9a corrections in force ([MetricsV2]); empty reproduces v10 exactly. */
    val v2: Set<V2Change> = MetricsV2.active,
)

/**
 * [MetricRegistry] over v10 (Sprint 0, S0-07). Every value is read or computed by the existing v10 code. Nothing is
 * re-derived here, so the registry returns what the screens show today. Where v10 has no rule, the answer is null
 * rather than an invented one:
 * - provenance is not tracked per value in v10, so it is null;
 * - Effort and Rest have no v10 bands (DIFFERENCES #9, M-EFF).
 *
 * Missing reasons are coarse: v10 cannot tell "not worn" from "not synced", so a day without the field reads
 * [MissingReason.NOT_SYNCED]. Calibration reads [MissingReason.INSUFFICIENT_HISTORY].
 */
class V10MetricRegistry(private val inputs: V10MetricInputs) : MetricRegistry {

    private val ordered = inputs.days.sortedBy { it.day }
    private val byDay = ordered.associateBy { it.day }
    private val todayKey = inputs.today.toString()
    private val carryBoundKey = inputs.carryBound ?: todayKey

    // Whole-history derivations the Sleep tab computes once per load (latest-anchored, never per browsed day).
    private val sleepTabNeed by lazy { sleepTabNeedMin(ordered) }
    private val debtNeed by lazy { sleepDebtNeedMin(ordered) }
    private val debtByDay by lazy {
        SleepDebt.debtSeries(
            series = ordered.map { it.day to SleepDebt.creditedSleepMin(it.totalSleepMin, inputs.napSleepMinByDay[it.day] ?: 0.0) },
            needHours = debtNeed / 60.0,
            importedDebtMin = inputs.importedDebtMin,
        ).toMap()
    }
    private val loads by lazy { ordered.map { TrainingLoadEngine.DailyLoad(it.day, it.strain) } }

    private fun v2(change: V2Change) = MetricsV2.isOn(change, inputs.v2)

    // R9a V2-1 (S0-3): the one need, per day, and what Hours vs needed and debt measure against with it on.
    private val asleepByDay by lazy { ordered.associate { it.day to it.totalSleepMin } }
    private fun oneNeedMin(key: String): Double = oneSleepNeedHours(asleepByDay, LocalDate.parse(key), inputs.age) * 60.0
    private val oneNeedDebtByDay by lazy {
        val latest = ordered.lastOrNull()?.day ?: return@lazy emptyMap()
        SleepDebt.debtSeries(
            series = ordered.map { it.day to SleepDebt.creditedSleepMin(it.totalSleepMin, inputs.napSleepMinByDay[it.day] ?: 0.0) },
            needHours = oneNeedMin(latest) / 60.0,
            importedDebtMin = inputs.importedDebtMin,
        ).toMap()
    }

    /**
     * RS-3c: the Sleep score's parts for the night that ended on [day], or null when an input is missing. The need is the one
     * that stood going into the night (the day before's): the Sleep page's Sleep needed, that is the base need, plus the
     * recent-Effort part, plus the payback of the debt carried in. The night itself never raises its own need.
     */
    internal fun sleepScoreParts(day: LocalDate): com.noop.ui.SleepScore.Parts? {
        val key = day.toString()
        val row = byDay[key] ?: return null
        val tonight = inputs.bedWakeByDay[key] ?: return null
        val prior = (1..com.noop.ui.SleepScore.PRIOR_NIGHTS).mapNotNull { inputs.bedWakeByDay[day.minusDays(it.toLong()).toString()] }
        val before = day.minusDays(1)
        val effort = if (v2(V2Change.SLEEP_NEED_EFFORT_PART)) {
            val (that, history) = com.noop.ui.SleepNeedParts.effortInputs(ordered, before)
            com.noop.ui.SleepNeedParts.effortPartMin(that, history) ?: 0
        } else 0
        val debtIn = if (v2(V2Change.ONE_SLEEP_NEED)) oneNeedDebtByDay else debtByDay
        val debt = ordered.lastOrNull { it.day < key && debtIn[it.day] != null }?.let { debtIn[it.day] }
        val payback = if (v2(V2Change.SLEEP_NEED_DEBT_PART)) com.noop.ui.RecommendedBedtime.paybackMin(debt) else 0
        return com.noop.ui.SleepScore.parts(
            asleepMin = row.totalSleepMin,
            needMin = oneNeedMin(before.toString()) + effort + payback,
            efficiencyPct = row.efficiency?.let { com.noop.ui.efficiencyPercent(it) },
            consistency = com.noop.ui.SleepScore.consistency(tonight, prior),
        )
    }

    override fun value(id: MetricId, day: DayKey): MetricValue = compute(id, day, allowCarry = true)

    override fun series(id: MetricId, from: DayKey, to: DayKey): List<MetricValue> {
        val out = ArrayList<MetricValue>()
        var d = from.date
        while (!d.isAfter(to.date)) {
            out.add(compute(id, DayKey(d), allowCarry = false))
            d = d.plusDays(1)
        }
        return out
    }

    override fun band(id: MetricId, value: Double): Band? = when (id) {
        // Synthesis words on Today (TodayScreen.synthesisWord / SYNTHESIS_BAND_FLOORS).
        MetricId.CHARGE -> {
            val keys = listOf("depleted", "low", "steady", "strong", "peak")
            val i = SYNTHESIS_BAND_FLOORS.indexOfLast { value.coerceIn(0.0, 100.0) >= it }.coerceAtLeast(0)
            Band(keys[i], SYNTHESIS_BAND_FLOORS[i], SYNTHESIS_BAND_FLOORS.getOrNull(i + 1))
        }
        // StressScreen's LOW / MEDIUM / HIGH.
        MetricId.STRESS -> when (StressBand.forScore(value)) {
            StressBand.Low -> Band("low", 0.0, 1.0)
            StressBand.Medium -> Band("medium", 1.0, 2.0)
            StressBand.High -> Band("high", 2.0, null)
        }
        // ReadinessEngine.Level, stored as its ordinal (Today's Push / Maintain / Rest chip reads the level).
        MetricId.READINESS -> ReadinessEngine.Level.entries.getOrNull(value.toInt())?.let {
            Band(it.name.lowercase(), it.ordinal.toDouble(), it.ordinal + 1.0)
        }
        else -> null
    }

    /**
     * The range [VitalBands.band] judges the Health tiles against: baseline ± [VitalBands.sigmaK]·σ once the
     * personal baseline is trusted, else the fixed population range `HealthVitalsLogic.vitalsFor` passes. History
     * is the calendar-padded series strictly before [day], as there.
     */
    override fun normalRange(id: MetricId, day: DayKey): NormalRange? {
        if (v2(V2Change.NORMAL_BAND_60)) normalRange60(id, day)?.let { return it }
        val (selector, population, cfg) = when (id) {
            MetricId.HRV -> Triple({ d: DailyMetric -> d.avgHrv }, 40.0..120.0, Baselines.hrvCfg)
            MetricId.RESTING_HR -> Triple({ d: DailyMetric -> d.restingHr?.toDouble() }, 40.0..60.0, Baselines.restingHRCfg)
            MetricId.RESP_RATE -> Triple({ d: DailyMetric -> d.respRateBpm }, 12.0..20.0, Baselines.respCfg)
            MetricId.SPO2 -> Triple({ d: DailyMetric -> d.spo2Pct }, 95.0..100.0, null as MetricCfg?)
            else -> return null
        }
        val key = day.toString()
        val history = VitalBands.calendarSeries(ordered.filter { it.day < key }.map { it.day to selector(it) })
        if (cfg != null) {
            val state = Baselines.foldHistory(history, cfg)
            if (state.trusted) {
                val half = VitalBands.sigmaK * Baselines.sigma(state)
                return NormalRange(state.baseline - half, state.baseline + half, NormalRange.Basis.PERSONAL, state.nValid)
            }
            return NormalRange(population.start, population.endInclusive, NormalRange.Basis.POPULATION, state.nValid)
        }
        return NormalRange(population.start, population.endInclusive, NormalRange.Basis.POPULATION, 0)
    }

    /**
     * R9a V2-4 (D-05, J-04): the 60-night band for HRV, RHR, respiratory rate and skin temperature. Before night 14
     * it falls back to v10's population range where v10 has one (nights = the tracked count), else null. Other ids
     * return null here and keep v10's rule.
     */
    private fun normalRange60(id: MetricId, day: DayKey): NormalRange? {
        val selector: (DailyMetric) -> Double? = when (id) {
            MetricId.HRV -> { d -> d.avgHrv }
            MetricId.RESTING_HR -> { d -> d.restingHr?.toDouble() }
            MetricId.RESP_RATE -> { d -> d.respRateBpm }
            MetricId.SKIN_TEMP_DEV -> { d -> d.skinTempDevC }
            else -> return null
        }
        val key = day.toString()
        val history = ordered.filter { it.day < key }.map(selector)
        normalBand60(history, normalBandMinWidth(id))?.let { return it }
        val population = when (id) {
            MetricId.HRV -> 40.0..120.0
            MetricId.RESTING_HR -> 40.0..60.0
            MetricId.RESP_RATE -> 12.0..20.0
            else -> null
        }
        return population?.let { NormalRange(it.start, it.endInclusive, NormalRange.Basis.POPULATION, normalBandNights(history)) }
    }

    override fun definition(id: MetricId): MetricDefinition = DEFINITIONS.getValue(id)

    private fun compute(id: MetricId, day: DayKey, allowCarry: Boolean): MetricValue {
        val key = day.toString()
        val isToday = key == todayKey
        val row = if (isToday) inputs.todayRow ?: byDay[key] else byDay[key]
        val unit = DEFINITIONS.getValue(id).unit
        return when (id) {
            MetricId.CHARGE, MetricId.HRV, MetricId.RESTING_HR, MetricId.RESP_RATE, MetricId.SPO2 ->
                recoveryDerived(id, day, row, isToday && allowCarry, unit, allowCarry)
            MetricId.REST -> {
                val own = inputs.restByDay[key]
                // As Today (#977): the newest night in the series, which freshRestScore then freshness-gates.
                // R9a V2-6 (#20): bounded by the #547 key, as Charge, so a future-dated night never carries.
                val last = if (v2(V2Change.REST_CARRY_BOUND)) {
                    inputs.restByDay.filterKeys { it <= carryBoundKey }.maxByOrNull { it.key }
                } else {
                    inputs.restByDay.maxByOrNull { it.key }
                }
                val shown = if (allowCarry) {
                    freshRestScore(own, last?.key, last?.value, isToday, todayKey)
                } else {
                    own
                }
                val carried = if (own == null && shown != null) last?.key?.let(DayKey::of) else null
                plain(id, day, shown, unit, carriedFrom = carried)
            }
            // #1001: Today's one Effort resolution: the live value on today, never below the stored one.
            MetricId.EFFORT -> plain(
                id, day, StrainScorer.effectiveEffort(if (isToday) inputs.liveTodayStrain else null, row?.strain), unit,
            )
            MetricId.SLEEP_NEED_REST ->
                if (isToday) plain(id, day, inputs.restNeedHours, unit, missing = MissingReason.INSUFFICIENT_HISTORY)
                else plain(id, day, null, unit, missing = MissingReason.NOT_STORED)
            MetricId.SLEEP_NEED_STORED -> plain(id, day, RestScorer.defaultSleepNeedHours, unit)
            MetricId.SLEEP_NEED_SLEEP_TAB -> plain(id, day, inputs.importedNeedMin[key] ?: sleepTabNeed, unit)
            MetricId.SLEEP_NEED_DEBT -> plain(id, day, debtNeed, unit)
            MetricId.SLEEP_NEED -> plain(id, day, oneNeedMin(key) / 60.0, unit)
            MetricId.HOURS_VS_NEEDED -> {
                // R9a-f: with the one need on, an imported WHOOP need no longer wins on its day (owner, 2026-11-14).
                val need = if (v2(V2Change.ONE_SLEEP_NEED)) oneNeedMin(key) else inputs.importedNeedMin[key] ?: sleepTabNeed
                plain(id, day, hoursVsNeededPct(row?.totalSleepMin, need), unit)
            }
            MetricId.SLEEP_DEBT -> plain(id, day, (if (v2(V2Change.ONE_SLEEP_NEED)) oneNeedDebtByDay else debtByDay)[key], unit)
            MetricId.SLEEP_SCORE -> plain(
                id, day, if (v2(V2Change.SLEEP_SCORE)) sleepScoreParts(LocalDate.parse(key))?.score else null, unit,
                missing = MissingReason.INSUFFICIENT_HISTORY,
            )
            MetricId.TRAINING_LOAD_CTL, MetricId.TRAINING_LOAD_ATL, MetricId.TRAINING_FORM -> {
                // TrainingLoadCard models the full history through its latest day; through = the asked day.
                val result = if (byDay.containsKey(key)) TrainingLoadEngine.evaluate(loads, through = key) else null
                val point = result?.takeIf { it.isAvailable }?.points?.lastOrNull()
                val v = when (id) {
                    MetricId.TRAINING_LOAD_CTL -> point?.ctl
                    MetricId.TRAINING_LOAD_ATL -> point?.atl
                    else -> point?.tsb
                }
                plain(id, day, v, unit, missing = MissingReason.INSUFFICIENT_HISTORY)
            }
            MetricId.ACWR, MetricId.READINESS -> {
                // R9a V2-5 (#25): a past day sees only its own history, not the rows after it.
                val rows = if (v2(V2Change.PAST_DAY_LOAD)) ordered.filter { it.day <= key } else ordered
                val readiness = ReadinessEngine.evaluate(rows, today = key)
                val v = if (id == MetricId.ACWR) readiness.acwr else readiness.level.ordinal.toDouble()
                plain(id, day, v, unit, missing = MissingReason.INSUFFICIENT_HISTORY)
            }
            MetricId.ACTIVE_KCAL -> plain(id, day, inputs.caloriesByDay[key], unit)
            MetricId.MAX_HR -> {
                // HrZones.zones: the Settings override, else Tanaka from age. No age and no override: no zones.
                val v = when {
                    inputs.maxHrOverride != null -> inputs.maxHrOverride
                    inputs.age != null && inputs.age > 0 -> HrZones.zones(age = inputs.age.toDouble()).maxHR
                    else -> null
                }
                plain(id, day, v, unit, missing = MissingReason.NEEDS_AGE)
            }
            // R9a V2-7 (#27): with no age entered, the age-derived readings need one (the engine's anchored 30 is
            // not shown). Vitality is a score, not an age reading, and stays.
            MetricId.VO2_MAX, MetricId.FITNESS_AGE, MetricId.VITALITY, MetricId.BODY_AGE ->
                if (id != MetricId.VITALITY && v2(V2Change.NO_ASSUMED_AGE) && inputs.age == null) {
                    plain(id, day, null, unit, missing = MissingReason.NEEDS_AGE)
                } else {
                    weekly(id, day, unit, allowCarry)
                }
            MetricId.SKIN_TEMP_DEV -> plain(id, day, row?.skinTempDevC, unit)
            MetricId.SLEEP_DURATION -> plain(id, day, row?.totalSleepMin, unit)
            // The Steps tile's precedence (TodayKeyMetrics, #107/#150): on-device count, else imported, else the
            // on-device estimate rounded to whole steps. Sprint 0 read only the first (found by the inspector).
            MetricId.STEPS -> plain(
                id, day,
                (row?.steps ?: inputs.importedStepsByDay[key] ?: inputs.stepsEstimateByDay[key]?.let { Math.round(it).toInt() })
                    ?.toDouble(),
                unit,
            )
            MetricId.STRESS -> {
                val upTo = ordered.filter { it.day <= key }
                val model = StressModel.build(upTo, inputs.storedStress)
                // StressModel scores the newest day with signal (#543 carry). Off today, only that day's own score counts.
                val scoredDay = upTo.lastOrNull {
                    it.restingHr != null || it.avgHrv != null || inputs.storedStress.containsKey(it.day)
                }?.day
                when {
                    model == null -> plain(id, day, null, unit)
                    scoredDay == key -> plain(id, day, model.score, unit)
                    allowCarry && isToday && scoredDay != null ->
                        plain(id, day, model.score, unit, carriedFrom = DayKey.of(scoredDay))
                    else -> plain(id, day, null, unit)
                }
            }
        }
    }

    /** Charge and the vitals that carry with it onto today (#543), from the one prior scored row Today uses. */
    private fun recoveryDerived(
        id: MetricId,
        day: DayKey,
        row: DailyMetric?,
        carryToday: Boolean,
        unit: MetricUnit,
        allowCarry: Boolean = carryToday,
    ): MetricValue {
        val key = day.toString()
        val history = ordered.filter { it.day <= key }
        // Today's call site gates past days to null: calibration is a today-only state. Like TodayUiStateBuilder,
        // today reads the whole history and the carry is bounded by the #547 key, not by the day itself.
        val calibrationNights = if (key == todayKey) {
            recoveryCalibrationNights(ordered, row?.recovery != null, inputs.hrvBaselineEpoch)
        } else {
            null
        }
        val calibrating = calibrationNights != null
        val carry = if (carryToday) {
            lastScoredRecoveryDay(ordered, key, isToday = true, todayScored = row?.recovery != null,
                isCalibrating = calibrating, today = carryBoundKey)
        } else {
            null
        }
        // R9b: Charge reads the carried row whole. The vitals read as Today's Key Metrics tiles do (#1331, #1599): the
        // day's own field first (a re-analysed night can lose its Charge but keep its HRV), then the Charge carry,
        // then for respiratory rate and SpO₂ the freshest earlier row with that field, and for SpO₂ last the strap
        // estimate. `vitalTileValues` is the tiles' copy of this rule; `TodayRegistryIdentityTest` holds them equal.
        val vitalsBound = maxOf(row?.day ?: "", carryBoundKey)
        val source: DailyMetric? = if (id == MetricId.CHARGE) {
            carry ?: row
        } else {
            listOfNotNull(row, carry).firstOrNull { field(id, it) != null } ?: when {
                !carryToday -> null
                id == MetricId.RESP_RATE -> lastRespRow(ordered, vitalsBound)
                id == MetricId.SPO2 -> lastSpo2Row(ordered, vitalsBound)
                else -> null
            }
        }
        // BS-x2-2 item 9: a daily SERIES read (no carry) falls back to the strap estimate too, so Insights and Trends
        // show the same value as the Key Metrics tile.
        val candidate = if (id == MetricId.SPO2 && source == null && (allowCarry || v2(V2Change.SPO2_ONE_SOURCE))) {
            row?.day?.let { inputs.spo2CandidateByDay[it] } ?: inputs.spo2CandidateByDay[key]
        } else {
            null
        }
        val v = source?.let { field(id, it) } ?: candidate
        val confidence = when {
            id != MetricId.CHARGE -> Confidence.SETTLED
            calibrating -> Confidence.CALIBRATING
            chargeConfidenceTier(history, source, inputs.hrvBaselineEpoch) == ScoreConfidence.CALIBRATING -> Confidence.CALIBRATING
            else -> Confidence.SETTLED
        }
        return MetricValue(
            id = id, day = day, value = v, unit = unit, provenance = null, confidence = confidence,
            missing = when {
                v != null -> null
                calibrating && id == MetricId.CHARGE -> MissingReason.INSUFFICIENT_HISTORY
                // J-06 (R9a): a night was recorded but has no HRV, so it was staged without the signal.
                id == MetricId.HRV && (source?.totalSleepMin ?: 0.0) > 0.0 -> MissingReason.UNSTAGED
                // J-06 (R9b): why Blood Oxygen is blank, when no measured reading or estimate exists.
                id == MetricId.SPO2 && inputs.spo2DeviceUnsupported -> MissingReason.DEVICE_UNSUPPORTED
                id == MetricId.SPO2 && !inputs.spo2EstimateOn -> MissingReason.ESTIMATOR_OFF
                else -> MissingReason.NOT_SYNCED
            },
            carriedFrom = source?.takeIf { it !== row && it.day != row?.day }?.let { DayKey.of(it.day) },
            provenanceNote = if (candidate != null) ValueNote.STRAP_ESTIMATE else null,
            progress = if (id == MetricId.CHARGE && calibrationNights != null) {
                Progress(calibrationNights, Baselines.minNightsSeed)
            } else {
                null
            },
        )
    }

    private fun field(id: MetricId, d: DailyMetric): Double? = when (id) {
        MetricId.CHARGE -> d.recovery
        MetricId.HRV -> d.avgHrv
        MetricId.RESTING_HR -> d.restingHr?.toDouble()
        MetricId.RESP_RATE -> d.respRateBpm
        MetricId.SPO2 -> d.spo2Pct
        else -> null
    }

    private fun plain(
        id: MetricId,
        day: DayKey,
        v: Double?,
        unit: MetricUnit,
        carriedFrom: DayKey? = null,
        missing: MissingReason = MissingReason.NOT_SYNCED,
    ) = MetricValue(
        id = id, day = day, value = v, unit = unit, provenance = null, confidence = Confidence.SETTLED,
        missing = if (v == null) missing else null, carriedFrom = carriedFrom,
    )

    /**
     * The weekly engine series (Saturday-keyed, `IntelligenceEngine.fitnessAgeRows` / vitality). Health shows the
     * latest reading (`latestMetricComputedUnion`), so [value] carries the newest reading on or before [day];
     * [series] keeps only the stored Saturdays, gaps preserved.
     */
    private fun weekly(id: MetricId, day: DayKey, unit: MetricUnit, allowCarry: Boolean): MetricValue {
        val key = day.toString()
        val stored = inputs.computedSeries[WEEKLY_KEYS.getValue(id)].orEmpty()
        stored[key]?.let { return plain(id, day, it, unit) }
        if (!allowCarry) return plain(id, day, null, unit, missing = MissingReason.INSUFFICIENT_HISTORY)
        val last = stored.filterKeys { it < key }.maxByOrNull { it.key }
            ?: return plain(id, day, null, unit, missing = MissingReason.INSUFFICIENT_HISTORY)
        return plain(id, day, last.value, unit, carriedFrom = DayKey.of(last.key))
    }

    companion object {
        /** Units, display precision and scales as v10 shows them; formulas are frozen in docs/ridge-build/metrics/. */
        val DEFINITIONS: Map<MetricId, MetricDefinition> = listOf(
            MetricDefinition(MetricId.CHARGE, MetricUnit.PERCENT, 0, 0.0, 100.0, "M-CHG.md"),
            MetricDefinition(MetricId.EFFORT, MetricUnit.EFFORT_0_100, 1, 0.0, 100.0, "M-EFF.md"),
            MetricDefinition(MetricId.REST, MetricUnit.PERCENT, 0, 0.0, 100.0, "M-RST.md"),
            MetricDefinition(MetricId.HRV, MetricUnit.MILLISECONDS, 0, null, null, "M-HRV.md"),
            MetricDefinition(MetricId.RESTING_HR, MetricUnit.BPM, 0, null, null, "M-RHR.md"),
            MetricDefinition(MetricId.RESP_RATE, MetricUnit.BREATHS_PER_MIN, 1, null, null, "M-RESP.md"),
            MetricDefinition(MetricId.SPO2, MetricUnit.PERCENT, 0, null, 100.0, "M-SPO2.md"),
            MetricDefinition(MetricId.SKIN_TEMP_DEV, MetricUnit.CELSIUS_DELTA, 1, null, null, "M-SKIN.md"),
            MetricDefinition(MetricId.SLEEP_DURATION, MetricUnit.MINUTES, 0, 0.0, null, "M-NEED.md"),
            MetricDefinition(MetricId.STEPS, MetricUnit.STEPS, 0, 0.0, null, "M-STEP.md"),
            MetricDefinition(MetricId.STRESS, MetricUnit.STRESS_0_3, 1, 0.0, 3.0, "M-STR.md"),
            MetricDefinition(MetricId.SLEEP_NEED_REST, MetricUnit.HOURS, 1, 0.0, 9.5, "M-NEED.md"),
            MetricDefinition(MetricId.SLEEP_NEED_STORED, MetricUnit.HOURS, 1, 0.0, null, "M-NEED.md"),
            MetricDefinition(MetricId.SLEEP_NEED_SLEEP_TAB, MetricUnit.MINUTES, 0, 0.0, null, "M-NEED.md"),
            MetricDefinition(MetricId.SLEEP_NEED_DEBT, MetricUnit.MINUTES, 0, 0.0, 570.0, "M-NEED.md"),
            MetricDefinition(MetricId.SLEEP_NEED, MetricUnit.HOURS, 1, 0.0, 9.5, "M-NEED.md"),
            MetricDefinition(MetricId.SLEEP_DEBT, MetricUnit.MINUTES, 0, null, 0.0, "M-DEBT.md"),
            MetricDefinition(MetricId.SLEEP_SCORE, MetricUnit.SCORE, 0, 0.0, 100.0, "M-SSCORE.md"),
            MetricDefinition(MetricId.HOURS_VS_NEEDED, MetricUnit.PERCENT, 0, 0.0, null, "M-HVN.md"),
            MetricDefinition(MetricId.TRAINING_LOAD_CTL, MetricUnit.LOAD, 1, 0.0, null, "M-LOAD-CTL-ATL.md"),
            MetricDefinition(MetricId.TRAINING_LOAD_ATL, MetricUnit.LOAD, 1, 0.0, null, "M-LOAD-CTL-ATL.md"),
            MetricDefinition(MetricId.TRAINING_FORM, MetricUnit.LOAD, 1, null, null, "M-LOAD-CTL-ATL.md"),
            MetricDefinition(MetricId.ACWR, MetricUnit.RATIO, 2, 0.0, null, "M-READY.md"),
            MetricDefinition(MetricId.READINESS, MetricUnit.CATEGORY, 0, null, null, "M-READY.md"),
            MetricDefinition(MetricId.ACTIVE_KCAL, MetricUnit.KCAL, 0, 0.0, null, "M-KCAL.md"),
            MetricDefinition(MetricId.MAX_HR, MetricUnit.BPM, 0, null, null, "M-MAXHR.md"),
            MetricDefinition(MetricId.VO2_MAX, MetricUnit.ML_KG_MIN, 1, null, null, "M-VO2.md"),
            MetricDefinition(MetricId.FITNESS_AGE, MetricUnit.YEARS, 0, 20.0, 80.0, "M-FITAGE.md"),
            MetricDefinition(MetricId.VITALITY, MetricUnit.SCORE, 0, null, null, "M-BODYAGE.md"),
            MetricDefinition(MetricId.BODY_AGE, MetricUnit.YEARS, 0, null, null, "M-BODYAGE.md"),
        ).associateBy { it.id }

        /** Where each value comes from in v10: the table or function the registry reads (the inspector's source
         *  column, and the lineage in 07). */
        val SOURCES: Map<MetricId, String> = mapOf(
            MetricId.CHARGE to "dailyMetric.recovery (merged; carry #543)",
            MetricId.EFFORT to "dailyMetric.strain",
            MetricId.REST to "metricSeries sleep_performance (resolved, imported wins)",
            MetricId.HRV to "dailyMetric.avgHrv",
            MetricId.RESTING_HR to "dailyMetric.restingHr",
            MetricId.RESP_RATE to "dailyMetric.respRateBpm",
            MetricId.SPO2 to "dailyMetric.spo2Pct",
            MetricId.SKIN_TEMP_DEV to "dailyMetric.skinTempDevC",
            MetricId.SLEEP_DURATION to "dailyMetric.totalSleepMin",
            MetricId.STEPS to "dailyMetric.steps, else appleDaily steps, else metricSeries steps_est",
            MetricId.STRESS to "StressModel over dailyMetric + metricSeries stress",
            MetricId.SLEEP_NEED_REST to "IntelligenceEngine.routineRestNeedHours (sleep sessions, 21 d)",
            MetricId.SLEEP_NEED_STORED to "RestScorer.defaultSleepNeedHours",
            MetricId.SLEEP_NEED_SLEEP_TAB to "metricSeries sleep_need_min, else sleepTabNeedMin(dailyMetric)",
            MetricId.SLEEP_NEED_DEBT to "sleepDebtNeedMin(dailyMetric)",
            MetricId.SLEEP_NEED to "oneSleepNeedHours(dailyMetric.totalSleepMin, 21 d, age) (R9a V2-1)",
            MetricId.SLEEP_DEBT to "SleepDebt.debtSeries (dailyMetric + naps; metricSeries sleep_debt_min wins)",
            MetricId.SLEEP_SCORE to "SleepScore (dailyMetric asleep and efficiency; sleepSession bed and wake; Sleep needed) (RS-3c)",
            MetricId.HOURS_VS_NEEDED to "hoursVsNeededPct(dailyMetric.totalSleepMin, Sleep tab need)",
            MetricId.TRAINING_LOAD_CTL to "TrainingLoadEngine over dailyMetric.strain",
            MetricId.TRAINING_LOAD_ATL to "TrainingLoadEngine over dailyMetric.strain",
            MetricId.TRAINING_FORM to "TrainingLoadEngine over dailyMetric.strain",
            MetricId.ACWR to "ReadinessEngine over dailyMetric",
            MetricId.READINESS to "ReadinessEngine over dailyMetric",
            MetricId.ACTIVE_KCAL to "appleDaily activeKcal, else metricSeries active_kcal",
            MetricId.MAX_HR to "ProfileStore override, else HrZones Tanaka(age)",
            MetricId.VO2_MAX to "metricSeries vo2max_est (computed, weekly)",
            MetricId.FITNESS_AGE to "metricSeries fitness_age (computed, weekly)",
            MetricId.VITALITY to "metricSeries vitality (computed, weekly)",
            MetricId.BODY_AGE to "metricSeries body_age (computed, weekly)",
        )

        /** The stored series key of each weekly engine metric. */
        val WEEKLY_KEYS: Map<MetricId, String> = mapOf(
            MetricId.VO2_MAX to "vo2max_est",
            MetricId.FITNESS_AGE to "fitness_age",
            MetricId.VITALITY to "vitality",
            MetricId.BODY_AGE to "body_age",
        )
    }
}
