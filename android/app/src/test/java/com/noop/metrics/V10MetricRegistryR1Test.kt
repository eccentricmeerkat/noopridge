package com.noop.metrics

import com.noop.analytics.HrZones
import com.noop.analytics.ReadinessEngine
import com.noop.analytics.RestScorer
import com.noop.analytics.SleepDebt
import com.noop.analytics.TrainingLoadEngine
import com.noop.data.DailyMetric
import com.noop.ui.hoursVsNeededPct
import com.noop.ui.sleepDebtNeedMin
import com.noop.ui.sleepTabNeedMin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * R1a (J-01) acceptance: each metric added to the registry equals the v10 call the surface that shows it makes,
 * over a fixed 50-day history with a gap. No value is re-derived; a mismatch means the registry drifted.
 */
class V10MetricRegistryR1Test {

    private val start = LocalDate.of(2026, 1, 1)
    private fun key(i: Int) = start.plusDays(i.toLong()).toString()

    /** 50 days; day 30 has no row. Strain is present every day so the load model has a contiguous run. */
    private val days: List<DailyMetric> = (0 until 50).filter { it != 30 }.map { i ->
        DailyMetric(
            deviceId = "my-whoop", day = key(i),
            totalSleepMin = 360.0 + (i * 29 % 150),
            restingHr = 50 + (i * 7 % 11), avgHrv = 50.0 + (i * 13 % 35), recovery = 20.0 + (i * 23 % 75),
            strain = 25.0 + (i * 11 % 55), respRateBpm = 14.0 + (i % 5) * 0.3,
        )
    }
    private val today = start.plusDays(49)
    private val naps = mapOf(key(40) to 35.0, key(44) to 20.0)
    private val importedNeed = mapOf(key(45) to 500.0)
    private val importedDebt = mapOf(key(46) to -42.0)
    private val calories = mapOf(key(48) to 612.0, key(49) to 288.0)
    private val vo2 = mapOf(key(38) to 44.2, key(45) to 44.9)

    private val inputs = V10MetricInputs(
        days = days, today = today, age = 40, restNeedHours = 8.25,
        importedNeedMin = importedNeed, importedDebtMin = importedDebt, napSleepMinByDay = naps,
        caloriesByDay = calories, computedSeries = mapOf("vo2max_est" to vo2),
        // These tests pin v10's own definitions; the R9a corrections are tested in MetricsV2Test.
        v2 = emptySet(),
    )
    private val reg = V10MetricRegistry(inputs)
    private fun v(id: MetricId, i: Int) = reg.value(id, DayKey(start.plusDays(i.toLong()))).value

    @Test
    fun theFourSleepNeedsAreEachTheirOwnV10Definition() {
        assertEquals(8.25, v(MetricId.SLEEP_NEED_REST, 49)!!, 0.0)
        val past = reg.value(MetricId.SLEEP_NEED_REST, DayKey(start.plusDays(48)))
        assertNull(past.value)
        assertEquals(MissingReason.NOT_STORED, past.missing)
        assertEquals(RestScorer.defaultSleepNeedHours, v(MetricId.SLEEP_NEED_STORED, 10)!!, 0.0)
        assertEquals(sleepTabNeedMin(days), v(MetricId.SLEEP_NEED_SLEEP_TAB, 44)!!, 0.0)
        assertEquals(500.0, v(MetricId.SLEEP_NEED_SLEEP_TAB, 45)!!, 0.0) // imported need wins for its day
        assertEquals(sleepDebtNeedMin(days), v(MetricId.SLEEP_NEED_DEBT, 49)!!, 0.0)
    }

    @Test
    fun hoursVsNeededAndDebtMatchTheSleepTab() {
        val row = days.first { it.day == key(44) }
        assertEquals(hoursVsNeededPct(row.totalSleepMin, sleepTabNeedMin(days))!!, v(MetricId.HOURS_VS_NEEDED, 44)!!, 0.0)
        val debt = SleepDebt.debtSeries(
            series = days.map { it.day to SleepDebt.creditedSleepMin(it.totalSleepMin, naps[it.day] ?: 0.0) },
            needHours = sleepDebtNeedMin(days) / 60.0,
            importedDebtMin = importedDebt,
        ).toMap()
        for (i in listOf(40, 44, 46, 49)) assertEquals(debt[key(i)]!!, v(MetricId.SLEEP_DEBT, i)!!, 0.0)
        assertEquals(-42.0, v(MetricId.SLEEP_DEBT, 46)!!, 0.0)
        assertNull(v(MetricId.SLEEP_DEBT, 30))
    }

    @Test
    fun trainingLoadMatchesTheTrendsCard() {
        // TrainingLoadCard: evaluate(days.map { DailyLoad(day, strain) }), latest point.
        val card = TrainingLoadEngine.evaluate(days.map { TrainingLoadEngine.DailyLoad(it.day, it.strain) })
        val p = card.points.last()
        assertEquals(p.ctl, v(MetricId.TRAINING_LOAD_CTL, 49)!!, 0.0)
        assertEquals(p.atl, v(MetricId.TRAINING_LOAD_ATL, 49)!!, 0.0)
        assertEquals(p.tsb, v(MetricId.TRAINING_FORM, 49)!!, 0.0)
        // The gap on day 30 breaks the run: days 31..44 are the first 14-day contiguous suffix again.
        assertNull(v(MetricId.TRAINING_LOAD_CTL, 35))
    }

    @Test
    fun readinessAndAcwrMatchTheTodayChip() {
        val r = ReadinessEngine.evaluate(days, today = key(49))
        assertEquals(r.level.ordinal.toDouble(), v(MetricId.READINESS, 49)!!, 0.0)
        assertEquals(r.level.name.lowercase(), reg.band(MetricId.READINESS, r.level.ordinal.toDouble())!!.key)
        val acwr = v(MetricId.ACWR, 49)
        if (r.acwr == null) assertNull(acwr) else assertEquals(r.acwr!!, acwr!!, 0.0)
    }

    @Test
    fun caloriesMaxHrAndWeeklyScores() {
        assertEquals(612.0, v(MetricId.ACTIVE_KCAL, 48)!!, 0.0)
        assertNull(v(MetricId.ACTIVE_KCAL, 47))
        assertEquals(HrZones.zones(age = 40.0).maxHR, v(MetricId.MAX_HR, 49)!!, 0.0)
        val overridden = V10MetricRegistry(inputs.copy(maxHrOverride = 191.0))
        assertEquals(191.0, overridden.value(MetricId.MAX_HR, DayKey(today)).value!!, 0.0)
        val noAge = V10MetricRegistry(inputs.copy(age = null)).value(MetricId.MAX_HR, DayKey(today))
        assertEquals(MissingReason.NEEDS_AGE, noAge.missing)
        // Weekly: Health shows the newest reading, so a value carries from the last stored Saturday.
        val carried = reg.value(MetricId.VO2_MAX, DayKey(today))
        assertEquals(44.9, carried.value!!, 0.0)
        assertEquals(DayKey.of(key(45)), carried.carriedFrom)
        val s = reg.series(MetricId.VO2_MAX, DayKey(start.plusDays(38)), DayKey(today))
        assertEquals(12, s.size)
        assertEquals(2, s.count { it.value != null })
    }

    @Test
    fun stepsFollowTheTodayTilePrecedence() {
        val withSteps = days.map { if (it.day == key(47)) it.copy(steps = 9100) else it }
        val r = V10MetricRegistry(inputs.copy(
            days = withSteps,
            importedStepsByDay = mapOf(key(47) to 8000, key(48) to 13003),
            stepsEstimateByDay = mapOf(key(48) to 7000.0, key(49) to 6543.6),
        ))
        fun s(i: Int) = r.value(MetricId.STEPS, DayKey(start.plusDays(i.toLong()))).value
        assertEquals(9100.0, s(47)!!, 0.0)   // on-device count wins
        assertEquals(13003.0, s(48)!!, 0.0)  // then imported
        assertEquals(6544.0, s(49)!!, 0.0)   // then the estimate, rounded like the tile
        assertNull(s(46))
    }

    @Test
    fun everyMetricHasASourceLine() {
        for (id in MetricId.entries) assertTrue("$id source", V10MetricRegistry.SOURCES[id].orEmpty().isNotBlank())
    }

    @Test
    fun everyMetricHasADefinitionAndAFormulaPage() {
        for (id in MetricId.entries) {
            val d = reg.definition(id)
            assertTrue("$id formula page", java.io.File("../docs/ridge-build/metrics/${d.formulaPage}").exists() ||
                java.io.File("../../docs/ridge-build/metrics/${d.formulaPage}").exists())
        }
    }
}
