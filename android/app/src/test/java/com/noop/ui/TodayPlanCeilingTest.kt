package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C-03 / D-08: Today's plan shows the TOP of v10's recovery-derived optimal Effort band ([optimalStrainRange]),
 * never a literal. Pinned at every band edge (33.99 / 34 / 66.99 / 67) on both Effort scales.
 */
class TodayPlanCeilingTest {

    @Test
    fun noChargeMeansNoTarget() {
        assertNull(planEffortCeiling(null, EffortScale.HUNDRED))
        assertNull(planEffortCeiling(null, EffortScale.WHOOP))
    }

    @Test
    fun bandTopsOnTheWhoopScale() {
        assertEquals("10", planEffortCeiling(0.0, EffortScale.WHOOP))
        assertEquals("10", planEffortCeiling(33.99, EffortScale.WHOOP))
        assertEquals("14", planEffortCeiling(34.0, EffortScale.WHOOP))
        assertEquals("14", planEffortCeiling(66.99, EffortScale.WHOOP))
        assertEquals("18", planEffortCeiling(67.0, EffortScale.WHOOP))
        assertEquals("18", planEffortCeiling(100.0, EffortScale.WHOOP))
    }

    @Test
    fun bandTopsOnTheHundredScaleAreRescaledAndRounded() {
        // 10/21 = 47.6 -> 48, 14/21 = 66.7 -> 67, 18/21 = 85.7 -> 86.
        assertEquals("48", planEffortCeiling(20.0, EffortScale.HUNDRED))
        assertEquals("67", planEffortCeiling(50.0, EffortScale.HUNDRED))
        assertEquals("86", planEffortCeiling(80.0, EffortScale.HUNDRED))
    }

    @Test
    fun synthesisColoursFollowD02() {
        assertEquals(Ridge.brand, SYNTHESIS_STATE_COLOURS[3])   // Strong
        assertEquals(Ridge.effort, SYNTHESIS_STATE_COLOURS[4])  // Peak
        assertEquals(Ridge.stateDepleted, SYNTHESIS_STATE_COLOURS[0])
    }

    @Test
    fun targetsAboveFiftyReadAsPushUp() {
        // The band tops are 10, 14 and 18 of 21: 48, 67 and 86 on the 0-100 axis. Only the last two are above 50.
        assertFalse(planEffortPushesUp(null))
        assertFalse(planEffortPushesUp(0.0))
        assertFalse(planEffortPushesUp(33.99))
        assertTrue(planEffortPushesUp(34.0))
        assertTrue(planEffortPushesUp(66.99))
        assertTrue(planEffortPushesUp(67.0))
    }
}
