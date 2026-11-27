package com.noop.ui

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Insights > Explore showed Sleep Efficiency as "1%" for every night: the stored value is a fraction (0.92) and the catalog
 * printed it as a whole-number percent. Every display now goes through [efficiencyPercent].
 */
class SleepEfficiencyPercentTest {
    @Test fun aFractionBecomesAPercentAndAPercentStays() {
        assertEquals(92.0, efficiencyPercent(0.92)!!, 1e-9)
        assertEquals(100.0, efficiencyPercent(1.0)!!, 1e-9)
        assertEquals(88.0, efficiencyPercent(88.0)!!, 1e-9)   // a wearable export that stored a percent
        assertNull(efficiencyPercent(null))
    }

    @Test fun theExploreCatalogEntryReadsPercent() {
        val src = MetricCatalog.entries.first { it.key == "efficiency" }.source as CatSource.Daily
        assertEquals(92.0, src.pick(DailyMetric(deviceId = "d", day = "2026-11-25", efficiency = 0.92))!!, 1e-9)
        assertEquals(81.0, src.pick(DailyMetric(deviceId = "d", day = "2026-11-24", efficiency = 81.0))!!, 1e-9)
    }
}
