package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** C-04: [shownCharge], the one Charge the Today orb and Key Metrics tile both show (identity by construction). */
class TodayShownChargeTest {
    private val carried = LastCharge(51.1, "Last night · 11 Nov")

    @Test fun todaysOwnScoreWins() = assertEquals(72.0, shownCharge(72.0, calibrating = false, carried = carried)!!, 0.0)
    @Test fun carriedWhenTodayUnscored() = assertEquals(51.1, shownCharge(null, calibrating = false, carried = carried)!!, 0.0)
    @Test fun nothingWhileCalibrating() = assertNull(shownCharge(null, calibrating = true, carried = carried))
    @Test fun nothingWithNoData() = assertNull(shownCharge(null, calibrating = false, carried = null))
}
