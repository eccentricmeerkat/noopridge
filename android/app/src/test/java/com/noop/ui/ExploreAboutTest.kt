package com.noop.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** BS-x2-4 item 21: the five metrics have a "What it means" card. */
class ExploreAboutTest {
    @Test fun theFiveNewMetricsHaveACard() {
        listOf("acwr", "ctl", "atl", "form", "sdnn", "skin", "active_kcal", "workout_kcal", "hc_active_kcal").forEach { assertNotNull(it, exploreAboutRes(it)) }
    }

    @Test fun theExistingOnesAreKept() {
        listOf("recovery", "strain", "hrv", "rhr", "sleep", "efficiency", "spo2", "resp").forEach { assertNotNull(it, exploreAboutRes(it)) }
    }

    @Test fun anUnknownMetricHasNone() = assertNull(exploreAboutRes("steps"))
}
