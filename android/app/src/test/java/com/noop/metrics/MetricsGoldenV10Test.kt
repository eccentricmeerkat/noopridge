package com.noop.metrics

import com.noop.analytics.Baselines
import com.noop.analytics.BaselineState
import com.noop.analytics.BaselineStatus
import com.noop.analytics.Calories
import com.noop.analytics.FitnessAgeEngine
import com.noop.analytics.HeartRateRecovery
import com.noop.analytics.HrZones
import com.noop.analytics.HrvAnalyzer
import com.noop.analytics.ReadinessEngine
import com.noop.analytics.RecoveryScorer
import com.noop.analytics.RestScorer
import com.noop.analytics.SleepDebt
import com.noop.analytics.SleepStager
import com.noop.analytics.StepsEstimateEngine
import com.noop.analytics.StrainScorer
import com.noop.analytics.StressIndex
import com.noop.analytics.TrainingLoadEngine
import com.noop.analytics.VitalBands
import com.noop.analytics.VitalityEngine
import com.noop.data.DailyMetric
import com.noop.data.HrSample
import com.noop.data.RrInterval
import com.noop.ui.EffortScale
import com.noop.ui.Palette
import com.noop.ui.StressModel
import com.noop.ui.UnitFormatter
import com.noop.ui.synthesisSliderPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Golden fixtures for the v10 metric formulas (Ridge Sprint 0 ticket S0-02).
 *
 * Every row here runs the CURRENT production function over a fixed input and compares the result to a
 * checked-in CSV under `src/test/resources/metrics_golden_v10/`. The point is a tripwire, not a spec:
 * any change to a number users see — intended or not — fails this test until the CSV is regenerated
 * and the change is reviewed and logged (docs/ridge-build/DECISIONS_LOG.md, release notes).
 *
 * The formulas themselves are documented in `docs/ridge-build/metrics/<id>.md`. Nothing in this file
 * reimplements a formula: each value comes from calling the shipped code.
 *
 * Regenerate (only for a reviewed, deliberate change):
 *   NOOP_GOLDEN_RECORD=1 ./gradlew testFullDebugUnitTest --tests com.noop.metrics.MetricsGoldenV10Test
 */
class MetricsGoldenV10Test {

    // ---- Harness ---------------------------------------------------------------------------------

    private val record = System.getenv("NOOP_GOLDEN_RECORD") == "1"
    private val dir = File("src/test/resources/metrics_golden_v10")

    private fun f(v: Double?): String = v?.let { String.format(Locale.ROOT, "%.6f", it) } ?: "null"
    private fun i(v: Int?): String = v?.toString() ?: "null"

    /** Render rows as CSV and compare with (or, in record mode, write) the golden file for [id]. */
    private fun golden(id: String, header: String, rows: List<String>) {
        val text = (listOf(header) + rows).joinToString("\n") + "\n"
        val file = File(dir, "$id.csv")
        if (record) {
            dir.mkdirs()
            file.writeText(text)
            return
        }
        check(file.exists()) { "Missing golden file ${file.path}; run with NOOP_GOLDEN_RECORD=1 to create it" }
        assertEquals("Golden output changed for $id (see docs/ridge-build/metrics/$id.md)", file.readText(), text)
    }

    // ---- Fixed input builders (deterministic, no clock) --------------------------------------------

    private val t0 = 1_767_225_600L   // 2026-01-01T00:00:00Z, an arbitrary fixed origin

    /** [minutes] of 1 Hz HR at [bpm], starting at [start]. */
    private fun hr(start: Long, minutes: Int, bpm: Int): List<HrSample> =
        (0 until minutes * 60).map { HrSample(deviceId = "golden", ts = start + it, bpm = bpm) }

    private fun state(mean: Double, spread: Double, n: Int): BaselineState = BaselineState(
        baseline = mean, spread = spread, nValid = n, nightsSinceUpdate = 0,
        status = when {
            n < Baselines.minNightsSeed -> BaselineStatus.CALIBRATING
            n < Baselines.minNightsTrust -> BaselineStatus.PROVISIONAL
            else -> BaselineStatus.TRUSTED
        },
    )

    private fun day(i: Int): String = java.time.LocalDate.of(2026, 1, 1).plusDays(i.toLong()).toString()

    private fun daily(
        i: Int, rhr: Int? = null, hrv: Double? = null, resp: Double? = null, strain: Double? = null,
        sleepMin: Double? = null, eff: Double? = null, deep: Double? = null, rem: Double? = null,
        recovery: Double? = null,
    ) = DailyMetric(
        deviceId = "golden", day = day(i), totalSleepMin = sleepMin, efficiency = eff, deepMin = deep,
        remMin = rem, restingHr = rhr, avgHrv = hrv, recovery = recovery, strain = strain, respRateBpm = resp,
    )

    // ---- M-CHG Charge ------------------------------------------------------------------------------

    @Test
    fun chargeCharge() {
        val hrvB = state(60.0, 8.0, 20)
        val rhrB = state(55.0, 2.0, 20)
        val respB = state(14.5, 0.6, 20)
        data class C(val name: String, val hrv: Double, val rhr: Double, val resp: Double?, val sleep: Double?,
                     val skin: Double?, val hrvBase: BaselineState)
        val cases = listOf(
            C("at_baseline", 60.0, 55.0, 14.5, 0.85, 0.0, hrvB),
            C("hrv_high_rhr_low", 75.0, 51.0, 14.0, 0.92, 0.1, hrvB),
            C("hrv_low_rhr_high", 45.0, 60.0, 15.5, 0.70, 0.6, hrvB),
            C("no_resp_no_skin", 60.0, 55.0, null, 0.85, null, hrvB),
            C("calibrating_3_nights", 60.0, 55.0, 14.5, 0.85, 0.0, state(60.0, 8.0, 3)),
        )
        golden("M-CHG", "case,hrv,rhr,resp,sleepPerf,skinTempDev,hrvBaselineNights,charge,band", cases.map { c ->
            val score = RecoveryScorer.recovery(
                hrv = c.hrv, rhr = c.rhr, resp = c.resp, hrvBaseline = c.hrvBase, rhrBaseline = rhrB,
                respBaseline = respB, sleepPerf = c.sleep, skinTempDev = c.skin,
            )
            "${c.name},${f(c.hrv)},${f(c.rhr)},${f(c.resp)},${f(c.sleep)},${f(c.skin)},${c.hrvBase.nValid}," +
                "${f(score)},${score?.let { RecoveryScorer.band(it) } ?: "null"}"
        })
    }

    // ---- M-SYN Synthesis stage ---------------------------------------------------------------------

    @Test
    fun synthesisStage() {
        val scores = listOf(0.0, 24.99, 25.0, 49.99, 50.0, 69.99, 70.0, 87.99, 88.0, 100.0)
        golden("M-SYN", "charge,sliderPosition,stageIndex,paletteRecoveryState,scorerBand", scores.map { s ->
            val pos = synthesisSliderPosition(s)
            // The slider gives each of the five stages an equal fifth; the stage index is the fifth it falls in.
            val stage = minOf(4, (pos * 5).toInt())
            "${f(s)},${f(pos.toDouble())},$stage,${Palette.recoveryState(s)},${RecoveryScorer.band(s)}"
        })
    }

    // ---- M-EFF / M-EFF-W Effort --------------------------------------------------------------------

    @Test
    fun effort() {
        data class C(val name: String, val samples: List<HrSample>, val maxHr: Double?, val rhr: Double,
                     val method: StrainScorer.Method, val sex: String)
        val easy = hr(t0, 60, 110)
        val hard = hr(t0, 30, 150) + hr(t0 + 1800, 30, 175)
        val sparse = (0 until 40).map { HrSample("golden", t0 + it * 30L, 140) }
        val cases = listOf(
            C("easy_60min_edwards", easy, 188.0, 55.0, StrainScorer.Method.EDWARDS, "male"),
            C("hard_60min_edwards", hard, 188.0, 55.0, StrainScorer.Method.EDWARDS, "male"),
            C("hard_60min_banister_f", hard, 188.0, 55.0, StrainScorer.Method.BANISTER, "female"),
            C("sparse_30s_cadence", sparse, 188.0, 55.0, StrainScorer.Method.EDWARDS, "male"),
            C("too_few_samples", hr(t0, 5, 160), 188.0, 55.0, StrainScorer.Method.EDWARDS, "male"),
            C("no_maxhr_uses_190", hard, null, 55.0, StrainScorer.Method.EDWARDS, "male"),
        )
        val rows = cases.map { c ->
            val e = StrainScorer.strain(c.samples, c.maxHr, c.rhr, c.method, c.sex)
            "${c.name},${c.samples.size},${f(c.maxHr)},${f(c.rhr)},${c.method},${c.sex},${f(e)}," +
                "${e?.let { UnitFormatter.effortDisplay(it, EffortScale.HUNDRED) } ?: "null"}," +
                "${e?.let { UnitFormatter.effortDisplay(it, EffortScale.WHOOP) } ?: "null"}"
        }
        val floor = listOf(
            "effectiveEffort(live=null;stored=40)" to StrainScorer.effectiveEffort(null, 40.0),
            "effectiveEffort(live=12;stored=40)" to StrainScorer.effectiveEffort(12.0, 40.0),
            "effectiveEffort(live=55;stored=40)" to StrainScorer.effectiveEffort(55.0, 40.0),
        ).map { (n, v) -> "$n,,,,,,${f(v)},," }
        golden("M-EFF", "case,samples,maxHr,restingHr,method,sex,effort,display0to100,display0to21", rows + floor)
    }

    // ---- M-RST Rest --------------------------------------------------------------------------------

    @Test
    fun rest() {
        data class C(val name: String, val asleepMin: Double, val eff: Double, val deepMin: Double,
                     val remMin: Double, val need: Double?, val consistency: Double?)
        val cases = listOf(
            C("8h_good", 480.0, 0.92, 80.0, 110.0, null, null),
            C("6h_short", 360.0, 0.85, 45.0, 70.0, null, null),
            C("7h_low_deep", 420.0, 0.95, 10.0, 120.0, null, null),
            C("7h_personal_need_8_5_consistency_0_9", 420.0, 0.9, 60.0, 100.0, 8.5, 0.9),
        )
        val rows = cases.map { c ->
            val r = RestScorer.rest(c.asleepMin * 60, c.eff, c.deepMin * 60, c.remMin * 60, c.need, c.consistency)
            "${c.name},${f(c.asleepMin)},${f(c.eff)},${f(c.deepMin)},${f(c.remMin)},${f(c.need)},${f(c.consistency)},${f(r)}"
        }
        // The persisted path (`sleep_performance`): restFromDaily, which passes no need and no consistency.
        val persisted = cases.take(3).map { c ->
            val d = daily(0, sleepMin = c.asleepMin, eff = c.eff, deep = c.deepMin, rem = c.remMin)
            "restFromDaily_${c.name},${f(c.asleepMin)},${f(c.eff)},${f(c.deepMin)},${f(c.remMin)},,,${f(RestScorer.restFromDaily(d))}"
        }
        golden("M-RST", "case,asleepMin,efficiency,deepMin,remMin,needHours,consistency,rest", rows + persisted)
    }

    // ---- M-NEED Sleep need, M-DEBT Sleep debt ------------------------------------------------------

    @Test
    fun sleepNeed() {
        val few = listOf(7.0, 7.5, 6.5)
        val month = (0 until 21).map { 6.0 + (it % 5) * 0.5 }
        val long = (0 until 14).map { 9.0 + (it % 3) * 0.5 }
        golden("M-NEED", "case,nights,age,needHours", listOf(
            "under_7_nights,${few.size},35,${f(RestScorer.personalizedNeedHours(few, 35))}",
            "21_nights_adult,${month.size},35,${f(RestScorer.personalizedNeedHours(month, 35))}",
            "21_nights_teen,${month.size},16,${f(RestScorer.personalizedNeedHours(month, 16))}",
            "long_sleeper_capped,${long.size},35,${f(RestScorer.personalizedNeedHours(long, 35))}",
            "age_unknown,${month.size},null,${f(RestScorer.personalizedNeedHours(month, null))}",
        ))
    }

    @Test
    fun sleepDebt() {
        fun series(vararg mins: Double?) = mins.mapIndexed { idx, m -> day(idx) to m }
        data class C(val name: String, val s: List<Pair<String, Double?>>, val need: Double)
        val cases = listOf(
            C("on_target", series(480.0, 480.0, 480.0), 8.0),
            C("three_short_nights", series(420.0, 400.0, 380.0), 8.0),
            C("gap_skipped", series(420.0, null, 400.0, 0.0, 380.0), 8.0),
            C("recovery_night_clears", series(360.0, 360.0, 600.0), 8.0),
            C("16_nights_window_14", series(*Array(16) { 400.0 }), 8.0),
        )
        golden("M-DEBT", "case,nights,needHours,balanceMin,countedNights", cases.map { c ->
            val l = SleepDebt.ledger(c.s, c.need)
            "${c.name},${c.s.size},${f(c.need)},${f(l.balanceMin)},${l.nightCount}"
        })
    }

    // M-HVN had no fixture at S0-02 (inline in the Sleep model builder); R1 J-01 extracted it.
    @Test
    fun hoursVsNeeded() {
        val short = (0 until 10).map { daily(it, sleepMin = 360.0 + it * 6.0) }
        val long = (0 until 10).map { daily(it, sleepMin = 480.0 + it * 3.0) }
        val withGaps = listOf(daily(0, sleepMin = 420.0), daily(1), daily(2, sleepMin = 0.0), daily(3, sleepMin = 470.0))
        golden("M-HVN", "case,sleepTabNeedMin,debtNeedMin,lastNightMin,hoursVsNeededPct", listOf(
            Triple("short_sleeper_floor_450", short, 414.0),
            Triple("long_sleeper_mean", long, 507.0),
            Triple("gaps_ignored", withGaps, 470.0),
            Triple("no_nights", emptyList(), 0.0),
        ).map { (name, days, last) ->
            val need = com.noop.ui.sleepTabNeedMin(days)
            "$name,${f(need)},${f(com.noop.ui.sleepDebtNeedMin(days))},${f(last)},${f(com.noop.ui.hoursVsNeededPct(last, need))}"
        })
    }

    // ---- M-HRV, M-RHR, M-RESP overnight signals ----------------------------------------------------

    @Test
    fun overnightSignals() {
        val night = hr(t0, 30, 62) + hr(t0 + 1800, 30, 52) + hr(t0 + 3600, 30, 58)
        // A trailing low dip in its own 5-min bin: 20 samples clears the >=5-sample gate and wins; 3 does not.
        val dip = night + (0 until 20).map { HrSample("golden", t0 + 5400 + it, 35) }
        val thinDip = night + (0 until 3).map { HrSample("golden", t0 + 5400 + it, 35) }
        val rrSteady = (0 until 400).map { 1000.0 + if (it % 2 == 0) 25.0 else -25.0 }
        val rrVaried = (0 until 400).map { 950.0 + (it % 7) * 12.0 }
        golden("M-HRV-RHR", "case,value", listOf(
            "rmssd_steady_alternating,${f(HrvAnalyzer.rmssdRaw(HrvAnalyzer.cleanRR(rrSteady)))}",
            "rmssd_varied,${f(HrvAnalyzer.rmssdRaw(HrvAnalyzer.cleanRR(rrVaried)))}",
            "sdnn_varied,${f(HrvAnalyzer.sdnnRaw(HrvAnalyzer.cleanRR(rrVaried)))}",
            "resting_hr_lowest_5min_bin,${i(SleepStager.sessionRestingHR(t0, t0 + 5399, night))}",
            "resting_hr_20_sample_dip_wins_bin,${i(SleepStager.sessionRestingHR(t0, t0 + 5420, dip))}",
            "resting_hr_3_sample_dip_gated_out,${i(SleepStager.sessionRestingHR(t0, t0 + 5420, thinDip))}",
        ))
        // Respiratory rate from R-R (RSA). A 15 breaths/min sinusoid on a 1000 ms baseline.
        val rrRsa = ArrayList<RrInterval>()
        var ts = t0 * 1000
        for (k in 0 until 1800) {
            val rr = (1000 + 40 * kotlin.math.sin(2 * Math.PI * (ts / 1000.0) * (15.0 / 60.0))).toInt()
            rrRsa.add(RrInterval(deviceId = "golden", ts = ts / 1000, rrMs = rr, seq = k))
            ts += rr
        }
        golden("M-RESP", "case,breathsPerMin", listOf(
            "rsa_15bpm_sinusoid,${f(SleepStager.respRateFromRR(rrRsa, t0, t0 + 1800).takeIf { it.isFinite() })}",
            "too_short,${f(SleepStager.respRateFromRR(rrRsa.take(10), t0, t0 + 10).takeIf { it.isFinite() })}",
            "empty,${f(SleepStager.respRateFromRR(emptyList(), t0, t0 + 1800).takeIf { it.isFinite() })}",
        ))
    }

    // ---- M-SKIN, M-NORM personal band, M-SPO2 ------------------------------------------------------

    @Test
    fun skinTempAndNormalBand() {
        val skin = state(33.5, 0.25, 20)
        golden("M-SKIN", "case,nightlyC,deltaC,z", listOf(33.5, 33.9, 32.8).mapIndexed { idx, v ->
            val d = Baselines.deviation(v, skin)
            "case$idx,${f(v)},${f(d.delta)},${f(d.z)}"
        })
        val steady = List(20) { 55.0 + (it % 3) - 1.0 }
        val short = List(10) { 55.0 }
        val cfg = Baselines.restingHRCfg
        val pop = 40.0..100.0
        golden("M-NORM", "case,value,historyNights,band,basis", listOf(
            Triple("personal_in_range", 56.0, steady),
            Triple("personal_out_of_range", 63.0, steady),
            Triple("population_fallback_under_14_nights", 63.0, short),
            Triple("implausible_value", 25.0, steady),
        ).map { (n, v, h) ->
            val r = VitalBands.band(v, h, pop, cfg)
            "$n,${f(v)},${h.size},${r.band},${r.basis}"
        } + listOf(
            "spo2_population_only_95,95.0,0,${VitalBands.band(95.0, emptyList(), 95.0..100.0, null).band},POPULATION",
            "spo2_population_only_92,92.0,0,${VitalBands.band(92.0, emptyList(), 95.0..100.0, null).band},POPULATION",
        ))
    }

    // ---- M-STR Stress ------------------------------------------------------------------------------

    @Test
    fun stress() {
        val base = (0 until 30).map { daily(it, rhr = 55 + (it % 3) - 1, hrv = 60.0 + (it % 5) - 2.0) }
        fun withToday(rhr: Int, hrv: Double) = base + daily(30, rhr = rhr, hrv = hrv)
        val rows = listOf(
            "at_baseline" to StressModel.build(withToday(55, 60.0), emptyMap()),
            "rhr_up_hrv_down" to StressModel.build(withToday(60, 50.0), emptyMap()),
            "rhr_down_hrv_up" to StressModel.build(withToday(51, 70.0), emptyMap()),
            "stored_value_wins" to StressModel.build(withToday(60, 50.0), mapOf(day(30) to 0.4)),
            "no_signal" to StressModel.build(listOf(daily(0)), emptyMap()),
        ).map { (n, m) -> "$n,${f(m?.score)},${m?.band ?: "null"},${m?.usingStored ?: "null"}" }
        val si = listOf(
            "baevsky_si_steady" to StressIndex.stressIndexRaw((0 until 200).map { 800.0 + (it % 4) * 10.0 }),
            "baevsky_si_varied" to StressIndex.stressIndexRaw((0 until 200).map { 800.0 + (it % 9) * 25.0 }),
        ).map { (n, v) -> "$n,${f(v)},," }
        golden("M-STR", "case,score0to3,band,usingStored", rows + si)
    }

    // ---- M-STEP steps estimate, M-KCAL calories ----------------------------------------------------

    @Test
    fun stepsEstimate() {
        val pts = listOf(
            StepsEstimateEngine.CalibrationPoint(motion = 1000.0, steps = 8000.0),
            StepsEstimateEngine.CalibrationPoint(motion = 1200.0, steps = 9000.0),
            StepsEstimateEngine.CalibrationPoint(motion = 800.0, steps = 6800.0),
            StepsEstimateEngine.CalibrationPoint(motion = 1500.0, steps = 11000.0),
        )
        val fit = StepsEstimateEngine.calibrate(pts)
        val tooFew = StepsEstimateEngine.calibrate(pts.take(2))
        val manual = StepsEstimateEngine.calibrate(pts, manualOverride = 9.5)
        golden("M-STEP", "case,coefficient,sampleDays,confidence,manual,estimateFor1100", listOf(
            "auto_fit_4_days,${f(fit?.coefficient)},${fit?.sampleDays},${f(fit?.confidence)},${fit?.manual},${i(fit?.let { StepsEstimateEngine.estimate(1100.0, it) })}",
            "too_few_days,${f(tooFew?.coefficient)},${tooFew?.sampleDays},${f(tooFew?.confidence)},${tooFew?.manual},null",
            "manual_override,${f(manual?.coefficient)},${manual?.sampleDays},${f(manual?.confidence)},${manual?.manual},${i(manual?.let { StepsEstimateEngine.estimate(1100.0, it) })}",
        ))
    }

    @Test
    fun calories() {
        val m = Calories.resolveCoeffs("male")
        val fe = Calories.resolveCoeffs("female")
        golden("M-KCAL", "case,value", listOf(
            "resting_kcal_per_day_male_80kg_180cm_40y,${f(Calories.restingKcalPerS(m, 80.0, 180.0, 40.0) * 86_400)}",
            "resting_kcal_per_day_female_62kg_165cm_35y,${f(Calories.restingKcalPerS(fe, 62.0, 165.0, 35.0) * 86_400)}",
            "active_kcal_per_min_male_hr150_base,${f(Calories.activeKcalPerS(m, 150.0, 185.0, 80.0, 40.0) * 60)}",
            "active_kcal_per_min_male_hr150_fit_vo2_45,${f(Calories.activeKcalPerS(m, 150.0, 185.0, 80.0, 40.0, 45.0) * 60)}",
            "active_kcal_per_min_female_hr140_base,${f(Calories.activeKcalPerS(fe, 140.0, 180.0, 62.0, 35.0) * 60)}",
        ))
    }

    // ---- M-ZONE, M-MAXHR, M-HRR --------------------------------------------------------------------

    @Test
    fun zonesAndMaxHr() {
        golden("M-MAXHR", "case,maxHr,source", listOf(
            "tanaka_age_40,${f(StrainScorer.tanakaHRmax(40.0))},tanaka",
            "tanaka_age_25,${f(StrainScorer.tanakaHRmax(25.0))},tanaka",
            "no_age_default_220_minus_30,${StrainScorer.defaultMaxHR()},default",
            "observed_beats_tanaka,${f(StrainScorer.estimateHRmax(List(600) { 150.0 + (it % 50) }, 40.0).first)},${StrainScorer.estimateHRmax(List(600) { 150.0 + (it % 50) }, 40.0).second}",
        ))
        val rows = listOf(
            "age_40" to HrZones.zones(age = 40.0),
            "override_190" to HrZones.zones(age = 40.0, maxHROverride = 190.0),
            "age_25" to HrZones.zones(age = 25.0),
        ).map { (n, z) -> "$n,${f(z.maxHR)},${z.source}," + z.zones.joinToString(";") { "${it.number}:${f(it.lower)}-${f(it.upper)}" } }
        golden("M-ZONE", "case,maxHr,source,zones", rows)
    }

    @Test
    fun heartRateRecovery() {
        val end = t0 + 3600
        val hard = hr(t0, 60, 168)
        val recovery = (1..330).map { s -> HrSample("golden", end + s, (168 - s / 6).coerceAtLeast(110)) }
        val easy = hr(t0, 60, 120) + recovery
        golden("M-HRR", "case,endHr,after1Min,after2Min,after5Min", listOf(
            "hard_session" to HeartRateRecovery.calculate(hard + recovery, t0, end, 185.0),
            "easy_session_ineligible" to HeartRateRecovery.calculate(easy, t0, end, 185.0),
            "no_post_data" to HeartRateRecovery.calculate(hard, t0, end, 185.0),
        ).map { (n, r) -> "$n,${i(r?.endHr)},${i(r?.after1Minute)},${i(r?.after2Minutes)},${i(r?.after5Minutes)}" })
    }

    // ---- M-VO2, M-FITAGE, M-BODYAGE ----------------------------------------------------------------

    @Test
    fun fitnessAgeAndVo2() {
        val pai = FitnessAgeEngine.physicalActivityIndexFromStrain(4, 60.0)
        golden("M-VO2", "case,vo2max", listOf(
            "nes_male_40_waist_90_rhr_55_pai_ref,${f(FitnessAgeEngine.estimateVO2max(40.0, "male", 90.0, 55.0, pai))}",
            "nes_female_35_waist_75_rhr_60,${f(FitnessAgeEngine.estimateVO2max(35.0, "female", 75.0, 60.0, pai))}",
            "uth_hrmax_180_rhr_55,${f(Calories.vo2maxFor(180.0, 55.0))}",
            "uth_no_rhr,${f(Calories.vo2maxFor(180.0, null))}",
        ))
        golden("M-FITAGE", "case,paIndex,fitnessAge,deltaYears,lowerConfidence", listOf(
            Triple("reference_peer_maps_to_own_age", 65.0, pai),
            Triple("low_rhr_active", 50.0, FitnessAgeEngine.physicalActivityIndexFromStrain(6, 80.0)),
            Triple("high_rhr_inactive", 78.0, FitnessAgeEngine.physicalActivityIndexFromStrain(1, 35.0)),
        ).map { (n, rhr, p) ->
            val r = FitnessAgeEngine.compute(40.0, "male", rhr, p)
            "$n,${f(p)},${f(r?.fitnessAge)},${f(r?.deltaYears)},${r?.lowerConfidence}"
        })
    }

    @Test
    fun vitalityBodyAge() {
        val rows = listOf(
            "average_inputs" to VitalityEngine.Inputs(40.0, restingHR = 65.0, sleepHours = 7.5,
                sleepConsistency = 0.75, rmssd = VitalityEngine.rmssdNorm(40.0), rmssdNorm = VitalityEngine.rmssdNorm(40.0), steps = 7000.0),
            "healthy_inputs" to VitalityEngine.Inputs(40.0, restingHR = 52.0, sleepHours = 7.5,
                sleepConsistency = 0.9, rmssd = 50.0, rmssdNorm = VitalityEngine.rmssdNorm(40.0), steps = 11000.0),
            "two_inputs_not_enough" to VitalityEngine.Inputs(40.0, restingHR = 52.0, steps = 9000.0),
        ).map { (n, inp) ->
            val r = VitalityEngine.compute(inp)
            "$n,${f(r?.vitality)},${f(r?.bodyAge)},${r?.factorsUsed ?: "null"}"
        }
        golden("M-BODYAGE", "case,vitality,bodyAge,factorsUsed", rows)
    }

    // ---- Existing v10 training load (CTL/ATL) and Readiness ----------------------------------------

    @Test
    fun trainingLoad() {
        val steady = List(42) { 50.0 }
        val ramp = List(28) { 40.0 } + List(14) { 70.0 }
        val short = List(10) { 50.0 }
        golden("M-LOAD-CTL-ATL", "case,days,state,ctl,atl,tsb", listOf(
            "steady_42" to TrainingLoadEngine.evaluateDense(steady),
            "ramp_last_14" to TrainingLoadEngine.evaluateDense(ramp),
            "only_10_days" to TrainingLoadEngine.evaluateDense(short),
        ).map { (n, r) -> "$n,${r.contiguousDays},${r.state},${f(r.ctl)},${f(r.atl)},${f(r.tsb)}" })
    }

    @Test
    fun readiness() {
        val history = (0 until 30).map { daily(it, rhr = 55 + (it % 3) - 1, hrv = 60.0 + (it % 5) - 2.0, resp = 14.5, strain = 45.0) }
        val cases = listOf(
            "balanced" to history + daily(30, rhr = 55, hrv = 60.0, resp = 14.5, strain = 45.0),
            "hrv_down_rhr_up" to history + daily(30, rhr = 62, hrv = 45.0, resp = 14.5, strain = 45.0),
            "primed" to history + daily(30, rhr = 51, hrv = 72.0, resp = 14.5, strain = 45.0),
            "load_spike" to history.dropLast(7) + (23 until 31).map { daily(it, rhr = 55, hrv = 60.0, resp = 14.5, strain = 90.0) },
            "insufficient" to listOf(daily(0, rhr = 55, hrv = 60.0)),
        )
        golden("M-READY", "case,level,acwr,monotony", cases.map { (n, days) ->
            val r = ReadinessEngine.evaluate(days, today = days.last().day)
            "$n,${r.level},${f(r.acwr)},${f(r.monotony)}"
        })
    }
}
