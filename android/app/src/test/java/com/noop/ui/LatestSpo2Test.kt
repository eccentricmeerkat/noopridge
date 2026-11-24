package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.metrics.V2Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Fix 5: Blood oxygen shows the newest reading even when it is older than the tile's 14 days, as Insights charts it. */
class LatestSpo2Test {
    private val today = LocalDate.of(2026, 11, 22)
    private fun key(back: Int) = today.minusDays(back.toLong()).toString()
    private fun day(back: Int, spo2: Double?) = DailyMetric(deviceId = "my-whoop", day = key(back), spo2Pct = spo2)
    private fun registry(days: List<DailyMetric>, candidates: Map<String, Double> = emptyMap()) =
        V10MetricRegistry(V10MetricInputs(days, emptyMap(), emptyMap(), today, spo2CandidateByDay = candidates, v2 = setOf(V2Change.SPO2_ONE_SOURCE)))

    @Test fun anOlderReadingIsFoundAndDated() {
        val days = (0..60).map { day(it, if (it == 40) 96.0 else null) }
        val l = latestSpo2(registry(days), today)
        assertNotNull(l)
        assertEquals(96.0, l!!.value, 1e-9)
        assertEquals(today.minusDays(40), l.day)
        assertFalse(l.estimate)
    }

    @Test fun theTrendsTileCarriesTheOlderReadingWithItsDay() {
        val days = (0..60).map { day(it, if (it == 40) 96.0 else null) }
        val s = buildSignals(registry(days), today).spo2
        assertEquals(96.0, s.latest!!, 1e-9)
        assertEquals(today.minusDays(40), s.asOf)
    }

    @Test fun aRecentReadingNeedsNoDate() {
        val days = (0..20).map { day(it, if (it == 2) 97.0 else null) }
        val s = buildSignals(registry(days), today).spo2
        assertEquals(97.0, s.latest!!, 1e-9)
        assertNull(s.asOf)
    }

    @Test fun theStrapEstimateIsFlaggedAsAnEstimate() {
        val days = (0..10).map { day(it, null) }
        val l = latestSpo2(registry(days, mapOf(key(3) to 95.0)), today)
        assertTrue(l!!.estimate)
        assertEquals(today.minusDays(3), l.day)
    }

    @Test fun noReadingAnywhereIsNull() = assertNull(latestSpo2(registry((0..10).map { day(it, null) }), today))
}
