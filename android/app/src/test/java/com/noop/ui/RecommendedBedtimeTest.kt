package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** BS-x2-2 item 11: recommended bedtime = wake target minus sleep need. */
class RecommendedBedtimeTest {
    private fun hm(h: Int, m: Int) = h * 60 + m

    @Test fun alarmAt0700AndNeed8h55GivesBedtime2205() {
        val b = RecommendedBedtime.compute(hm(7, 0), typicalWakeMin = hm(6, 0), needHours = 8.0 + 55 / 60.0, nights = 10)!!
        assertEquals(hm(22, 5), b.bedMin)
        assertEquals(hm(7, 0), b.wakeMin)
        assertTrue(b.fromAlarm)
    }

    @Test fun changingTheAlarmMovesTheBedtime() {
        val need = 8.0
        assertEquals(hm(22, 0), RecommendedBedtime.compute(hm(6, 0), null, need, 10)!!.bedMin)
        assertEquals(hm(23, 0), RecommendedBedtime.compute(hm(7, 0), null, need, 10)!!.bedMin)
    }

    @Test fun noAlarmUsesTheTypicalWake() {
        val b = RecommendedBedtime.compute(null, hm(6, 30), 8.0, 10)!!
        assertEquals(hm(22, 30), b.bedMin)
        assertFalse(b.fromAlarm)
    }

    @Test fun bedtimeWrapsPastMidnight() {
        assertEquals(hm(23, 45), RecommendedBedtime.bedtimeMin(hm(5, 15), 5.5 * 60))
        assertEquals(hm(1, 0), RecommendedBedtime.bedtimeMin(hm(9, 0), 8.0 * 60))
        assertEquals(hm(18, 0), RecommendedBedtime.bedtimeMin(hm(2, 0), 8.0 * 60))
    }

    @Test fun underFourNightsOrNoNeedShowsNothing() {
        assertNull(RecommendedBedtime.compute(hm(7, 0), hm(7, 0), 8.0, 3))
        assertNull(RecommendedBedtime.compute(hm(7, 0), hm(7, 0), null, 10))
        assertNull(RecommendedBedtime.compute(null, null, 8.0, 10))
    }

    @Test fun typicalWakeIsTheMedianAndNeedsFourNights() {
        assertNull(RecommendedBedtime.typicalWakeMin(listOf(hm(6, 0), hm(7, 0), hm(8, 0))))
        assertEquals(hm(7, 0), RecommendedBedtime.typicalWakeMin(listOf(hm(6, 0), hm(7, 0), hm(7, 0), hm(9, 0), hm(6, 30))))
    }

    @Test fun wakesEitherSideOfMidnightAreNeighbours() {
        val t = RecommendedBedtime.typicalWakeMin(listOf(hm(23, 50), hm(0, 10), hm(23, 55), hm(0, 5)))!!
        assertTrue("$t", t >= hm(23, 50) || t <= hm(0, 10))
    }

    @Test fun wakeMinutesKeepsOnePerDayWithinFourteenDays() {
        val zone = ZoneId.of("UTC")
        fun ts(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toEpochSecond()
        val now = ts(20, 12, 0)
        val out = RecommendedBedtime.wakeMinutes(
            listOf(ts(19, 6, 30), ts(19, 5, 0), ts(18, 7, 0), ts(1, 7, 0)), now, zone,
        )
        assertEquals(setOf(hm(6, 30), hm(7, 0)), out.toSet())
        assertEquals(2, out.size)
    }

    // --- Debt payback (owner, 24 Nov 2026) ---

    @Test fun aQuarterOfTheDebtIsRepaidUpToAnHour() {
        assertEquals(0, RecommendedBedtime.paybackMin(null))
        assertEquals(0, RecommendedBedtime.paybackMin(9.0))     // under the 'on target' band
        assertEquals(3, RecommendedBedtime.paybackMin(10.0))    // 25% of 10 min, rounded
        assertEquals(30, RecommendedBedtime.paybackMin(120.0))  // 2 h debt -> 30 min
        assertEquals(60, RecommendedBedtime.paybackMin(240.0))  // exactly the cap
        assertEquals(60, RecommendedBedtime.paybackMin(600.0))  // 10 h debt is still an hour
    }

    @Test fun debtMovesTheBedtimeEarlierByThePayback() {
        val plain = RecommendedBedtime.compute(hm(6, 30), null, 8.0, 10, debtMin = null, paybackOn = true)!!
        val inDebt = RecommendedBedtime.compute(hm(6, 30), null, 8.0, 10, debtMin = 240.0, paybackOn = true)!!
        assertEquals(hm(22, 30), plain.bedMin)
        assertEquals(hm(21, 30), inDebt.bedMin)
        assertEquals(60, inDebt.paybackMin)
        assertEquals(0, plain.paybackMin)
    }

    @Test fun theSwitchOffGivesTheOldBedtimeWhateverTheDebt() {
        val off = RecommendedBedtime.compute(hm(6, 30), null, 8.0, 10, debtMin = 600.0, paybackOn = false)!!
        assertEquals(hm(22, 30), off.bedMin)
        assertEquals(0, off.paybackMin)
    }

    @Test fun paybackWrapsPastMidnightLikeTheRest() {
        // Wake 05:00 with a 7.5 h need and a 1 h payback: 05:00 - 8.5 h = 20:30.
        assertEquals(hm(20, 30), RecommendedBedtime.compute(hm(5, 0), null, 7.5, 10, debtMin = 400.0, paybackOn = true)!!.bedMin)
    }

    @Test fun theSwitchIsOnInAReleaseBuild() {
        assertTrue(com.noop.metrics.V2Change.BEDTIME_DEBT_PAYBACK in com.noop.metrics.MetricsV2.APPROVED)
    }
}
