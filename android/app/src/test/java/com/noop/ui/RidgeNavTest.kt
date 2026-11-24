package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A-01 (R5-1): the Ridge bar's tabs and which tab is lit. */
class RidgeNavTest {

    @Test
    fun barIsTodayTrendsBaselineSleepHub() {
        assertEquals(
            listOf("today", "trends", "baseline", "sleep", "more"),
            ridgeTabs { "" }.map { it.key },
        )
    }

    @Test
    fun aScreenReachedThroughHubLightsHub() {
        assertEquals(Destination.Trends, ridgeTabFor(Destination.Trends))
        assertEquals(Destination.Baseline, ridgeTabFor(Destination.Baseline))
        assertEquals(Destination.More, ridgeTabFor(Destination.Workouts))
        assertEquals(Destination.More, ridgeTabFor(Destination.Coach))
    }

    @Test
    fun everyRidgeTabResolvesToItsRoute() {
        for (d in ridgeTabDestinations) assertEquals(d, Destination.forRoute(d.route))
    }
}
