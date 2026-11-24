package com.noop.ui

import com.noop.data.DailyMetric
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * C-01 part 2 (R3c-1): [buildTodayUiState] over 9 fixture days, each rendered to text and compared with a
 * committed snapshot in `today_ui_state/<case>.txt`. A changed field fails the test, so later Today releases
 * (C-04's nine states) cannot move what Today shows by accident.
 *
 * Re-record after a deliberate, reviewed change:
 *   NOOP_GOLDEN_RECORD=1 ./gradlew testFullDebugUnitTest --tests com.noop.ui.TodayUiStateBuilderTest
 */
class TodayUiStateBuilderTest {

    private val record = System.getenv("NOOP_GOLDEN_RECORD") == "1"
    private lateinit var savedLocale: Locale

    @Before fun pinLocale() { savedLocale = Locale.getDefault(); Locale.setDefault(Locale.UK) }
    @After fun restoreLocale() { Locale.setDefault(savedLocale) }

    private val today = LocalDate.of(2026, 11, 11)
    private fun key(back: Int) = today.minusDays(back.toLong()).toString()

    private fun night(back: Int, recovery: Double? = 60.0, hrv: Double? = 70.0, strain: Double? = 40.0) = DailyMetric(
        deviceId = "my-whoop", day = key(back), totalSleepMin = 420.0, restingHr = 55, avgHrv = hrv,
        recovery = recovery, strain = strain, respRateBpm = 14.5, spo2Pct = 97.0, skinTempDevC = 0.1,
    )

    /** 20 scored nights ending [lastBack] days ago. */
    private fun history(lastBack: Int = 1) = (lastBack + 19 downTo lastBack).map { night(it, recovery = 40.0 + it % 30) }

    private fun inputs(
        days: List<DailyMetric>, todayRow: DailyMetric?, offset: Int = 0, live: Double? = null,
        localCalendar: LocalDate = today,
    ) = TodayInputs(days, todayRow, today, localCalendar, offset, live, hrvBaselineEpoch = 0.0)

    private val cases: Map<String, TodayInputs> by lazy {
        val scoredToday = night(0, recovery = 72.0, strain = 35.0)
        val unscoredToday = night(0, recovery = null, hrv = null, strain = 12.0)
        mapOf(
            "1_brand_new" to inputs(emptyList(), null),
            "2_calibrating_2_of_4" to listOf(night(2, null), night(1, null)).let { inputs(it, null) },
            "3_scored_today" to (history() + scoredToday).let { inputs(it, scoredToday) },
            "4_carried_last_night" to (history() + unscoredToday).let { inputs(it, unscoredToday) },
            "5_stale_carry" to (history(lastBack = 5) + unscoredToday).let { inputs(it, unscoredToday) },
            "6_past_day" to (history() + scoredToday).let { inputs(it, scoredToday, offset = 3) },
            "7_live_effort_above_stored" to (history() + scoredToday).let { inputs(it, scoredToday, live = 61.5) },
            "8_live_effort_below_stored_is_floored" to (history() + scoredToday).let { inputs(it, scoredToday, live = 9.0) },
            "9_pre_0400_local_row" to night(0, recovery = 55.0).copy(day = today.plusDays(1).toString()).let { localRow ->
                inputs(history() + localRow, localRow, localCalendar = today.plusDays(1))
            },
        )
    }

    private fun DailyMetric?.brief() = this?.let { "${it.day} rec=${it.recovery} strain=${it.strain}" } ?: "null"

    private fun render(s: TodayUiState) = listOf(
        "selectedDay=${s.selectedDay}",
        "selectedDayKey=${s.selectedDayKey}",
        "isToday=${s.isToday}",
        "displayMetric=${s.displayMetric.brief()}",
        "effortForDay=${s.effortForDay}",
        "recoveryCalibration=${s.recoveryCalibration}",
        "carryOverTodayKey=${s.carryOverTodayKey}",
        "lastScoredRecoveryDay=${s.lastScoredRecoveryDay?.day}",
        "lastVitalsDay=${s.lastVitalsDay?.day}",
        "lastSpo2Day=${s.lastSpo2Day?.day}",
        "lastSkinTempDay=${s.lastSkinTempDay?.day}",
        "lastHrvDay=${s.lastHrvDay?.day}",
        "lastRestingHrDay=${s.lastRestingHrDay?.day}",
        "lastRespDay=${s.lastRespDay?.day}",
        "scoreState=${s.scoreState}",
    ).joinToString("\n", postfix = "\n")

    private fun goldenFile(name: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/test/resources/today_ui_state") }
            .firstOrNull { it.isDirectory }
            ?.let { File(it, "$name.txt") }
            ?: File(javaClass.classLoader!!.getResource("today_ui_state")!!.toURI()).resolve("$name.txt")

    @Test
    fun nineFixtureDaysMatchTheirSnapshots() {
        val mismatches = mutableListOf<String>()
        for ((name, input) in cases) {
            val actual = render(buildTodayUiState(input))
            val file = goldenFile(name)
            if (record) { file.writeText(actual); continue }
            check(file.exists()) { "Missing snapshot ${file.path}; record with NOOP_GOLDEN_RECORD=1" }
            if (file.readText() != actual) mismatches += "$name:\n--- expected\n${file.readText()}--- actual\n$actual"
        }
        assertEquals("TodayUiState snapshots changed:\n" + mismatches.joinToString("\n"), 0, mismatches.size)
    }

    @Test
    fun liveEffortNeverReadsBelowTheStoredValue() {
        val s = buildTodayUiState(cases.getValue("8_live_effort_below_stored_is_floored"))
        assertEquals(35.0, s.effortForDay!!, 0.0)
    }

    @Test
    fun carryOnlyHappensOnToday() {
        val s = buildTodayUiState(cases.getValue("6_past_day"))
        assertEquals(null, s.lastScoredRecoveryDay)
        assertEquals(null, s.lastVitalsDay)
        assertEquals(null, s.recoveryCalibration)
    }
}
