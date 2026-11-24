package com.noop.metrics

import com.noop.analytics.Baselines
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.ui.StressBand
import com.noop.ui.StressModel
import com.noop.ui.freshRestScore
import com.noop.ui.lastScoredRecoveryDay
import com.noop.ui.recoveryCalibrationNights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * S0-07 acceptance: the registry returns what the v10 screens show. Each assertion compares the registry with the
 * v10 function the screen itself calls, over a fixed 40-day history, and the Synthesis bands with the frozen
 * `metrics_golden_v10/M-SYN.csv`.
 */
class V10MetricRegistryTest {

    /** These tests pin v10's own behaviour; the R9a corrections are tested in MetricsV2Test. */
    private val V10 = emptySet<V2Change>()

    private val start = LocalDate.of(2026, 1, 1)
    private fun key(i: Int) = start.plusDays(i.toLong()).toString()

    /** 40 days; day 25 is a gap (no row); day 39 (today) is unscored, so Charge carries from day 38. */
    private fun history(): List<DailyMetric> = (0 until 40).filter { it != 25 }.map { i ->
        val scored = i != 39
        DailyMetric(
            deviceId = "my-whoop",
            day = key(i),
            totalSleepMin = 380.0 + (i * 37 % 120),
            restingHr = if (scored) 52 + (i * 7 % 9) else null,
            avgHrv = if (scored) 55.0 + (i * 13 % 30) else null,
            recovery = if (scored) 20.0 + (i * 23 % 75) else null,
            strain = 30.0 + (i * 11 % 50),
            respRateBpm = if (scored) 14.0 + (i % 5) * 0.3 else null,
            spo2Pct = if (scored) 95.0 + (i % 4) else null,
        )
    }

    private val days = history()
    private val today = start.plusDays(39)
    private val rest = (0 until 38).filter { it != 25 }.associate { key(it) to 60.0 + (it * 17 % 40) }
    private val reg = V10MetricRegistry(V10MetricInputs(days, rest, emptyMap(), today, v2 = V10))

    @Test
    fun chargeMatchesStoredRecoveryOnEveryScoredDay() {
        for (d in days.filter { it.recovery != null }) {
            assertEquals(d.day, d.recovery, reg.value(MetricId.CHARGE, DayKey.of(d.day)).value)
        }
    }

    @Test
    fun todayCarriesTheSameRowTodayCarries() {
        val expected = lastScoredRecoveryDay(days, key(39), isToday = true, todayScored = false,
            isCalibrating = false, today = key(39))!!
        val v = reg.value(MetricId.CHARGE, DayKey(today))
        assertEquals(expected.recovery, v.value)
        assertEquals(DayKey.of(expected.day), v.carriedFrom)
        assertEquals(expected.avgHrv, reg.value(MetricId.HRV, DayKey(today)).value)
        assertEquals(expected.restingHr?.toDouble(), reg.value(MetricId.RESTING_HR, DayKey(today)).value)
        // A series never carries: today is a gap there.
        assertNull(reg.series(MetricId.CHARGE, DayKey(today), DayKey(today)).single().value)
    }

    @Test
    fun calibrationMatchesTheTodayCalibrationCount() {
        val young = days.take(2).map { it.copy(recovery = null) }
        val r = V10MetricRegistry(V10MetricInputs(young, today = LocalDate.parse(young.last().day), v2 = V10))
        val v = r.value(MetricId.CHARGE, DayKey.of(young.last().day))
        val n = recoveryCalibrationNights(young, hasRecovery = false, hrvBaselineEpoch = 0.0)!!
        assertEquals(Progress(n, Baselines.minNightsSeed), v.progress)
        assertEquals(Confidence.CALIBRATING, v.confidence)
        assertEquals(MissingReason.INSUFFICIENT_HISTORY, v.missing)
        assertNull(v.value)
    }

    @Test
    fun seriesHasOneEntryPerDayWithGapsKept() {
        val s = reg.series(MetricId.EFFORT, DayKey.of(key(20)), DayKey.of(key(30)))
        assertEquals(11, s.size)
        assertEquals((20..30).map { key(it) }, s.map { it.day.toString() })
        val gap = s.single { it.day.toString() == key(25) }
        assertNull(gap.value)
        assertEquals(MissingReason.NOT_SYNCED, gap.missing)
        assertEquals(days.single { it.day == key(24) }.strain, s[4].value)
    }

    @Test
    fun restMatchesTodaysFreshRestCarry() {
        // Today's call (TodayScreen restScoreForDay, #977): the newest night in the whole series, freshness-gated.
        fun todays(series: Map<String, Double>, k: String): Double? {
            val latest = series.entries.maxByOrNull { it.key }
            return freshRestScore(series[k], latest?.key, latest?.value, isTodaySelected = k == key(39), today = k)
        }
        for (i in listOf(10, 25, 37, 38, 39)) {
            val k = key(i)
            assertEquals(k, todays(rest, k), reg.value(MetricId.REST, DayKey.of(k)).value)
        }
        // A future-dated night (bad strap clock): Today carries it, so the registry does too (DIFFERENCES #20).
        val withFuture = rest + (key(41) to 71.0)
        val r = V10MetricRegistry(V10MetricInputs(days, withFuture, emptyMap(), today, v2 = V10))
        assertEquals(todays(withFuture, key(39)), r.value(MetricId.REST, DayKey.of(key(39))).value)
        assertEquals(71.0, r.value(MetricId.REST, DayKey.of(key(39))).value!!, 0.0)
    }

    @Test
    fun synthesisBandsMatchTheGoldenFixture() {
        val keys = listOf("depleted", "low", "steady", "strong", "peak")
        val csv = File(javaClass.classLoader!!.getResource("metrics_golden_v10/M-SYN.csv")!!.toURI()).readLines()
        for (line in csv.drop(1)) {
            val cols = line.split(",")
            val band = reg.band(MetricId.CHARGE, cols[0].toDouble())!!
            assertEquals(line, keys[cols[2].toInt()], band.key)
        }
        assertNull(reg.band(MetricId.EFFORT, 50.0))
    }

    @Test
    fun stressMatchesStressModelAndItsBand() {
        val model = StressModel.build(days, emptyMap())!!
        val v = reg.value(MetricId.STRESS, DayKey(today))
        assertEquals(model.score, v.value!!, 1e-12)
        assertEquals(DayKey.of(key(38)), v.carriedFrom)
        val expectedKey = when (StressBand.forScore(model.score)) {
            StressBand.Low -> "low"; StressBand.Medium -> "medium"; StressBand.High -> "high"
        }
        assertEquals(expectedKey, reg.band(MetricId.STRESS, model.score)!!.key)
        for (s in listOf(0.0, 0.999, 1.0, 1.999, 2.0, 3.0)) {
            val b = reg.band(MetricId.STRESS, s)!!
            assertEquals(StressBand.forScore(s).name.lowercase(), b.key)
        }
    }

    @Test
    fun normalRangeAgreesWithTheHealthTileBanding() {
        val day = DayKey(today)
        val history = VitalBands.calendarSeries(days.filter { it.day < key(39) }.map { it.day to it.avgHrv })
        val range = reg.normalRange(MetricId.HRV, day)!!
        assertEquals(NormalRange.Basis.PERSONAL, range.basis)
        var v = 20.0
        while (v < 140.0) {
            val tile = VitalBands.band(v, history, 40.0..120.0, Baselines.hrvCfg).band
            val inside = v >= range.low && v <= range.high
            // Skip values within float noise of an edge.
            if (kotlin.math.abs(v - range.low) > 1e-6 && kotlin.math.abs(v - range.high) > 1e-6) {
                assertEquals("v=$v", inside, tile == VitalBands.Band.IN_RANGE)
            }
            v += 0.25
        }
        val young = V10MetricRegistry(V10MetricInputs(days.take(5), today = LocalDate.parse(days[4].day), v2 = V10))
        val pop = young.normalRange(MetricId.HRV, DayKey.of(days[4].day))!!
        assertEquals(NormalRange.Basis.POPULATION, pop.basis)
        assertTrue(pop.low == 40.0 && pop.high == 120.0)
    }
}
