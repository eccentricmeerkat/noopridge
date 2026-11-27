package com.noop.ui

import com.noop.analytics.ReadinessEngine
import com.noop.data.DailyMetric
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R4b: the fixed-window Trends cards reuse v10's rules and the D-03 / D-07 / D-14 rulings. */
class TrendsRidgeFixedTest {

    private val today = LocalDate.of(2026, 11, 11) // a Wednesday

    private fun day(back: Int) = today.minusDays(back.toLong()).toString()

    private fun registry(rows: List<DailyMetric>, rest: Map<String, Double> = emptyMap()) =
        V10MetricRegistry(V10MetricInputs(days = rows.sortedBy { it.day }, restByDay = rest, today = today))

    @Test
    fun todaysLoadIsTheValueTodayUses() {
        val rows = (0 until 60).map { DailyMetric("d", day(it), strain = 30.0 + (it * 7) % 50) }
        val load = buildLoadTrend(registry(rows), rows, today)
        val v10 = ReadinessEngine.evaluate(rows.sortedBy { it.day }, today = today.toString()).signals
            .firstNotNullOf { it.evidence as? ReadinessEngine.Evidence.TrainingLoad }
        assertEquals(v10.acute, load.acute.last()!!, 1e-9)
        assertEquals(v10.chronic, load.chronic.last()!!, 1e-9)
        assertEquals(LOAD_DAYS, load.days.size)
    }

    @Test
    fun aPastDateUsesOnlyItsOwnHistory() {
        // 40 days at 40, then the last 10 days at 80: ten days ago the load was flat.
        val rows = (0 until 50).map { DailyMetric("d", day(it), strain = if (it < 10) 80.0 else 40.0) }
        val load = buildLoadTrend(registry(rows), rows, today)
        val tenDaysAgo = load.days.indexOf(today.minusDays(10))
        assertEquals(40.0, load.acute[tenDaysAgo]!!, 1e-9)
        assertEquals(40.0, load.chronic[tenDaysAgo]!!, 1e-9)
        assertFalse(load.ramp[tenDaysAgo])
        // Today: acute 80 vs chronic (10 × 80 + 18 × 40) / 28 ≈ 54.3 → ratio ≈ 1.47, shaded.
        assertTrue(load.ramp.last())
    }

    @Test
    fun rampStartsAtV10sBuildingFastThreshold() {
        // Chronic 28 readings: the acute 7 at a, the other 21 at b. Ratio = a / ((7a + 21b) / 28).
        fun rampAt(a: Double, b: Double): Boolean {
            val rows = (0 until 28).map { DailyMetric("d", day(it), strain = if (it < 7) a else b) }
            return buildLoadTrend(registry(rows), rows, today).ramp.last()
        }
        // a = 65, b = 40: chronic 46.25 → 1.405 ≥ 1.3; a = 50, b = 40: chronic 42.5 → 1.18.
        assertTrue(rampAt(65.0, 40.0))
        assertFalse(rampAt(50.0, 40.0))
    }

    @Test
    fun noLoadBeforeFourteenReadings() {
        val rows = (0 until 10).map { DailyMetric("d", day(it), strain = 50.0) }
        val load = buildLoadTrend(registry(rows), rows, today)
        assertNull(load.latestAcute)
        assertEquals(10, load.readings)
    }

    @Test
    fun effortBandEdges() {
        assertEquals(EffortBand.REST, effortBand(47.9))
        assertEquals(EffortBand.MODERATE, effortBand(48.0))
        assertEquals(EffortBand.MODERATE, effortBand(66.9))
        assertEquals(EffortBand.HIGH, effortBand(67.0))
        assertEquals(EffortBand.HIGH, effortBand(85.9))
        assertEquals(EffortBand.ALL_OUT, effortBand(86.0))
    }

    @Test
    fun consistencyGridIsTwelveMondayFirstWeeks() {
        val rows = (0 until 40).map { DailyMetric("d", day(it), strain = if (it % 2 == 0) 70.0 else 20.0) }
        // Rest tracked on all but two nights; above 85 on every third.
        val rest = (0 until 40).filter { it != 1 && it != 2 }.associate { day(it) to if (it % 3 == 0) 90.0 else 70.0 }
        val c = buildConsistency(registry(rows, rest), today)
        assertEquals(CONSISTENCY_WEEKS * 7, c.cells.size)
        assertEquals(LocalDate.of(2026, 8, 24), c.cells.first().date)
        assertEquals(java.time.DayOfWeek.MONDAY, c.cells.first().date.dayOfWeek)
        // Thu–Sun of the current week are future: 80 elapsed days of the 84.
        assertEquals(80, c.elapsed)
        assertTrue(c.cells.last().future && c.cells.last().band == null)
        // Only the last 40 days are tracked; every even day trains (Effort 70), the odd rest (Effort 20).
        assertEquals(20, c.trained)
        assertEquals(38, c.trackedNights)
        assertEquals((0 until 40).count { it != 1 && it != 2 && it % 3 == 0 }, c.onTarget)
    }

    /** RS-2d: the moon marks an Optimal Rest (85+) as shown, whether or not the night reached its full sleep need. */
    @Test
    fun theMoonMarksARestThatReads85OrMore() {
        val rests = listOf(84.0, 84.4, 84.5, 85.0, 99.0, 100.0)
        // Six hours asleep every night, well under any need: the old asleep-≥-need rule marked none of these.
        val rows = rests.indices.map { DailyMetric("d", day(it), totalSleepMin = 360.0) }
        val c = buildConsistency(registry(rows, rests.indices.associate { day(it) to rests[it] }), today)
        val byDay = c.cells.associate { it.date.toString() to it.onTarget }
        assertEquals(listOf(false, false, true, true, true, true), rests.indices.map { byDay[day(it)] })
        assertEquals(4, c.onTarget)
        assertEquals(6, c.trackedNights)
    }

    @Test
    fun signalsReadLatestAndSparkline() {
        val rows = (0 until 14).map {
            DailyMetric("d", day(it), strain = 30.0, spo2Pct = 96.0 + (it % 2), skinTempDevC = 0.1 * (it % 3), respRateBpm = 14.0 + (it % 4) * 0.1)
        }
        val s = buildSignals(registry(rows), today)
        assertEquals(SIGNALS_DAYS, s.spo2.values.size)
        // Latest = the newest day (back 0).
        assertEquals(96.0, s.spo2.latest!!, 1e-9)
        assertEquals(14.0, s.resp.latest!!, 1e-9)
        assertEquals(0.0, s.skinTemp.latest!!, 1e-9)
    }

    @Test
    fun intensityUsesStoredZonesOnly() {
        val z = listOf(10.0, 30.0, 20.0, 25.0, 15.0)
        val sessions = listOf(
            ZonedSession(today, 60.0, z),
            ZonedSession(today.minusDays(1), 30.0, null),       // no zones: nothing added
            ZonedSession(today.minusDays(40), 90.0, z),         // before the window
        )
        val t = buildIntensity(sessions, today)
        assertEquals(4, t.weeks.size)
        val week = t.weeks.last()
        assertEquals(24.0, week.low, 1e-9)
        assertEquals(12.0, week.moderate, 1e-9)
        assertEquals(24.0, week.high, 1e-9)
        assertEquals(2, t.sessions)
        assertEquals(1, t.sessionsWithZones)
        assertEquals(60.0, t.total, 1e-9)
    }

    // MARK: - B2-2a-6 · Synthesis calendar

    @Test
    fun synthesisBandsCellsByStageAndBucketsSharesByRawCharge() {
        // 60 tracked days, newest first: Charge 20 (Depleted) on the 10 newest, 45 (Low) on the next 20, 75 (Strong) on 30.
        val rows = (0 until 60).map {
            DailyMetric("d", day(it), recovery = when {
                it < 10 -> 20.0
                it < 30 -> 45.0
                else -> 75.0
            })
        }
        val s = buildSynthesis(registry(rows), today, readiness = false)
        assertEquals(SYNTHESIS_WEEKS * 7, s.cells.size)
        assertEquals(60, s.tracked)
        // Stage colours: 20 → Depleted (band 0), 45 → Low (band 1), 75 → Strong (band 3).
        assertEquals(0, s.cells.first { it.date == today }.band)
        assertEquals(1, s.cells.first { it.date == today.minusDays(15) }.band)
        assertEquals(3, s.cells.first { it.date == today.minusDays(40) }.band)
        // Shares (Q3): Low or Depleted < 40 → 10; Steady 40–69 → 20; Strong or Peak ≥ 70 → 30.
        assertEquals(50, s.strongOrPeak)
        assertEquals(33, s.steady)
        assertEquals(17, s.lowOrDepleted)
        assertEquals(100, s.strongOrPeak + s.steady + s.lowOrDepleted)
    }

    @Test
    fun synthesisMonthLabelsCollapseConsecutiveDuplicates() {
        val rows = (0 until 20).map { DailyMetric("d", day(it), recovery = 60.0) }
        val s = buildSynthesis(registry(rows), today, readiness = false)
        assertEquals(SYNTHESIS_WEEKS, s.monthLabels.size)
        // The first label is always present; repeats are blank.
        assertTrue(s.monthLabels.first().isNotEmpty())
        s.monthLabels.drop(1).forEachIndexed { i, m ->
            if (m.isNotEmpty()) assertTrue(s.monthLabels[i] != m || s.monthLabels[i] == "")
        }
    }
}
