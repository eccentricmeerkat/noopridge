package com.noop.ui

import com.noop.metrics.V10MetricRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** R1b (J-01, HUB-04): the scoring guide's three sections map to registry metrics and draw on their scales. */
class ScoringGuideRegistryTest {
    @Test
    fun eachSectionReadsItsRegistryDefinition() {
        for (section in ScoreSection.entries) {
            val def = V10MetricRegistry.DEFINITIONS.getValue(section.metricId)
            assertEquals(section.name, def.scaleMax!!, section.scaleMax, 0.0)
            assertEquals(section.name, 0.0, def.scaleMin!!, 0.0)
        }
        // The copy says "each from 0 to 100"; the sample numbers are unchanged (82 / 64 / 88).
        assertEquals(listOf("82", "64", "88"), ScoreSection.entries.map { it.sampleNumber })
    }
}
