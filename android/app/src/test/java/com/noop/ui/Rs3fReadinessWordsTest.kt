package com.noop.ui

import com.noop.ui.ReadinessReason.EFFORT
import com.noop.ui.ReadinessReason.GOOD_REST
import com.noop.ui.ReadinessReason.HIGH_BOTH
import com.noop.ui.ReadinessReason.HIGH_CHARGE
import com.noop.ui.ReadinessReason.LOWER_REST
import com.noop.ui.ReadinessReason.LOW_BOTH
import com.noop.ui.ReadinessReason.LOW_CHARGE
import com.noop.ui.ReadinessReason.LOW_REST
import com.noop.ui.ReadinessReason.MODERATE_CHARGE
import com.noop.ui.ReadinessReason.SOLID_BOTH
import org.junit.Assert.assertEquals
import org.junit.Test

/** RS-3f: the cause the opened Readiness card names. */
class Rs3fReadinessWordsTest {

    /** The reason for a morning with no Effort taken off. */
    private fun morningReason(charge: Double, rest: Double): ReadinessReason {
        val morning = ReadinessScore.morning(charge, rest)
        return readinessReason(charge, rest, morning, ReadinessScore.band(morning))
    }

    @Test
    fun lowDaysNameWhatIsLow() {
        assertEquals(LOW_CHARGE, morningReason(22.0, 77.0))
        assertEquals(LOW_BOTH, morningReason(30.0, 40.0))
        assertEquals(LOW_REST, morningReason(52.0, 20.0))
    }

    @Test
    fun steadyDaysNameWhatHoldsThemBack() {
        assertEquals(LOW_CHARGE, morningReason(46.0, 95.0))
        assertEquals(MODERATE_CHARGE, morningReason(60.0, 80.0))
        assertEquals(LOWER_REST, morningReason(75.0, 40.0))
    }

    @Test
    fun goodDaysNameWhatCarriesThem() {
        assertEquals(HIGH_BOTH, morningReason(75.0, 92.0))
        assertEquals(HIGH_CHARGE, morningReason(80.0, 70.0))
        assertEquals(GOOD_REST, morningReason(66.0, 94.0))
        assertEquals(SOLID_BOTH, morningReason(69.0, 80.0))
        assertEquals(HIGH_BOTH, morningReason(95.0, 95.0))
    }

    @Test
    fun aCategoryBelowTheMorningsIsEfforts() {
        val morning = ReadinessScore.morning(75.0, 92.0)
        assertEquals(EFFORT, readinessReason(75.0, 92.0, morning, ReadinessScore.band(morning) - 1))
    }
}
