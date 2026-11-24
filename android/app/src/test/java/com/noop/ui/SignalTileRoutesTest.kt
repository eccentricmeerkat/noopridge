package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** BS-x2-2 item 7: each Other-daily-signals tile opens its own metric, never the Journal. */
class SignalTileRoutesTest {
    @Test fun stressOpensExploreOnStressNotTheJournal() {
        assertEquals("explore/stress", ridgeMetricRoute(SIGNAL_TILE_KEYS.stress))
        assertNotEquals(Destination.Insights.route, ridgeMetricRoute(SIGNAL_TILE_KEYS.stress))
    }

    @Test fun breathAndBloodOxygenOpenExplore() {
        assertEquals("explore/resp", ridgeMetricRoute(SIGNAL_TILE_KEYS.breath))
        assertEquals("explore/spo2", ridgeMetricRoute(SIGNAL_TILE_KEYS.spo2))
    }

    @Test fun skinOpensInsightsOnSkinTemperature() {
        assertEquals("explore/skin", ridgeMetricRoute(SIGNAL_TILE_KEYS.skin))
    }

    @Test fun everyTileKeyIsACatalogueMetricAndNoTileGoesToTheJournal() {
        for (key in SIGNAL_TILE_KEYS.all) {
            assertNotNull("catalogue has $key", MetricCatalog.forKey(key))
            assertNotEquals("$key", Destination.Insights.route, ridgeMetricRoute(key))
        }
    }
}
