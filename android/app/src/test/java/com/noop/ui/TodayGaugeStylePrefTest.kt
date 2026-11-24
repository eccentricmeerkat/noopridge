package com.noop.ui

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Today gauge-style preference: vessels by default, rings when turned on.
 *
 * The default is the load-bearing part. The glass Today redesign (docs/assets/noop_UI_x2.png) draws the
 * scores as liquid orbs, so an install that has never opened Settings has to get vessels, and a pref that
 * defaulted the other way would silently undo that for everyone. Asserting the absent-key case is what
 * pins that, since reading a key nobody has written is exactly the state most installs are in.
 *
 * The key is deliberately NOT Apple's `noop.liquidTodayEnabled`. That one switches between two whole
 * Today screens there; this chooses a gauge inside the single Android one, and binding the two together
 * would make a later divergence on either platform quietly wrong.
 */
@RunWith(RobolectricTestRunner::class)
class TodayGaugeStylePrefTest {

    private fun ctx(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun `an install that never opened settings gets the vessels`() {
        val c = ctx()
        NoopPrefs.of(c).edit().remove(NoopPrefs.KEY_TODAY_RING_GAUGES).apply()
        assertFalse("the glass redesign draws vessels, so the absent key must read as false",
                    NoopPrefs.todayRingGauges(c))
    }

    @Test
    fun `turning it off selects the vessels and survives a read`() {
        val c = ctx()
        NoopPrefs.setTodayRingGauges(c, false)
        assertFalse(NoopPrefs.todayRingGauges(c))
        NoopPrefs.setTodayRingGauges(c, true)
        assertTrue(NoopPrefs.todayRingGauges(c))
    }

    @Test
    fun `the key is its own, not Apple's whole-screen toggle`() {
        // Sharing `noop.liquidTodayEnabled` would tie a gauge choice here to a screen choice there.
        assertTrue(NoopPrefs.KEY_TODAY_RING_GAUGES == "noop.todayRingGauges")
        assertFalse(NoopPrefs.KEY_TODAY_RING_GAUGES == "noop.liquidTodayEnabled")
    }
}
