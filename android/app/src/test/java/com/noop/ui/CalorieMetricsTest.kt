package com.noop.ui

import com.noop.data.AppleDaily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The three calorie metrics (owner, 21 Nov 2026) each mean one thing. */
class CalorieMetricsTest {
    private fun hc(day: String, active: Double?, basal: Double?) = AppleDaily("health-connect", day, activeKcal = active, basalKcal = basal)

    @Test fun anImportsTotalNeedsBothParts() {
        assertEquals(2072.0, CalorieMetrics.importedTotal(hc("d", 246.0, 1826.0))!!, 1e-9)
        assertNull(CalorieMetrics.importedTotal(hc("d", 246.0, null)))   // active alone is not a total
        assertNull(CalorieMetrics.importedTotal(hc("d", null, 1826.0)))
    }

    @Test fun theStrapWinsAndAnImportFillsOnlyGaps() {
        val strap = mapOf("d1" to 2600.0)
        val out = CalorieMetrics.totalByDay(strap, listOf(hc("d1", 246.0, 1826.0), hc("d2", 300.0, 1800.0), hc("d3", 250.0, null)))
        assertEquals(2600.0, out.getValue("d1"), 1e-9)   // not 2072
        assertEquals(2100.0, out.getValue("d2"), 1e-9)
        assertNull(out["d3"])                             // an active-only day is not a total
    }

    @Test fun activeEnergyIsHealthConnectsOwnFigure() {
        val out = CalorieMetrics.hcActiveByDay(listOf(hc("d1", 246.0, 1826.0), hc("d2", null, 1800.0), hc("d3", 0.0, 1800.0)))
        assertEquals(mapOf("d1" to 246.0), out)
    }
}
