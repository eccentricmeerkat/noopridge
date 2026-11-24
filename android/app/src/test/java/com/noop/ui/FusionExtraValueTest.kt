package com.noop.ui

import com.noop.analytics.FusionSource
import com.noop.data.AppleDaily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Fused page compares calories and VO2 max across the strap and the phone imports. */
class FusionExtraValueTest {
    private val hc = AppleDaily("health-connect", "d", activeKcal = 246.0, basalKcal = 1826.0, vo2max = 44.0)

    @Test fun anImportsCaloriesAreItsRestingPlusActiveTotal() {
        assertEquals(2072.0, FusionDayAdapter.extraValue("active_kcal", FusionSource.HEALTH_CONNECT, hc, null)!!, 1e-9)
        assertNull(FusionDayAdapter.extraValue("active_kcal", FusionSource.HEALTH_CONNECT, hc.copy(basalKcal = null), null))
    }

    @Test fun vo2MaxComesFromTheImportsAndTheStrapsWeeklyEstimate() {
        assertEquals(44.0, FusionDayAdapter.extraValue("vo2max", FusionSource.HEALTH_CONNECT, hc, null)!!, 1e-9)
        assertEquals(46.5, FusionDayAdapter.extraValue("vo2max", FusionSource.NOOP_COMPUTED, null, 46.5)!!, 1e-9)
        assertNull(FusionDayAdapter.extraValue("vo2max", FusionSource.WHOOP_IMPORT, null, 46.5))
    }

    @Test fun otherMetricsFallBackToTheDailyRow() = assertNull(FusionDayAdapter.extraValue("hrv", FusionSource.HEALTH_CONNECT, hc, 46.5))

    @Test fun vo2MaxIsFormattedWithItsUnit() = assertEquals("46.5 ml/kg/min", FusionFormat.value(46.5, "vo2max"))
}
