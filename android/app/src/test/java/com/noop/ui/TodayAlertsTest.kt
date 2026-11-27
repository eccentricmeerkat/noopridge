package com.noop.ui

import androidx.compose.ui.graphics.Color
import com.noop.analytics.IllnessWatch
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** RS-2a: Today's Vitals / Illness heads-up alert slot, and the Body Monitor count that reads the same five vitals. */
class TodayAlertsTest {

    private fun vital(key: String, label: String, value: Double?, band: VitalBands.Band) = Vital(
        key = key, label = label, unit = "", value = value,
        format = { it.toString() },
        missingCaption = "none",
        banding = VitalBands.Result(band, VitalBands.Basis.PERSONAL, 30),
        metricColor = Color.White,
    )

    /** A reported screen: Resp out, no calibrated SpO₂ (a strap estimate in the registry), RHR in, no HRV, Skin out. */
    private fun paulsLatestDay() = listOf(
        vital("resp", "Resp Rate", 12.3, VitalBands.Band.OUT_OF_RANGE),
        vital("spo2", "Blood O₂", null, VitalBands.Band.NO_DATA),
        vital("rhr", "Resting HR", 55.0, VitalBands.Band.IN_RANGE),
        vital("hrv", "HRV", null, VitalBands.Band.NO_DATA),
        vital("skin", "Skin Temp", 32.9, VitalBands.Band.OUT_OF_RANGE),
    )

    @Test
    fun bodyMonitorWithoutTheRegistrySpo2WasTheOneOfThreeBug() {
        val s = summariseVitals(paulsLatestDay())
        assertEquals(1, s.inRange)
        assertEquals(3, s.read)
    }

    @Test
    fun withTheRegistrySpo2BodyMonitorReadsTwoOfFourLikeThePage() {
        val vitals = withRegistrySpo2(paulsLatestDay(), LatestSpo2(96.0, LocalDate.of(2026, 6, 6), estimate = true), "Strap estimate")
        val s = summariseVitals(vitals)
        assertEquals(2, s.inRange)
        assertEquals(4, s.read)
        assertEquals(listOf("Resp Rate", "Skin Temp"), s.outOfRange.map { it.label })
        val spo2 = vitals.first { it.key == "spo2" }
        assertTrue(spo2.isEstimate)
        assertTrue(spo2.secondary!!.startsWith("Strap estimate · "))
    }

    @Test
    fun aCalibratedSpo2IsNeverReplaced() {
        val withCal = paulsLatestDay().map { if (it.key == "spo2") vital("spo2", "Blood O₂", 91.0, VitalBands.Band.OUT_OF_RANGE) else it }
        val out = withRegistrySpo2(withCal, LatestSpo2(98.0, LocalDate.of(2026, 6, 6), estimate = true), "Strap estimate")
        assertEquals(91.0, out.first { it.key == "spo2" }.value!!, 0.0)
    }

    @Test
    fun allInRangeAndQuietShowsNothing() {
        val calm = paulsLatestDay().map { it.copy(banding = it.banding.copy(band = if (it.value == null) VitalBands.Band.NO_DATA else VitalBands.Band.IN_RANGE)) }
        val state = todayAlertsState(calm, null)
        assertTrue(state.isEmpty)
    }

    @Test
    fun missingVitalsAreNeverCountedAsOutside() {
        val allMissing = paulsLatestDay().map { it.copy(value = null, banding = it.banding.copy(band = VitalBands.Band.NO_DATA)) }
        assertTrue(todayAlertsState(allMissing, null).isEmpty)
    }

    @Test
    fun twoOutsideShowsTheVitalsCardAloneFullWidth() {
        val state = todayAlertsState(paulsLatestDay(), null)
        assertTrue(state.showVitals)
        assertFalse(state.showIllness)
        assertFalse(state.sideBySide)
        assertEquals(listOf("Resp Rate", "Skin Temp"), state.vitalsOut)
    }

    @Test
    fun fiveOutsideCountsAllFive() {
        val all = paulsLatestDay().map { it.copy(value = 1.0, banding = it.banding.copy(band = VitalBands.Band.OUT_OF_RANGE)) }
        assertEquals(RIDGE_VITALS_TOTAL, todayAlertsState(all, null).vitalsOut.size)
    }

    @Test
    fun bothFiringSitSideBySide() {
        val state = todayAlertsState(paulsLatestDay(), "Your body looks strained - resting HR +6 bpm, HRV −24%. Consider taking it easy.")
        assertTrue(state.sideBySide)
        assertEquals("Resting HR +6 bpm, HRV −24%", state.illnessSignals)
    }

    @Test
    fun theHeadsUpAloneShowsWithNoVitalsOutside() {
        val calm = paulsLatestDay().map { if (it.banding.band == VitalBands.Band.OUT_OF_RANGE) it.copy(banding = it.banding.copy(band = VitalBands.Band.IN_RANGE)) else it }
        val state = todayAlertsState(calm, "Your body looks strained - skin temp +0.7°C, respiration up. Consider taking it easy.")
        assertTrue(state.showIllness)
        assertFalse(state.showVitals)
        assertEquals("Skin temp +0.7°C, respiration up", state.illnessSignals)
    }

    @Test
    fun anUnexpectedMessageStillShowsTheCardWithoutAList() {
        assertEquals("", illnessSignalsOf("Something else entirely"))
        assertTrue(todayAlertsState(emptyList(), "Something else entirely").showIllness)
    }

    /** The parser is pinned to IllnessWatch's real output, so a change to its wording fails here rather than on the card. */
    @Test
    fun parsesTheRealIllnessWatchMessage() {
        fun day(d: String, rhr: Int, hrv: Double) =
            DailyMetric(deviceId = "test", day = d, restingHr = rhr, avgHrv = hrv, skinTempDevC = 0.0, respRateBpm = 14.0)
        val baseline = (0 until 31).map { day("2026-01-%02d".format(it + 1), 50, 60.0) }
        val recent = listOf(day("2026-02-01", 58, 20.0), day("2026-02-02", 58, 20.0))
        val msg = IllnessWatch.evaluate(baseline + recent)
        assertNotNull(msg)
        val signals = illnessSignalsOf(msg!!)
        assertTrue(signals, signals.startsWith("Resting HR +8 bpm"))
        assertTrue(signals, signals.contains("HRV"))
    }
}
