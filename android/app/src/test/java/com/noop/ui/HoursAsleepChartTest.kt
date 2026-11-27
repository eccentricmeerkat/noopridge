package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** RS-1-2 item 6: the Hours asleep card's 14-night window, gaps, the short-night colour and the three figures. */
class HoursAsleepChartTest {
    private val last = LocalDate.of(2026, 11, 25)
    private fun key(back: Int) = last.minusDays(back.toLong()).toString()

    @Test fun fourteenCalendarNightsEndingOnTheLastDayWithGapsAsNoBar() {
        val h = HoursAsleepChart.build(mapOf(key(0) to 420.0, key(2) to 300.0, key(13) to 480.0, key(14) to 999.0, key(5) to 0.0), last)!!
        assertEquals(14, h.nights.size)
        assertEquals(last.minusDays(13), h.nights.first().day)
        assertEquals(last, h.nights.last().day)
        assertNull(h.nights[12].min)          // yesterday: nothing recorded
        assertNull(h.nights[8].min)           // a 0 is not a night
        assertEquals(300.0, h.minMin, 0.0)    // gaps and the out-of-window 999 are left out
        assertEquals(480.0, h.maxMin, 0.0)
        assertEquals(400.0, h.avgMin, 1e-9)
    }

    @Test fun underThreeNightsIsTheEmptyState() {
        assertNull(HoursAsleepChart.build(mapOf(key(0) to 420.0, key(1) to 430.0), last))
    }

    @Test fun defaultSelectionIsTheNewestNightWithSleep() {
        val h = HoursAsleepChart.build(mapOf(key(1) to 420.0, key(3) to 430.0, key(4) to 400.0), last)!!
        assertEquals(12, h.defaultIndex)
    }

    @Test fun shortNightsAreUnderSixHoursAndTheTopFitsTheData() {
        assertTrue(HoursAsleepChart.isShort(359.0))
        assertTrue(!HoursAsleepChart.isShort(360.0))
        val h = HoursAsleepChart.build(mapOf(key(0) to 420.0, key(1) to 600.0, key(2) to 400.0), last)!!
        assertEquals(600.0, HoursAsleepChart.topMin(h, 486.0), 0.0)
        assertEquals(620.0, HoursAsleepChart.topMin(h, 620.0), 0.0)
        val low = HoursAsleepChart.build(mapOf(key(0) to 420.0, key(1) to 300.0, key(2) to 400.0), last)!!
        assertEquals(540.0, HoursAsleepChart.topMin(low, null), 0.0)
    }
}
