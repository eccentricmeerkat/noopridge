package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B2-1b: the Today heart-rate line's light smoothing and its blue / yellow / orange bands. */
class HeartRateLineTest {

    @Test
    fun smoothingWindowIsAboutOneAndAHalfPercentOfThePoints() {
        // 300 points at 1.5% is 4.5, rounded to 5 (odd): a spike of 300 in a flat 100 line is shared across 5 points.
        val flat = List(300) { 100.0 }
        val spiky = flat.toMutableList().also { it[150] = 300.0 }
        val out = smoothHeartRate(spiky, null)
        assertEquals(300, out.size)
        assertEquals(140.0, out[150], 1e-9)          // (4 x 100 + 300) / 5
        assertEquals(140.0, out[148], 1e-9)          // still inside the window of the spike
        assertEquals(100.0, out[140], 1e-9)          // far away: untouched
    }

    @Test
    fun smoothingKeepsTheSeriesLengthAndNeverCrossesAGap() {
        val values = List(100) { if (it < 50) 60.0 else 120.0 }
        val seg = List(100) { if (it < 50) "a" else "b" }
        val out = smoothHeartRate(values, seg)
        assertEquals(100, out.size)
        assertEquals(60.0, out[49], 1e-9)
        assertEquals(120.0, out[50], 1e-9)
        // Without segments the step is blurred.
        assertTrue(smoothHeartRate(values, null)[49] > 60.0)
    }

    @Test
    fun shortSeriesAndTinyWindowsAreLeftAlone() {
        assertEquals(listOf(70.0, 80.0), smoothHeartRate(listOf(70.0, 80.0), null))
        val sixty = List(60) { it.toDouble() }                 // 1.5% of 60 is under one point
        assertEquals(sixty, smoothHeartRate(sixty, null))
    }

    @Test
    fun theBandBrushNeedsTwoBandsAndARange() {
        val bands = HR_LINE_BANDS
        assertNotNull(lineBandBrush(bands, listOf(55.0, 90.0, 150.0), 200f, 6f, 6f, null))
        assertNull(lineBandBrush(bands.take(1), listOf(55.0, 90.0), 200f, 6f, 6f, null))
        assertNull(lineBandBrush(bands, listOf(80.0, 80.0), 200f, 6f, 6f, null))
        assertNull(lineBandBrush(bands, emptyList(), 200f, 6f, 6f, null))
    }

    @Test
    fun theThresholdsAreSeventyAndOneThirty() {
        assertEquals(listOf(0.0, 70.0, 130.0), HR_LINE_BANDS.map { it.first })
    }
}
