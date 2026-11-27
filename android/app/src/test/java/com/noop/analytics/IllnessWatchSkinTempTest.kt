package com.noop.analytics

import com.noop.data.DailyMetric
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RS-3i: `skinTempDevC` holds a change from normal for nights NOOP scored, but the wrist temperature itself for nights from
 * a WHOOP export. The illness check must not read a temperature as a change.
 */
class IllnessWatchSkinTempTest {

    /** 31 steady nights; the last two take [recentSkin], [recentRhr]. */
    private fun days(skin: Double, recentSkin: Double, recentRhr: Int = 55): List<DailyMetric> = (0 until 31).map { i ->
        val recent = i >= 29
        DailyMetric(
            "d", java.time.LocalDate.of(2026, 10, 1).plusDays(i.toLong()).toString(),
            restingHr = if (recent) recentRhr else 55, avgHrv = 60.0, respRateBpm = 14.5,
            skinTempDevC = if (recent) recentSkin else skin,
        )
    }

    @Test
    fun aSteadyImportedTemperatureIsNotAWarningSign() {
        // One real sign (resting HR) and a normal 34 °C wrist: one sign is not an alert.
        assertNull(IllnessWatch.evaluate(days(skin = 34.0, recentSkin = 34.1, recentRhr = 62)))
    }

    @Test
    fun anImportedTemperatureThatRisesStillCounts() {
        val message = IllnessWatch.evaluate(days(skin = 34.0, recentSkin = 34.9, recentRhr = 62))
        assertTrue("$message", message != null && message.contains("skin temp +0.9°C"))
    }

    @Test
    fun nooPsOwnChangeFromNormalCountsAsBefore() {
        val message = IllnessWatch.evaluate(days(skin = 0.0, recentSkin = 0.8, recentRhr = 62))
        assertTrue("$message", message != null && message.contains("skin temp +0.8°C"))
        assertNull(IllnessWatch.evaluate(days(skin = 0.0, recentSkin = 0.3, recentRhr = 62)))
    }

    /** A temperature with too few earlier temperatures to compare with says nothing, whatever else the nights hold. */
    @Test
    fun aTemperatureWithNothingToCompareItWithIsIgnored() {
        val mixed = days(skin = 0.0, recentSkin = 34.0, recentRhr = 62)
        val message = IllnessWatch.evaluate(mixed)
        assertFalse("$message", message?.contains("skin temp") == true)
    }
}
