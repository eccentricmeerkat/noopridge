package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS-x2-1 item 6: the Intensity Y axis is a clean step above the data, in minutes. */
class IntensityAxisTest {
    @Test fun lowVariationWeekGetsTheMinimumStep() {
        val a = minutesAxis(40.0)
        assertEquals(listOf(0.0, 30.0, 60.0, 90.0), a.ticks)
        assertEquals(90.0, a.hi, 0.0)
    }

    @Test fun highVariationWeekRoundsUpToACleanStep() {
        val a = minutesAxis(260.0) // 260 / 3 = 86.7 -> step 90
        assertEquals(listOf(0.0, 90.0, 180.0, 270.0), a.ticks)
        assertTrue(a.hi >= 260.0)
    }

    @Test fun ticksAreEvenlySpacedAndLabelsDoNotCollide() {
        val a = minutesAxis(500.0)
        val gaps = a.ticks.zipWithNext { x, y -> y - x }
        assertEquals(1, gaps.distinct().size)
        assertEquals(listOf("0m", "180m", "360m", "540m"), a.ticks.map(::minutesTickLabel))
    }
}
