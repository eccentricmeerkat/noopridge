package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7b-1 (G-02, A-03 refined): every Today tile key opens its own metric somewhere, never a different one. */
class RidgeInsightsTest {

    /** Every key Today's tiles and cards send (TodayKeyMetrics, dashboardCardMetricKey). */
    private val todayKeys = listOf(
        "recovery", "strain", "rest", "hrv", "rhr", "spo2", "resp", "steps_est", "active_kcal", "skin",
        "fitness_age", "vo2max_est", "vitality",
    )

    @Test
    fun tilesRouteToTheirMetric() {
        for (k in listOf("recovery", "strain", "hrv", "rhr", "spo2", "resp")) assertEquals(exploreRoute(k), ridgeMetricRoute(k))
        for (k in listOf("fitness_age", "vo2max_est")) assertEquals(Destination.Baseline.route, ridgeMetricRoute(k))
        // RS-3e: the Vitality tile opens Insights on Vitality, not Baseline.
        assertEquals(exploreRoute("vitality"), ridgeMetricRoute("vitality"))
        assertEquals(Destination.Sleep.route, ridgeMetricRoute("rest"))
        // Fix 5: Calories, Skin temperature and Steps open Insights on their metric (the vital_detail pages were empty or flat).
        assertEquals("explore/active_kcal", ridgeMetricRoute("active_kcal"))
        assertEquals("explore/skin", ridgeMetricRoute("skin"))
        assertEquals("explore/steps", ridgeMetricRoute("steps_est"))   // the alias resolves to the Steps metric
    }

    @Test
    fun everyTileKeyHasADestination() {
        val known = Destination.entries.map { it.route.substringBefore('/') }.toSet() + "explore"
        for (k in todayKeys) assertTrue(k, ridgeMetricRoute(k).substringBefore('/') in known)
    }

    @Test
    fun threeModesInBoardOrder() {
        assertEquals(listOf("explore", "compare", "intelligence"), InsightsMode.entries.map { it.route })
        for (m in InsightsMode.entries) assertEquals(m, InsightsMode.entries.first { it.route == Destination.forRoute(m.route).route })
    }
}
