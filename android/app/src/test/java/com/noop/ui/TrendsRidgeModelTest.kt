package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.ui.ridge.chart.SleepNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R4a: the Ridge Trends model windows and averages registry values, and invents none. */
class TrendsRidgeModelTest {

    private val today = LocalDate.of(2026, 11, 11)

    private fun day(back: Int) = today.minusDays(back.toLong()).toString()

    private fun model(
        rows: List<DailyMetric>,
        range: RidgeTrendsRange = RidgeTrendsRange.D14,
        basalByDay: Map<String, Double> = emptyMap(),
        activeByDay: Map<String, Double> = emptyMap(),
        consistencyByDay: Map<String, Double> = emptyMap(),
        workoutKcalByDay: Map<String, Double> = emptyMap(),
    ): TrendsRidgeModel {
        val sorted = rows.sortedBy { it.day }
        val registry = V10MetricRegistry(V10MetricInputs(days = sorted, today = today))
        return buildTrendsRidge(registry, sorted.associateBy { it.day }, today, range, basalByDay, activeByDay, workoutKcalByDay, consistencyByDay)
    }

    @Test
    fun oneSlotPerDayWithGapsAsNull() {
        val rows = (0 until 14).filter { it != 3 }.map { DailyMetric("d", day(it), recovery = 50.0 + it) }
        for (r in RidgeTrendsRange.entries) assertEquals(r.days, model(rows, r).charge.values.size)
        val m = model(rows)
        assertEquals(today, m.days.last())
        // Index 13 is today, so 3 days back is index 10.
        assertNull(m.charge.values[10])
        assertEquals(13, m.charge.tracked)
        assertEquals(50.0, m.charge.today!!, 0.0)
    }

    @Test
    fun chargeWeekMeansUseTheLeadDays() {
        // 7 days back to 13 days back at 40, the last 7 days at 60.
        val rows = (0 until 14).map { DailyMetric("d", day(it), recovery = if (it < 7) 60.0 else 40.0) }
        val c = model(rows, RidgeTrendsRange.D7).charge
        assertEquals(60.0, c.thisWeek!!, 1e-9)
        assertEquals(40.0, c.lastWeek!!, 1e-9)
        assertEquals(60.0, c.average!!, 1e-9)
    }

    @Test
    fun heartBandWaitsForATrustedBaseline() {
        val few = (0 until 10).map { DailyMetric("d", day(it), avgHrv = 50.0 + (it % 3), restingHr = 55) }
        val m = model(few)
        assertNull(m.heart.hrv.band)
        assertEquals(NormalPosition.NO_BAND, m.heart.hrv.position)

        val many = (0 until 60).map { DailyMetric("d", day(it), avgHrv = 50.0 + (it % 5), restingHr = 54 + it % 3) }
        val h = model(many).heart.hrv
        assertNotNull(h.band)
        assertEquals(NormalPosition.WITHIN, h.position)
        // R9a V2-4 (D-05, approved): mean ± 1 SD of the tracked nights, so the band sits around 52 and is
        // narrower than v10's ± 2 SD.
        assertTrue(h.band!!.start < 52.0 && h.band!!.endInclusive > 52.0)
        assertTrue(h.band!!.endInclusive - h.band!!.start < 4.0)
    }

    @Test
    fun anOutlierIsDrawnButLeftOutOfTheMean() {
        val rows = (0 until 60).map { DailyMetric("d", day(it), avgHrv = if (it == 0) 400.0 else 50.0 + (it % 5)) }
        val h = model(rows).heart.hrv
        assertTrue(h.outlier.last())
        // The 7-day mean ignores tonight's 400 ms.
        assertTrue(h.mean7.last()!! < 60.0)
    }

    @Test
    fun sleepNightsComeFromTheSameRows() {
        val rows = listOf(
            DailyMetric("d", day(0), totalSleepMin = 420.0, lightMin = 210.0, deepMin = 90.0, remMin = 120.0),
            DailyMetric("d", day(1), totalSleepMin = 400.0),
        )
        val s = model(rows, RidgeTrendsRange.D7).sleep
        assertTrue(s.nights.last() is SleepNight.Staged)
        assertEquals(SleepNight.Unstaged(400.0), s.nights[5])
        assertEquals(SleepNight.Untracked, s.nights[0])
        assertEquals(2, s.tracked)
        assertEquals(410.0, s.avgAsleepMin!!, 1e-9)
    }

    /** R9b (§7.6): the sleep card's dashed need line is the registry's one need for each night, gaps included. */
    @Test
    fun theNeedLineIsTheOneNeedPerNight() {
        val rows = (0 until 30).filter { it != 4 }.map { DailyMetric("d", day(it), totalSleepMin = 400.0 + it * 11 % 180) }
        val sorted = rows.sortedBy { it.day }
        val registry = V10MetricRegistry(V10MetricInputs(days = sorted, today = today))
        val s = buildTrendsRidge(registry, sorted.associateBy { it.day }, today, RidgeTrendsRange.D14).sleep
        assertEquals(14, s.needHours.size)
        for (i in 0 until 14) {
            val d = today.minusDays((13 - i).toLong())
            assertEquals(registry.value(com.noop.metrics.MetricId.SLEEP_NEED, com.noop.metrics.DayKey(d)).value, s.needHours[i])
        }
        assertTrue(s.axis.hi >= s.needHours.filterNotNull().max())
    }

    // MARK: - B2-2b-3 · the Sleep card's regularity stat

    /** The board's "regularity" is the mean imported sleep_consistency (0–100) over the tracked nights. */
    @Test
    fun regularityIsTheMeanImportedConsistency() {
        val rows = (0 until 14).map { DailyMetric("d", day(it), totalSleepMin = 400.0) }
        val consistency = (0 until 14).associate { day(it) to if (it < 7) 90.0 else 80.0 }
        val s = model(rows, RidgeTrendsRange.D14, consistencyByDay = consistency).sleep
        assertEquals(85.0, s.regularity!!, 1e-9)
    }

    /** An untracked night and a tracked night with no imported value both drop out of the mean. */
    @Test
    fun regularitySkipsUntrackedAndValueLessNights() {
        val rows = (1 until 14).map { DailyMetric("d", day(it), totalSleepMin = 400.0) } // day(0) = today, untracked
        val consistency = (0 until 13).associate { day(it) to 90.0 } // day(0)..day(12): oldest day(13) has none
        val s = model(rows, RidgeTrendsRange.D14, consistencyByDay = consistency).sleep
        assertEquals(90.0, s.regularity!!, 1e-9)
    }

    // MARK: - B2-2a-2 · the Effort card and the drawn band

    /** The Effort card's numbers: the newest 7 days at 70 are the week, the 7 before at 40 the week before. */
    @Test
    fun effortWeekMeansAverageAndHardDays() {
        val rows = (0 until 14).map { DailyMetric("d", day(it), strain = if (it < 7) 70.0 else 40.0) }
        val e = model(rows, RidgeTrendsRange.D7).effort
        assertEquals(70.0, e.today!!, 1e-9)
        assertEquals(70.0, e.thisWeek!!, 1e-9)
        assertEquals(40.0, e.lastWeek!!, 1e-9)
        assertEquals(70.0, e.average!!, 1e-9)
        // Effort ≥ 67 is EffortBand.HIGH, the Intensity card's existing bound — not a new number.
        assertEquals(7, e.hardDays)
        assertEquals(7, e.tracked)
    }

    /** A day at 66.9 is Moderate and is not a hard day; the 7-day mean still carries it. */
    @Test
    fun hardDaysStopAtTheIntensityHighBound() {
        val rows = (0 until 14).map { DailyMetric("d", day(it), strain = if (it < 7) 67.0 else 66.9) }
        val e = model(rows, RidgeTrendsRange.D7).effort
        assertEquals(7, e.hardDays)
        val softer = (0 until 14).map { DailyMetric("d", day(it), strain = 66.9) }
        assertEquals(0, model(softer, RidgeTrendsRange.D7).effort.hardDays)
    }

    // MARK: - B2-2a-3 · Calories

    /** Imported days split base/active; averages exclude today, and the active share is round(active/total). */
    @Test
    fun caloriesSplitAndAverage() {
        // 14 days: basal 2000, active 500 on even back-indices and 100 on odd; today (back 0) is split too.
        val basal = (0 until 14).associate { day(it) to 2000.0 }
        val active = (0 until 14).associate { day(it) to (if (it % 2 == 0) 500.0 else 100.0) }
        val rows = (0 until 14).map { DailyMetric("d", day(it)) }
        val c = model(rows, RidgeTrendsRange.D14, basal, active).calories
        assertEquals(13, c.tracked) // today excluded from the average
        // Full days are back 1..13: six at 500 (even) and seven at 100 (odd) → active mean 3700 / 13.
        assertEquals(3700.0 / 13, c.activeAvg!!, 1e-9)
        assertEquals(2000.0 + 3700.0 / 13, c.totalAvg!!, 1e-9)
        assertEquals(2500.0, c.todayTotal!!, 1e-9) // 2000 + 500 (back 0 is even)
        assertEquals(12, c.activeShare!!) // round(3700 / 29700 · 100) = 12
        assertTrue(c.hasSplit)
    }

    /** A strap-only day has no import, so the whole-day estimate is base and there is no split. */
    @Test
    fun caloriesStrapDayIsWholeDayBase() {
        val rows = (0 until 14).map { DailyMetric("d", day(it), activeKcalEst = 1800.0) }
        val c = model(rows, RidgeTrendsRange.D14).calories
        assertEquals(13, c.tracked)
        assertEquals(1800.0, c.totalAvg!!, 1e-9)
        assertNull(c.activeAvg)
        assertNull(c.activeShare)
        assertTrue(!c.hasSplit)
        // Every day is base-only (no active cap).
        assertTrue(c.days.filter { it.base != null }.all { it.active == null })
    }

    /** A logged workout is the active part of a strap-only day's whole-day estimate (the owner's 20 Nov correction). */
    @Test
    fun caloriesStrapDayTakesLoggedWorkoutsAsActive() {
        val rows = (0 until 14).map { DailyMetric("d", day(it), activeKcalEst = 2000.0) }
        // Back 3: one workout of 600 kcal. Back 5: two workouts summing 900. Back 7: recorded above the day's total.
        val workouts = mapOf(day(3) to 600.0, day(5) to 900.0, day(7) to 2500.0)
        val c = model(rows, RidgeTrendsRange.D14, workoutKcalByDay = workouts).calories
        fun cell(back: Int) = c.days.first { it.date == today.minusDays(back.toLong()) }
        assertEquals(1400.0, cell(3).base!!, 1e-9)
        assertEquals(600.0, cell(3).active!!, 1e-9)
        assertEquals(2000.0, cell(3).total!!, 1e-9)          // the total never changes
        assertEquals(1100.0, cell(5).base!!, 1e-9)
        assertEquals(900.0, cell(5).active!!, 1e-9)
        assertEquals(0.0, cell(7).base!!, 1e-9)              // capped at the whole-day total, never negative
        assertEquals(2000.0, cell(7).active!!, 1e-9)
        assertNull(cell(4).active)                           // days without a workout stay whole-day base
        assertTrue(c.hasSplit)
        assertEquals((600.0 + 900.0 + 2000.0) / 13, c.activeAvg!!, 1e-9)
        assertEquals(2000.0, c.totalAvg!!, 1e-9)
    }

    /** A day with NO strap estimate keeps the import's own split: workouts are already inside Apple/Health's active energy. */
    @Test
    fun caloriesImportedDayIgnoresWorkoutsToAvoidDoubleCounting() {
        val rows = (0 until 14).map { DailyMetric("d", day(it), activeKcalEst = if (it == 2) null else 2000.0) }
        val basal = mapOf(day(2) to 1500.0)
        val active = mapOf(day(2) to 400.0)
        val c = model(rows, RidgeTrendsRange.D14, basal, active, workoutKcalByDay = mapOf(day(2) to 700.0)).calories
        val d = c.days.first { it.date == today.minusDays(2) }
        assertEquals(1500.0, d.base!!, 1e-9)
        assertEquals(400.0, d.active!!, 1e-9)
    }

    /** The strap always wins (owner, 21 Nov): a flat Health Connect basal of 1826 never replaces the strap's own estimate. */
    @Test
    fun caloriesStrapWinsOverAnImportOnTheSameDay() {
        val rows = (0 until 14).map { DailyMetric("d", day(it), activeKcalEst = 2300.0 + it) }
        val basal = (0 until 14).associate { day(it) to 1826.0 }
        val active = (0 until 14).associate { day(it) to 246.0 }
        val c = model(rows, RidgeTrendsRange.D14, basal, active).calories
        val d = c.days.first { it.date == today.minusDays(3) }
        assertEquals(2303.0, d.total!!, 1e-9)
        assertNull(d.active)
        assertTrue(c.days.filter { it.base != null }.map { it.total }.distinct().size > 1) // not one flat value
    }
}
