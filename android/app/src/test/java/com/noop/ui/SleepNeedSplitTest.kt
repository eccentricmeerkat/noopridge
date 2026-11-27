package com.noop.ui

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** RS-1-2 item 5: Sleep needed = Base + Recent Effort + Sleep debt, with each part's rule pinned. */
class SleepNeedSplitTest {
    // 30 days alternating 40 and 60: mean 50, sample SD = sqrt(30 * 100 / 29) ≈ 10.17.
    private val hist30 = List(30) { if (it % 2 == 0) 40.0 else 60.0 }
    private val sd = kotlin.math.sqrt(30 * 100.0 / 29)

    @Test fun debtPartIsAQuarterOfTheDebtUpToAnHour() {
        fun debtPart(debt: Double) = SleepNeedParts.split(8.0, debt, null, emptyList(), debtOn = true, effortOn = true)!!.debtMin
        assertEquals(0, debtPart(0.0))
        assertEquals(30, debtPart(120.0))
        assertEquals(60, debtPart(360.0))
    }

    @Test fun debtPartIsTheBedtimePaybackForTheSameDebt() {
        listOf(0.0, 5.0, 10.0, 37.0, 120.0, 239.0, 240.0, 900.0).forEach { debt ->
            assertEquals(RecommendedBedtime.paybackMin(debt), SleepNeedParts.split(8.0, debt, null, emptyList(), debtOn = true, effortOn = false)!!.debtMin)
        }
    }

    @Test fun effortAddsTwentyMinutesPerSdAboveYourUsualUpToAnHour() {
        assertEquals(0, SleepNeedParts.effortPartMin(50.0, hist30))              // at the mean
        assertEquals(20, SleepNeedParts.effortPartMin(50.0 + sd, hist30))        // +1 SD
        assertEquals(40, SleepNeedParts.effortPartMin(50.0 + 2 * sd, hist30))    // +2 SD
        assertEquals(60, SleepNeedParts.effortPartMin(50.0 + 4 * sd, hist30))    // capped
        assertEquals(0, SleepNeedParts.effortPartMin(20.0, hist30))              // an easy day adds nothing
    }

    @Test fun effortNeedsTodayAndFourteenDaysOfHistory() {
        assertNull(SleepNeedParts.effortPartMin(null, hist30))
        assertNull(SleepNeedParts.effortPartMin(80.0, hist30.take(13)))
        assertEquals(0, SleepNeedParts.effortPartMin(80.0, List(14) { 50.0 }))   // flat history: no spread, nothing added
    }

    @Test fun bothPartsOffIsBaseAloneSoTheCardReadsAsBefore() {
        val s = SleepNeedParts.split(8.1, 360.0, 50.0 + 4 * sd, hist30, debtOn = false, effortOn = false)!!
        assertEquals(486.0, s.totalMin, 1e-9)
        assertNull(s.effortMin)
        assertEquals(0, s.debtMin)
    }

    @Test fun totalIsTheSumOfThePartsAndNoBaseMeansNoSplit() {
        val s = SleepNeedParts.split(6.2, 240.0, 50.0 + 2.7 * sd, hist30, debtOn = true, effortOn = true)!!
        assertEquals(372.0 + 54 + 60, s.totalMin, 1e-9)
        assertNull(SleepNeedParts.split(null, 240.0, 80.0, hist30, debtOn = true, effortOn = true))
        assertNull(SleepNeedParts.split(0.0, 240.0, 80.0, hist30, debtOn = true, effortOn = true))
    }

    @Test fun effortInputsTakeTodayAndThePreviousThirtyDays() {
        val today = LocalDate.of(2026, 11, 25)
        val days = (0..40).map { back -> DailyMetric(deviceId = "d", day = today.minusDays(back.toLong()).toString(), strain = back.toDouble()) }
        val (t, hist) = SleepNeedParts.effortInputs(days, today)
        assertEquals(0.0, t!!, 0.0)
        assertEquals((1..30).map { it.toDouble() }, hist)
    }

    @Test fun paddedHoursAndMinutes() {
        assertEquals("8h 06m", hoursMinutesPadded(486.0))
        assertEquals("+0h 54m", hoursMinutesPadded(54.0, signed = true))
        assertEquals(1f, hoursOfSleepFill(540.0, 486.0))
        assertEquals(0.5f, hoursOfSleepFill(243.0, 486.0))
    }
}
