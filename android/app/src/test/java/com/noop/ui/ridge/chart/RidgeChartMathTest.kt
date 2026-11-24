package com.noop.ui.ridge.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-01: the pure chart rules (05 R1–R8, §6, §7). */
class RidgeChartMathTest {

    @Test
    fun heartAxisConstantSeriesGetsTheMinimumSpan() {
        // §6.10: a constant series → minimum span applied (RHR 8 bpm), four ticks, snapped to whole bpm.
        val a = heartAxis(List(14) { 55.0 }, band = null, minSpan = 8.0, step = 1.0)
        // 51..59 widens to 9 bpm (three 3-bpm gaps) so every tick is a whole bpm.
        assertEquals(listOf(51.0, 54.0, 57.0, 60.0), a.ticks)
    }

    @Test
    fun heartAxisCoversPointsAndBandWithPadding() {
        val a = heartAxis(listOf(40.0, 50.0, 60.0), band = 45.0..70.0, minSpan = 20.0, step = 2.0)
        // pool 40..70, pad 3 → 37..73, snapped to 2 ms → 36..74, widened to three 14 ms gaps → 34..76.
        assertEquals(listOf(34.0, 48.0, 62.0, 76.0), a.ticks)
    }

    @Test
    fun heartAxisWithFewPointsCentresOnThem() {
        val a = heartAxis(listOf(50.0), band = null, minSpan = 20.0, step = 2.0)
        // 40..60 widens to three 8 ms gaps, centred: 38..62.
        assertEquals(listOf(38.0, 46.0, 54.0, 62.0), a.ticks)
    }

    @Test
    fun sleepAxisStartsAtZeroAndExpands() {
        assertEquals(listOf(0.0, 3.0, 6.0, 9.0), sleepHoursAxis(listOf(6.5, 7.2)).ticks)
        // §7.10: a > 9 h night expands the axis (to the next 3 h tick).
        assertEquals(12.0, sleepHoursAxis(listOf(9.4)).hi, 0.0)
        assertEquals(0.0, sleepHoursAxis(listOf(3.2)).lo, 0.0)
    }

    @Test
    fun rollingMeanNeedsFourOfSeven() {
        val v = listOf(50.0, null, null, null, 54.0, 56.0, 58.0, 60.0)
        val m = rollingMean(v)
        // Index 6: window 0..6 has 50, 54, 56, 58 → 4 of 7 → 54.5.
        assertEquals(54.5, m[6]!!, 1e-9)
        // Index 5: 50, 54, 56 → 3 → null.
        assertNull(m[5])
        // The lead days are dropped so the result lines up with the visible window.
        assertEquals(v.size - 6, rollingMean(v, lead = 6).size)
        assertEquals(54.5, rollingMean(v, lead = 6)[0]!!, 1e-9)
    }

    @Test
    fun linesBridgeOneMissingDayButBreakAcrossTwo() {
        val v = listOf(1.0, null, 2.0, null, null, 3.0, 4.0)
        assertEquals(listOf(listOf(0, 2), listOf(5, 6)), lineRuns(v))
    }

    @Test
    fun scrubPicksTheNearestSlot() {
        assertEquals(0, scrubIndex(0f, 14, bars = false))
        assertEquals(13, scrubIndex(1f, 14, bars = false))
        assertEquals(7, scrubIndex(0.52f, 14, bars = false))
        // Bars: slot k spans k/n..(k+1)/n.
        assertEquals(0, scrubIndex(0.05f, 7, bars = true))
        assertEquals(6, scrubIndex(0.99f, 7, bars = true))
        assertEquals(6, scrubIndex(2f, 7, bars = true))
    }

    @Test
    fun axisLabelsKeepBothEnds() {
        assertEquals(listOf(0, 3, 7, 10, 13), axisLabelSlots(14))
        assertEquals(listOf(0, 1, 2), axisLabelSlots(3))
        assertEquals(listOf(0), axisLabelSlots(1))
    }

    @Test
    fun outliersNeedATrustedBaseline() {
        assertTrue(isOutlier(80.0, baseline = 50.0, sigma = 5.0))
        assertFalse(isOutlier(69.0, baseline = 50.0, sigma = 5.0))
        assertFalse(isOutlier(200.0, baseline = null, sigma = null))
    }

    @Test
    fun sleepStagesScaleToTheDuration() {
        // §7.10: stages summing to 400 against a 394-minute night are scaled to 394.
        val n = sleepNight(394.0, lightMin = 200.0, deepMin = 100.0, remMin = 100.0) as SleepNight.Staged
        assertEquals(394.0, n.lightMin + n.deepMin + n.remMin, 1e-9)
        assertEquals(197.0, n.lightMin, 1e-9)
    }

    @Test
    fun untrackedAndUnstagedNights() {
        assertEquals(SleepNight.Untracked, sleepNight(null, null, null, null))
        assertEquals(SleepNight.Untracked, sleepNight(0.0, null, null, null))
        assertEquals(SleepNight.Unstaged(420.0), sleepNight(420.0, null, null, null))
        // No stored duration: the stages give it.
        assertEquals(300.0, (sleepNight(null, 200.0, 50.0, 50.0) as SleepNight.Staged).asleepMin, 1e-9)
    }

    @Test
    fun percentChangeIsWhole() {
        assertEquals(6, percentChange(53.0, 50.0))
        assertEquals(-10, percentChange(45.0, 50.0))
        assertNull(percentChange(45.0, null))
        assertNull(percentChange(45.0, 0.0))
    }
}
