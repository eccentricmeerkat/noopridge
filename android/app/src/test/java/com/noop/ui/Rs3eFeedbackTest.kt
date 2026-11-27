package com.noop.ui

import androidx.compose.ui.graphics.Color
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.ui.ridge.chart.effortAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RS-3e: HRV on Vital signs when the latest night has none, and Effort's fitted axis. */
class Rs3eFeedbackTest {

    private fun hrv(value: Double?, band: VitalBands.Band) = Vital(
        key = "hrv", label = "HRV", unit = "ms", value = value, format = { it.toString() }, missingCaption = "none",
        deltaText = "(+2)", banding = VitalBands.Result(band, VitalBands.Basis.PERSONAL, 30), metricColor = Color.White,
    )

    private fun night(day: String, hrv: Double? = null) = DailyMetric("d", day, avgHrv = hrv, restingHr = 55)

    private fun carried(days: List<DailyMetric>) = withCarriedHrv(
        listOf(hrv(days.last().avgHrv, VitalBands.Band.NO_DATA)), days.last(), days, "From %1\$s",
    ) { row -> hrv(row.avgHrv, VitalBands.Band.OUT_OF_RANGE) }.single()

    @Test
    fun aLatestNightWithoutHrvShowsTheLastOneDatedAndStillBanded() {
        val v = carried(listOf(night("2026-11-24", 47.0), night("2026-11-25"), night("2026-11-26")))
        assertEquals(47.0, v.value!!, 0.0)
        assertEquals(VitalBands.Band.OUT_OF_RANGE, v.banding.band)
        assertEquals("From 24 Nov", v.secondary)
        assertNull(v.deltaText)
    }

    @Test
    fun anOlderOneIsShownButNeverCountedAsInOrOutOfRange() {
        val v = carried(listOf(night("2026-11-20", 47.0), night("2026-11-26")))
        assertEquals(47.0, v.value!!, 0.0)
        assertEquals(VitalBands.Band.NO_DATA, v.banding.band)
        assertEquals(0, summariseVitals(listOf(v)).read)
        assertEquals(emptyList<String>(), todayAlertsState(listOf(v), null).vitalsOut)
    }

    @Test
    fun tonightsOwnHrvWinsAndNothingOlderThanThirtyDaysIsShown() {
        assertEquals(51.0, carried(listOf(night("2026-11-25", 47.0), night("2026-11-26", 51.0))).value!!, 0.0)
        assertNull(carried(listOf(night("2026-10-01", 47.0), night("2026-11-26"))).value)
        assertNull(carried(listOf(night("2026-11-26"))).value)
    }

    @Test
    fun effortsAxisFitsTheDataOnEitherScale() {
        assertEquals(listOf(0.0, 25.0, 50.0), effortAxis(listOf(8.0, 31.0), whoop = false).ticks)
        assertEquals(75.0, effortAxis(listOf(51.0), whoop = false).hi, 0.0)
        assertEquals(100.0, effortAxis(listOf(96.0), whoop = false).hi, 0.0)
        assertEquals(50.0, effortAxis(emptyList(), whoop = false).hi, 0.0)
        // 0–21: whole-number ticks, drawn at their 0–100 places.
        assertEquals(listOf(0, 5, 10), effortAxis(listOf(30.0), whoop = true).ticks.map { Math.round(it * 21 / 100).toInt() })
        assertEquals(listOf(0, 7, 14, 21), effortAxis(listOf(90.0), whoop = true).ticks.map { Math.round(it * 21 / 100).toInt() })
    }
}
