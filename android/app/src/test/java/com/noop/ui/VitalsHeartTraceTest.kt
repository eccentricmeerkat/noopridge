package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RS-1-1 item 4: the Vital signs heart card's three states and its drawn heartbeat trace. */
class VitalsHeartTraceTest {
    @Test fun statesFollowTheLinkThenTheReading() {
        assertEquals(HeartCardState.NOT_CONNECTED, heartCardState(connected = false, hr = null))
        // A stale bpm after the link drops is not Live.
        assertEquals(HeartCardState.NOT_CONNECTED, heartCardState(connected = false, hr = 64))
        assertEquals(HeartCardState.WAITING, heartCardState(connected = true, hr = null))
        assertEquals(HeartCardState.LIVE, heartCardState(connected = true, hr = 64))
    }

    @Test fun oneBeatStartsAndEndsOnTheBaselineSoBeatsJoin() {
        assertEquals(0f, HEART_BEAT.first().x, 0f)
        assertEquals(1f, HEART_BEAT.last().x, 0f)
        assertEquals(HEART_BEAT.first().y, HEART_BEAT.last().y, 0f)
        assertTrue(HEART_BEAT.zipWithNext().all { (a, b) -> b.x > a.x })
        assertTrue(HEART_BEAT.all { it.y in 0f..1f })
    }

    @Test fun theTraceCoversTheWidthAtEveryPhase() {
        listOf(0f, 0.25f, 0.5f, 0.99f).forEach { phase ->
            val t = heartTrace(phase)
            assertTrue("starts at or before the left edge at $phase", t.first().x <= 0f)
            assertTrue("reaches the right edge at $phase", t.last().x >= 1f)
            assertTrue(t.zipWithNext().all { (a, b) -> b.x > a.x })
            assertTrue(t.all { it.y in 0f..1f })
        }
    }

    @Test fun onePeriodSlidesTheTraceLeftByExactlyOneBeat() {
        // Phase 1 is phase 0 again (a seamless loop every HEART_TRACE_PERIOD_MS), and half a period moves half a beat.
        assertEquals(heartTrace(0f), heartTrace(1f))
        val beatW = 1f / HEART_TRACE_BEATS
        val spike0 = heartTrace(0f).minBy { it.y }.x
        val spikeHalf = heartTrace(0.5f).filter { it.x >= 0f }.minBy { it.y }.x
        // The first beat's spike has slid off the left edge; the second beat's is now half a beat right of where the first was.
        assertEquals(spike0 + beatW / 2f, spikeHalf, 1e-5f)
        assertEquals(2_000, HEART_TRACE_PERIOD_MS)
    }
}
