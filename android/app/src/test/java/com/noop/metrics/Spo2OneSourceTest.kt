package com.noop.metrics

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** BS-x2-2 item 9: Blood oxygen's source order is pinned: calibrated or imported, then the strap estimate (tagged). */
class Spo2OneSourceTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun key(back: Int) = today.minusDays(back.toLong()).toString()
    private val on = setOf(V2Change.SPO2_ONE_SOURCE)

    private fun day(back: Int, spo2: Double?) = DailyMetric(deviceId = "my-whoop", day = key(back), spo2Pct = spo2)

    private fun registry(days: List<DailyMetric>, candidates: Map<String, Double>, v2: Set<V2Change>) =
        V10MetricRegistry(V10MetricInputs(days, emptyMap(), emptyMap(), today, spo2CandidateByDay = candidates, v2 = v2))

    @Test fun strapOnlyUserSeesTheEstimateOnEveryDayOfTheSeriesTagged() {
        val days = (0..4).map { day(it, null) }
        val cand = (0..4).associate { key(it) to 96.0 + it }
        val series = registry(days, cand, on).series(MetricId.SPO2, DayKey.of(key(4)), DayKey(today))
        assertEquals(listOf(100.0, 99.0, 98.0, 97.0, 96.0), series.map { it.value })
        series.forEach { assertEquals(ValueNote.STRAP_ESTIMATE, it.provenanceNote) }
    }

    @Test fun importedValueWinsOverTheEstimateAndIsNotTagged() {
        val days = listOf(day(1, 97.0), day(0, null))
        val cand = mapOf(key(1) to 90.0, key(0) to 95.0)
        val series = registry(days, cand, on).series(MetricId.SPO2, DayKey.of(key(1)), DayKey(today))
        assertEquals(listOf(97.0, 95.0), series.map { it.value })
        assertNull(series[0].provenanceNote)
        assertEquals(ValueNote.STRAP_ESTIMATE, series[1].provenanceNote)
    }

    @Test fun withTheChangeOffTheSeriesIgnoresTheEstimate() {
        val days = (0..2).map { day(it, null) }
        val cand = (0..2).associate { key(it) to 96.0 }
        val series = registry(days, cand, emptySet()).series(MetricId.SPO2, DayKey.of(key(2)), DayKey(today))
        series.forEach { assertNull(it.value) }
    }

    @Test fun theSeriesAndTheTodayPointAgree() {
        val days = listOf(day(0, null))
        val cand = mapOf(key(0) to 96.0)
        val r = registry(days, cand, on)
        assertEquals(r.value(MetricId.SPO2, DayKey(today)).value, r.series(MetricId.SPO2, DayKey(today), DayKey(today)).single().value)
    }
}
