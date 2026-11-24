package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHrChartTest {
    @Test
    fun axisClockIsMinutesThenHours() {
        assertEquals("0:00", hrAxisClock(0))
        assertEquals("45:07", hrAxisClock(45 * 60 + 7))
        assertEquals("1:05:00", hrAxisClock(3900))
    }

    @Test
    fun pressPicksTheNearestReading() {
        val times = listOf(0.0, 60.0, 120.0, 180.0)
        assertEquals(0, nearestIndex(times, -5.0))
        assertEquals(1, nearestIndex(times, 80.0))
        assertEquals(3, nearestIndex(times, 500.0))
    }
}
