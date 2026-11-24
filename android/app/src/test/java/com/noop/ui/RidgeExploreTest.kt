package com.noop.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RidgeExploreTest {
    @Test
    fun everyBuiltInMetricHasAWhatItMeansParagraph() {
        listOf("recovery", "strain", "hrv", "rhr", "sleep", "efficiency", "spo2", "resp").forEach { assertNotNull(it, exploreAboutRes(it)) }
    }

    @Test
    fun importedSeriesHaveNoMadeUpParagraph() {
        assertNull(exploreAboutRes("calories_in"))
        assertNull(exploreAboutRes("mood"))
    }
}
