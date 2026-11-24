package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS-x2-1 item 2: the Edit workout selector's visible window. */
class WorkoutSelectorWindowTest {
    private val day = 1_700_000_000L - (1_700_000_000L % 86_400L) // a UTC midnight, any day will do
    private val h = 3_600L

    @Test fun defaultIsSixtyMinutesEitherSide() {
        val w = WorkoutSelectorWindows.default(day + 7 * h, day + 8 * h, day)
        assertEquals(day + 6 * h, w.fromSec)
        assertEquals(day + 9 * h, w.toSec)
    }

    @Test fun defaultIsClampedAtMidnightStart() {
        val w = WorkoutSelectorWindows.default(day + 20 * 60, day + h, day)
        assertEquals(day, w.fromSec)
        assertEquals(day + 2 * h, w.toSec)
    }

    @Test fun defaultIsClampedAtMidnightEnd() {
        val w = WorkoutSelectorWindows.default(day + 23 * h, day + 23 * h + 40 * 60, day)
        assertEquals(day + 22 * h, w.fromSec)
        assertEquals(day + 86_400L, w.toSec)
    }

    @Test fun pinchOutWidensAndStaysInTheDay() {
        val w0 = WorkoutSelectorWindows.default(day + 7 * h, day + 8 * h, day)
        val w1 = WorkoutSelectorWindows.transform(w0, 0.5f, 0.5f, 0f, day)
        assertEquals(w0.spanSec * 2, w1.spanSec)
        val wMax = WorkoutSelectorWindows.transform(w0, 0.1f, 0.5f, 0f, day)
        assertEquals(86_400L, wMax.spanSec)
        assertEquals(day, wMax.fromSec)
    }

    @Test fun pinchInStopsAtTheMinimumSpan() {
        val w0 = WorkoutSelectorWindows.default(day + 7 * h, day + 8 * h, day)
        val w = WorkoutSelectorWindows.transform(WorkoutSelectorWindows.transform(w0, 10f, 0.5f, 0f, day), 10f, 0.5f, 0f, day)
        assertEquals(WorkoutSelectorWindows.MIN_SPAN_SEC, w.spanSec)
    }

    @Test fun zoomKeepsTheFocusPointFixed() {
        val w0 = SelectorWindow(day + 6 * h, day + 10 * h)
        val w1 = WorkoutSelectorWindows.transform(w0, 2f, 0.25f, 0f, day)
        assertEquals(day + 7 * h, w1.fromSec + w1.spanSec / 4) // 25% across stays at 07:00
    }

    @Test fun panCannotLeaveTheDay() {
        val w0 = SelectorWindow(day + 30 * 60, day + 2 * h)
        val w = WorkoutSelectorWindows.transform(w0, 1f, 0.5f, 5f, day)
        assertTrue(w.fromSec >= day)
        val e = WorkoutSelectorWindows.transform(SelectorWindow(day + 22 * h, day + 24 * h), 1f, 0.5f, -5f, day)
        assertTrue(e.toSec <= day + 86_400L)
    }
}
