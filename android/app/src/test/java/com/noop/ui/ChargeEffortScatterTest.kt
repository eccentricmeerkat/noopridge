package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** R-08 (05 §3): Charge against the Effort 1–3 days before, Pearson r from 21 paired days. */
class ChargeEffortScatterTest {

    private val today = LocalDate.of(2026, 11, 14)
    private fun day(back: Int) = today.minusDays(back.toLong()).toString()

    @Test
    fun pearsonOnKnownSeries() {
        assertEquals(1.0, pearsonR(listOf(1.0 to 2.0, 2.0 to 4.0, 3.0 to 6.0))!!, 1e-12)
        assertEquals(-1.0, pearsonR(listOf(1.0 to 3.0, 2.0 to 2.0, 3.0 to 1.0))!!, 1e-12)
        assertNull("no variance", pearsonR(listOf(1.0 to 5.0, 2.0 to 5.0, 3.0 to 5.0)))
        assertNull("too few", pearsonR(listOf(1.0 to 2.0, 2.0 to 3.0)))
        // A hand-checked case: x = 1..5, y = 2, 4, 5, 4, 5 → r = 0.7746.
        assertEquals(0.7746, pearsonR((1..5).map { it.toDouble() }.zip(listOf(2.0, 4.0, 5.0, 4.0, 5.0)))!!, 1e-4)
    }

    @Test
    fun eachDaysRestPairsWithTheEffortLagDaysBefore() {
        // Charge on each day = 100 − Effort the day before, exactly; day 10 back has no Charge.
        val rows = (70 downTo 0).map { back ->
            val effort = 20.0 + (back * 7 % 50)
            val prevEffort = 20.0 + ((back + 1) * 7 % 50)
            DailyMetric("d", day(back), strain = effort, recovery = if (back == 10) null else 100.0 - prevEffort)
        }
        val reg = V10MetricRegistry(V10MetricInputs(days = rows, restByDay = rows.mapNotNull { r -> r.recovery?.let { r.day to it } }.toMap(), today = today))
        val s1 = buildChargeEffortScatter(reg, today, lag = 1)
        assertEquals(SCATTER_DAYS - 1, s1.n)
        assertEquals(-1.0, s1.r!!, 1e-12)
        val s2 = buildChargeEffortScatter(reg, today, lag = 2)
        assertEquals(2, s2.lag)
        assertEquals(SCATTER_DAYS - 1, s2.n)
    }

    @Test
    fun noCorrelationUnderTwentyOnePairs() {
        val rows = (19 downTo 0).map { back -> DailyMetric("d", day(back), strain = 30.0 + back, recovery = 50.0 + back % 7) }
        val reg = V10MetricRegistry(V10MetricInputs(days = rows, restByDay = rows.mapNotNull { r -> r.recovery?.let { r.day to it } }.toMap(), today = today))
        val s = buildChargeEffortScatter(reg, today, lag = 1)
        assertEquals(19, s.n)
        assertNull(s.r)
    }
}
