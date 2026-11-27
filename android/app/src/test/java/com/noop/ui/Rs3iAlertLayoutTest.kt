package com.noop.ui

import com.noop.ui.TodaySection.HERO
import com.noop.ui.TodaySection.KEY_METRICS
import com.noop.ui.TodaySection.LIVE_SESSION
import com.noop.ui.TodaySection.SYNTHESIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RS-3i: the half-width heads-up wording, and when Readiness moves up under the alert cards. */
class Rs3iAlertLayoutTest {

    @Test
    fun theHalfWidthCardNamesTheSignals() {
        assertEquals("Resting HR, HRV, Breathing", illnessSignalNames("Resting HR +13 bpm, HRV −50%, respiration up"))
        assertEquals("Resting HR, HRV, Skin temp, Breathing", illnessSignalNames("Resting HR +13 bpm, HRV −50%, skin temp +0.9°C, respiration up"))
        assertEquals("", illnessSignalNames(""))
    }

    @Test
    fun readinessMovesUpOnlyUnderTheAlerts() {
        assertTrue(alertsSitAboveSynthesis(listOf(HERO, SYNTHESIS, KEY_METRICS), true))
        assertTrue(alertsSitAboveSynthesis(listOf(HERO, LIVE_SESSION, SYNTHESIS), true))
        assertFalse(alertsSitAboveSynthesis(listOf(HERO, SYNTHESIS), false))
        assertFalse(alertsSitAboveSynthesis(listOf(HERO, KEY_METRICS, SYNTHESIS), true))
        assertFalse(alertsSitAboveSynthesis(listOf(SYNTHESIS, HERO), true))
    }
}
