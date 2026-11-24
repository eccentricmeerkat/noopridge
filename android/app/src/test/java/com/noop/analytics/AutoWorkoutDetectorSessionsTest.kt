package com.noop.analytics

import com.noop.data.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS-x2-1 item 1: the stricter Today-card rule ([AutoWorkoutDetector.detectSessions]). */
class AutoWorkoutDetectorSessionsTest {

    private val dev = "test-device"
    private val rest = 58
    private val max = 186 // start gate 125 (floor), stay gate 90
    private val t0 = 1_000_020L // minute-aligned

    private fun minutes(startMin: Int, bpms: List<Int>): List<HrSample> =
        bpms.flatMapIndexed { i, bpm ->
            (0 until 60 step 5).map { s -> HrSample(deviceId = dev, ts = t0 + (startMin + i) * 60L + s, bpm = bpm) }
        }

    private fun flat(startMin: Int, n: Int, bpm: Int) = minutes(startMin, List(n) { bpm })

    private fun run(hr: List<HrSample>) = AutoWorkoutDetector.detectSessions(hr, restingHR = rest, maxHR = max)

    @Test fun gates() {
        assertEquals(125, AutoWorkoutDetector.cardStartGate(rest, max))
        assertEquals(90, AutoWorkoutDetector.cardStayGate(rest, max))
        assertEquals(125, AutoWorkoutDetector.cardStartGate(null, null))
    }

    @Test fun fourMinutesAt130IsNotAWorkout() = assertTrue(run(flat(0, 4, 130)).isEmpty())

    @Test fun nineMinutesAt130IsTooShort() = assertTrue(run(flat(0, 9, 130)).isEmpty())

    @Test fun tenMinutesAt126Prompts() {
        val out = run(flat(0, 10, 126))
        assertEquals(1, out.size)
        assertEquals(126, out[0].avgBpm)
    }

    @Test fun oneMinuteSpikeTo140Prompts_Nothing() =
        assertTrue(run(flat(0, 20, 80) + flat(20, 1, 140) + flat(21, 20, 80)).isEmpty())

    @Test fun briskWalk95to110IsNotAWorkout() =
        assertTrue(run(minutes(0, List(12) { 95 + (it % 4) * 5 })).isEmpty())

    @Test fun runWithTwoMinuteDipIsOnePrompt() {
        val bpms = List(14) { 150 } + List(2) { 70 } + List(14) { 150 }
        val out = run(minutes(0, bpms))
        assertEquals(1, out.size)
        assertTrue(out[0].durationMin >= 29)
    }

    @Test fun rampUpAndCoolDownAreKeptInTheWindow() {
        val bpms = List(3) { 100 } + List(10) { 140 } + List(3) { 100 }
        val out = run(minutes(0, bpms))
        assertEquals(1, out.size)
        assertTrue(out[0].durationMin >= 14)
    }

    @Test fun savedWorkoutSuppressesWindow() {
        val hr = flat(0, 15, 140)
        val out = AutoWorkoutDetector.detectSessions(hr, rest, max, listOf((t0 + 120) to (t0 + 600)))
        assertTrue(out.isEmpty())
    }
}
