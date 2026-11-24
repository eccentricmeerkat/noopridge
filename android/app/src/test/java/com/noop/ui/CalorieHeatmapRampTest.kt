package com.noop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The active-calories heatmap's burn ramp (`calorieHeatmapRamp`). Its two jobs, pinned as tests: the
 * empty cell (level 0, "no data") must read as a bare cell rather than "a little burn", and each burn
 * level must be a distinct colour — not the old one-amber-at-four-opacities ramp that muddied the steps.
 * Translucent levels are composited over the glass card's ground (bg100) so they are compared as drawn.
 */
class CalorieHeatmapRampTest {

    private val ground = Ridge.bg100

    private fun composited(c: Color) = c.compositeOver(ground)

    private fun distance(a: Color, b: Color): Double {
        val ca = composited(a); val cb = composited(b)
        val dr = (ca.red - cb.red).toDouble()
        val dg = (ca.green - cb.green).toDouble()
        val db = (ca.blue - cb.blue).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    @Test
    fun rampHasFiveLevels() {
        assertEquals(5, calorieHeatmapRamp.size)
    }

    @Test
    fun emptyCellIsClearlySeparateFromTheLowestBurn() {
        val d = distance(calorieHeatmapRamp[0], calorieHeatmapRamp[1])
        assertTrue("empty vs lowest burn is too close (%.2f)".format(d), d > 0.30)
    }

    @Test
    fun adjacentBurnLevelsAreDistinct() {
        // Floor sits below the closest pair (gold → orange, a hue-only step) but above "collapsed".
        for (i in 1 until calorieHeatmapRamp.size - 1) {
            val d = distance(calorieHeatmapRamp[i], calorieHeatmapRamp[i + 1])
            assertTrue("levels $i and ${i + 1} collapse (%.2f)".format(d), d > 0.08)
        }
    }
}
