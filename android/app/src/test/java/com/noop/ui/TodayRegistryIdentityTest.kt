package com.noop.ui

import com.noop.analytics.StrainScorer
import com.noop.data.DailyMetric
import com.noop.metrics.DayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * J-01 acceptance (R1b): Today's orb, tile, Synthesis and breakdown read the same registry value per metric.
 *
 * 1. Values: for every case, the registry-derived [TodayUiState] fields equal the pre-R1b inline derivation, kept
 *    here verbatim as the oracle, so moving Today onto the registry changed no number.
 * 2. One source: the four surfaces take Charge (`displayMetric` / `recoveryCalibration` / `lastScoredRecoveryDay`),
 *    Effort (`effortForDay`) and Rest (`restScoreForDay`) from `ui`, and `TodayScreen` recomputes none of them.
 */
class TodayRegistryIdentityTest {

    private val today = LocalDate.of(2026, 11, 11)
    private fun key(back: Int) = today.minusDays(back.toLong()).toString()

    private fun night(back: Int, recovery: Double? = 60.0, hrv: Double? = 70.0, strain: Double? = 40.0) = DailyMetric(
        deviceId = "my-whoop", day = key(back), totalSleepMin = 420.0, restingHr = 55, avgHrv = hrv,
        recovery = recovery, strain = strain, respRateBpm = 14.5, spo2Pct = 97.0, skinTempDevC = 0.1,
    )
    private fun history(lastBack: Int = 1) = (lastBack + 19 downTo lastBack).map { night(it, recovery = 40.0 + it % 30) }
    private val rest = (1..20).associate { key(it) to 70.0 + it }

    private fun inputs(
        days: List<DailyMetric>, todayRow: DailyMetric?, offset: Int = 0, live: Double? = null,
        localCalendar: LocalDate = today, restByDay: Map<String, Double> = rest,
    ) = TodayInputs(days, todayRow, today, localCalendar, offset, live, hrvBaselineEpoch = 0.0, restByDay = restByDay)

    private val cases: Map<String, TodayInputs> by lazy {
        val scoredToday = night(0, recovery = 72.0, strain = 35.0)
        val unscoredToday = night(0, recovery = null, hrv = null, strain = 12.0)
        mapOf(
            "brand_new" to inputs(emptyList(), null, restByDay = emptyMap()),
            "calibrating_2_of_4" to listOf(night(2, null), night(1, null)).let { inputs(it, null) },
            "scored_today" to (history() + scoredToday).let { inputs(it, scoredToday, restByDay = rest + (key(0) to 88.0)) },
            "carried_last_night" to (history() + unscoredToday).let { inputs(it, unscoredToday) },
            "stale_carry" to (history(lastBack = 5) + unscoredToday).let {
                inputs(it, unscoredToday, restByDay = rest.filterKeys { k -> k <= key(5) })
            },
            "past_day" to (history() + scoredToday).let { inputs(it, scoredToday, offset = 3) },
            "live_effort_above_stored" to (history() + scoredToday).let { inputs(it, scoredToday, live = 61.5) },
            "live_effort_below_stored" to (history() + scoredToday).let { inputs(it, scoredToday, live = 9.0) },
            "pre_0400_local_row" to night(0, recovery = 55.0).copy(day = today.plusDays(1).toString()).let { localRow ->
                inputs(history() + localRow, localRow, localCalendar = today.plusDays(1))
            },
            "rest_future_dated_night" to (history() + unscoredToday).let {
                inputs(it, unscoredToday, restByDay = rest + (today.plusDays(2).toString() to 66.0))
            },
            "resolver_row_not_in_days" to inputs(history(), night(0, recovery = 64.0, strain = 22.0)),
            "past_day_equal_to_resolver_row_day" to night(1, recovery = 58.0).let { row ->
                inputs(history(lastBack = 2) + row, row, offset = 1)
            },
            "past_day_before_any_rest" to (history() + scoredToday).let { inputs(it, scoredToday, offset = 19) },
        )
    }

    /** The pre-R1b derivation (TodayUiStateBuilder + TodayScreen's restScoreForDay, `52c5f538`), verbatim. */
    private data class Old(
        val displayRecovery: Double?, val effortForDay: Double?, val recoveryCalibration: Int?,
        val lastScoredRecoveryDay: DailyMetric?, val restScore: Double?, val shownCharge: Double?,
    )

    private fun old(i: TodayInputs): Old {
        val isToday = i.selectedDayOffset == 0
        val selectedDay = i.todayDate.minusDays(i.selectedDayOffset.toLong())
        val selectedDayKey = if (isToday) i.today?.day ?: selectedDay.toString() else selectedDay.toString()
        val historicalMetric = i.days.lastOrNull { it.day == selectedDayKey }
        val displayMetric = if (isToday) i.today ?: historicalMetric else historicalMetric
        val effortForDay = StrainScorer.effectiveEffort(live = if (isToday) i.liveTodayStrain else null, stored = displayMetric?.strain)
        val recoveryCalibration: Int? =
            if (isToday) recoveryCalibrationNights(i.days, displayMetric?.recovery != null, i.hrvBaselineEpoch) else null
        val carryOverTodayKey = maxOf(i.todayDate.toString(), i.localCalendarDate.toString())
        val last = lastScoredRecoveryDay(i.days, selectedDayKey, isToday, displayMetric?.recovery != null,
            recoveryCalibration != null, carryOverTodayKey)
        // R9a V2-6 (#20, approved 2026-11-14): Rest's carry is bounded by the #547 key, as Charge's. The one change
        // this oracle carries from the pre-R1b derivation; `rest_future_dated_night` is the case it moves.
        val latest = i.restByDay.entries.filter { it.key <= carryOverTodayKey }.maxByOrNull { it.key }
        val restScore = freshRestScore(i.restByDay[selectedDayKey], latest?.key, latest?.value, isToday, selectedDayKey)
        val carried = last?.recovery?.let { LastCharge(it, "") }
        return Old(displayMetric?.recovery, effortForDay, recoveryCalibration, last, restScore,
            shownCharge(displayMetric?.recovery, recoveryCalibration != null, carried))
    }

    @Test
    fun theRegistryDerivedStateEqualsThePreR1bDerivationForEveryCase() {
        for ((name, i) in cases) {
            val o = old(i)
            val s = buildTodayUiState(i)
            assertEquals("$name effort", o.effortForDay, s.effortForDay)
            assertEquals("$name calibration", o.recoveryCalibration, s.recoveryCalibration)
            assertEquals("$name carried row", o.lastScoredRecoveryDay, s.lastScoredRecoveryDay)
            assertEquals("$name rest", o.restScore, s.restScore)
            assertEquals("$name display recovery", o.displayRecovery, s.displayMetric?.recovery)
            // The one Charge the orb and tile show (shownCharge) is the registry's Charge value.
            assertEquals("$name shown charge", o.shownCharge, s.charge.value)
        }
    }

    @Test
    fun eachMetricIsOneRegistryValueForTheSelectedDay() {
        for ((name, i) in cases) {
            val s = buildTodayUiState(i)
            val day = DayKey.of(s.selectedDayKey)
            assertEquals(name, day, s.charge.day)
            assertEquals(name, day, s.effort.day)
            assertEquals(name, day, s.rest.day)
            assertEquals(name, s.effort.value, s.effortForDay)
            assertEquals(name, s.rest.value, s.restScore)
            assertEquals(name, s.charge.progress?.done, s.recoveryCalibration)
            assertEquals(name, s.charge.carriedFrom?.toString(), s.lastScoredRecoveryDay?.day)
        }
    }

    @Test
    fun todayScreenReadsTheseFromUiAndRecomputesNone() {
        val src = listOf("src/main/java/com/noop/ui/TodayScreen.kt", "app/src/main/java/com/noop/ui/TodayScreen.kt")
            .map(::File).first { it.exists() }.readText()
        for (line in listOf(
            "val displayMetric = ui.displayMetric",
            "val restScoreForDay = ui.restScore",
            "val effortForDay = ui.effortForDay",
            "val recoveryCalibration: Int? = ui.recoveryCalibration",
            "val lastScoredRecoveryDay: DailyMetric? = ui.lastScoredRecoveryDay",
        )) assertTrue(line, line in src)
        for (call in listOf("lastScoredRecoveryDay(", "recoveryCalibrationNights(", "freshRestScore(", "effectiveEffort(")) {
            assertFalse("TodayScreen recomputes $call", Regex("""(?<![\w.])${Regex.escape(call)}""").containsMatchIn(src))
        }
        // The four surfaces are fed from those locals.
        for (surface in listOf("ScoreHeroRow(", "SynthesisHeroCard(", "MetricGrid(", "ChargeBreakdownSheet(")) {
            assertTrue(surface, surface in src)
        }
    }

    /**
     * R9b: the Key Metrics vitals tiles (HRV, Resting HR, Respiratory, Blood Oxygen) show the registry's value for
     * the selected day in every case, including a night re-analysed without its Charge (own HRV, Charge carried),
     * respiratory rate and SpO₂ found only on an older night, and the SpO₂ strap estimate.
     */
    @Test
    fun theVitalsTilesShowTheRegistrysValues() {
        val reanalysed = night(0, recovery = null, hrv = 61.0).copy(respRateBpm = null, spo2Pct = null)
        val olderVitals = history().mapIndexed { i, d -> if (i < 15) d else d.copy(respRateBpm = null, spo2Pct = null) }
        val extra = mapOf(
            "reanalysed_night_keeps_its_hrv" to inputs(history() + reanalysed, reanalysed),
            "resp_and_spo2_from_an_older_night" to inputs(olderVitals + reanalysed, reanalysed),
            "spo2_strap_estimate" to inputs(olderVitals.map { it.copy(spo2Pct = null) } + reanalysed, reanalysed)
                .copy(spo2CandidateByDay = mapOf(key(0) to 95.4)),
        )
        for ((name, i) in cases + extra) {
            val s = buildTodayUiState(i)
            val tile = vitalTileValues(s.displayMetric, s.lastScoredRecoveryDay, s.lastRespDay, s.lastSpo2Day, i.spo2CandidateByDay)
            val v = s.vitals
            assertEquals("$name hrv", tile.hrv, v.getValue(com.noop.metrics.MetricId.HRV).value)
            assertEquals("$name rhr", tile.restingHr?.toDouble(), v.getValue(com.noop.metrics.MetricId.RESTING_HR).value)
            assertEquals("$name resp", tile.resp, v.getValue(com.noop.metrics.MetricId.RESP_RATE).value)
            assertEquals("$name spo2", tile.spo2, v.getValue(com.noop.metrics.MetricId.SPO2).value)
            assertEquals("$name estimate", tile.spo2IsEstimate,
                v.getValue(com.noop.metrics.MetricId.SPO2).provenanceNote == com.noop.metrics.ValueNote.STRAP_ESTIMATE)
        }
        // The re-analysed night shows its own HRV, not the carried night's.
        val own = buildTodayUiState(extra.getValue("reanalysed_night_keeps_its_hrv"))
        assertEquals(61.0, own.vitals.getValue(com.noop.metrics.MetricId.HRV).value!!, 0.0)
        assertEquals(95.4, buildTodayUiState(extra.getValue("spo2_strap_estimate")).vitals.getValue(com.noop.metrics.MetricId.SPO2).value!!, 0.0)
    }
}
