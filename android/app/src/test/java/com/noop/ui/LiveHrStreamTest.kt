package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Vital signs live heart rate slides right to left: the newest reading at the right, older ones to the left. */
class LiveHrStreamTest {
    private val now = 1_000_000L
    private fun s(agoMs: Long, bpm: Double) = LiveHrSample(now - agoMs, bpm)

    @Test fun theNewestIsAtTheRightEdgeAndOlderOnesToTheLeft() {
        val pts = liveHrStream(listOf(s(30_000, 70.0), s(15_000, 72.0), s(0, 75.0)), now)
        assertEquals(1f, pts.last().x, 1e-6f)
        assertEquals(0.5f, pts.first().x, 1e-6f)
        assertTrue(pts.zipWithNext().all { (a, b) -> a.x < b.x })
    }

    @Test fun asTimePassesTheSameReadingMovesLeft() {
        val sample = listOf(s(0, 70.0))
        val x0 = liveHrStream(sample, now).single().x
        val x1 = liveHrStream(sample, now + 6_000).single().x
        assertEquals(1f, x0, 1e-6f)
        assertEquals(0.9f, x1, 1e-6f)   // 6 s of a 60 s window to the left
    }

    @Test fun readingsOlderThanTheWindowAreDropped() {
        assertEquals(1, liveHrStream(listOf(s(61_000, 70.0), s(5_000, 72.0)), now).size)
        assertTrue(liveHrStream(emptyList(), now).isEmpty())
    }

    @Test fun higherHeartRateIsHigherOnThePlotAndASteadyRateIsNotMagnified() {
        val pts = liveHrStream(listOf(s(20_000, 60.0), s(10_000, 90.0)), now)
        assertTrue(pts[1].y < pts[0].y)                      // y grows downward, 90 bpm sits above 60 bpm
        val flat = liveHrStream(listOf(s(20_000, 70.0), s(10_000, 71.0)), now)
        assertTrue(kotlin.math.abs(flat[0].y - flat[1].y) < 0.1f)   // a 1 bpm wobble stays a small wobble
    }
}
