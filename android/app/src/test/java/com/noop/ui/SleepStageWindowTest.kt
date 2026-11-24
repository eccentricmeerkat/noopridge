package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS-x2-2 item 10: the scrollable stage chart's window, pan and segment lookup. */
class SleepStageWindowTest {
    private val h = 3600.0
    private val night = 8 * h

    @Test fun everyNightIsShownWholeAndDoesNotScroll() {
        listOf(3.5 * h, 8 * h, 11 * h).forEach { n ->
            assertFalse(SleepStageWindows.scrollable(n))
            val w = SleepStageWindows.default(n)
            assertEquals(0.0, w.fromSec, 0.0); assertEquals(n, w.toSec, 0.0)
            assertEquals(w, SleepStageWindows.pan(w, 2 * h, n))
        }
    }

    @Test fun xMapsToTimeInsideTheWindow() {
        val w = StageWindow(2 * h, 5.5 * h)
        assertEquals(2 * h, SleepStageWindows.secAtX(0f, 100f, w), 0.0)
        assertEquals(3.75 * h, SleepStageWindows.secAtX(50f, 100f, w), 1e-6)
        assertEquals(5.5 * h, SleepStageWindows.secAtX(500f, 100f, w), 0.0)
        assertEquals(50f, SleepStageWindows.xOfSec(3.75 * h, 100f, w), 1e-3f)
    }

    @Test fun tipFindsTheRunAndReportsItsDuration() {
        val ivs = listOf(
            StageInterval("light", 0.0, 1800.0), StageInterval("deep", 1800.0, 3600.0), StageInterval("rem", 4200.0, 5400.0),
        )
        val tip = SleepStageWindows.tipAt(ivs, 2000.0)
        assertNotNull(tip)
        assertEquals("deep", tip!!.stage)
        assertEquals(1800.0, tip.durationSec, 0.0)
        assertNull("a gap borrows no neighbour's stage", SleepStageWindows.tipAt(ivs, 3900.0))
        assertNull(SleepStageWindows.tipAt(emptyList(), 10.0))
    }

    @Test fun hourMarksAreWholeLocalHoursAndThinOutOnALongWindow() {
        val onset = 1_700_000_000L
        val off = java.util.TimeZone.getDefault().getOffset(onset * 1000L) / 1000L
        val short = SleepStageWindows.hourMarks(onset, StageWindow(0.0, 3.5 * h))
        assertTrue(short.size in 3..4)
        short.forEach { (f, t) -> assertEquals(0L, (t + off) % 3600L); assertTrue(f in 0f..1f) }
        val night = SleepStageWindows.hourMarks(onset, StageWindow(0.0, 8 * h))
        night.forEach { (_, t) -> assertEquals(0L, (t + off) % 7200L) }   // every second hour on a full night
        assertTrue(night.size in 3..5)
    }
}
