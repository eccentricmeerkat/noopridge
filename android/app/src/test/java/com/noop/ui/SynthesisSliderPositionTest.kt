package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the expanded Synthesis slider places a Charge. The track gives each of the five bands an equal
 * fifth, so the knob must land inside the fifth whose word the card shows, and a higher Charge must never
 * sit left of a lower one.
 */
class SynthesisSliderPositionTest {

    @Test
    fun `band floors land on the fifths`() {
        assertEquals(0.0f, synthesisSliderPosition(0.0), 1e-6f)
        assertEquals(0.2f, synthesisSliderPosition(25.0), 1e-6f)
        assertEquals(0.4f, synthesisSliderPosition(50.0), 1e-6f)
        assertEquals(0.6f, synthesisSliderPosition(70.0), 1e-6f)
        assertEquals(0.8f, synthesisSliderPosition(88.0), 1e-6f)
        assertEquals(1.0f, synthesisSliderPosition(100.0), 1e-6f)
    }

    @Test
    fun `a score sits inside its own band's fifth`() {
        // 39 is "Low" (25..50), so it belongs in the second fifth.
        val p = synthesisSliderPosition(39.0)
        assertTrue(p > 0.2f && p < 0.4f)
    }

    @Test
    fun `out of range scores clamp to the track ends`() {
        assertEquals(0.0f, synthesisSliderPosition(-5.0), 1e-6f)
        assertEquals(1.0f, synthesisSliderPosition(140.0), 1e-6f)
    }

    @Test
    fun `position never decreases as the score rises`() {
        var last = -1f
        for (tenths in 0..1000) {
            val p = synthesisSliderPosition(tenths / 10.0)
            assertTrue("dropped at ${tenths / 10.0}", p >= last)
            last = p
        }
    }
}
