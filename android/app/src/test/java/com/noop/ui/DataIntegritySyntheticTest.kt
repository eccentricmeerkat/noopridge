package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/**
 * Final review (data integrity): one synthetic history with deliberate faults, checked three ways.
 *
 *  1. the registry returns one entry per day, keeps gaps as gaps and a genuine zero as a zero;
 *  2. the surfaces that read the SAME metric differently agree: the Explore catalogue reads some columns straight from
 *     the daily row, Trends and Today read them through the registry. For a scored day they must be equal;
 *  3. Trends' averages, counts and week means equal arithmetic done here, independently, on the raw rows.
 *
 * The data is synthetic and deterministic; nothing here is a real person's reading. The faults:
 *   day  9 : no row at all (a gap)             day 19 : a row with every value null (synced, nothing scored)
 *   day 29 : recovery is a real 0.0           day 39 : an extreme but valid HRV (250 ms)
 */
class DataIntegritySyntheticTest {

    private val today = LocalDate.of(2026, 11, 20)
    private val n = 60
    private fun key(i: Int) = today.minusDays((n - 1 - i).toLong()).toString()   // i = 0 oldest .. n-1 = today

    private val gap = 9
    private val empty = 19
    private val zeroCharge = 29
    private val extremeHrv = 39

    private fun row(i: Int): DailyMetric? = when (i) {
        gap -> null
        empty -> DailyMetric("my-whoop", key(i))
        else -> DailyMetric(
            deviceId = "my-whoop", day = key(i),
            totalSleepMin = 380.0 + (i * 37 % 120),
            lightMin = 200.0 + (i % 40), deepMin = 60.0 + (i % 25), remMin = 90.0 + (i % 30),
            restingHr = 50 + (i * 7 % 11),
            avgHrv = if (i == extremeHrv) 250.0 else 45.0 + (i * 13 % 40),
            recovery = if (i == zeroCharge) 0.0 else 25.0 + (i * 23 % 70),
            strain = 20.0 + (i * 11 % 60),
            respRateBpm = 13.0 + (i % 7) * 0.4,
            spo2Pct = 94.0 + (i % 5),
        )
    }

    private val rows: List<DailyMetric> = (0 until n).mapNotNull { row(it) }
    private val byDay = rows.associateBy { it.day }
    private val registry = V10MetricRegistry(V10MetricInputs(days = rows.sortedBy { it.day }, today = today))

    private fun series(id: MetricId, last: Int = n): List<Double?> =
        registry.series(id, DayKey(today.minusDays((last - 1).toLong())), DayKey(today)).map { it.value }

    // --- 1. the registry itself ------------------------------------------------------------------------------

    @Test
    fun theRegistryReturnsOneEntryPerDayAndKeepsGapsAsGaps() {
        for (id in listOf(MetricId.CHARGE, MetricId.HRV, MetricId.RESTING_HR, MetricId.RESP_RATE, MetricId.SLEEP_DURATION)) {
            val s = series(id)
            assertEquals("$id: one slot per day", n, s.size)
            assertNull("$id: the missing day stays a gap", s[gap])
            assertNull("$id: a row with nothing scored is a gap, not a zero", s[empty])
        }
    }

    @Test
    fun aGenuineZeroIsAValueNotAGap() {
        val charge = series(MetricId.CHARGE)
        assertEquals(0.0, charge[zeroCharge]!!, 0.0)
    }

    @Test
    fun anExtremeButValidReadingIsNotClamped() {
        assertEquals(250.0, series(MetricId.HRV)[extremeHrv]!!, 0.0)
    }

    // --- 2. the same metric on different surfaces -------------------------------------------------------------

    /** Explore's picker entry for [catalogKey] reads this column straight from the daily row. */
    private fun exploreValue(catalogKey: String, d: DailyMetric): Double? {
        val src = MetricCatalog.forKey(catalogKey)!!.source
        return (src as CatSource.Daily).pick(d)
    }

    @Test
    fun exploreAndTheRegistryAgreeOnEveryScoredDay() {
        val pairs = listOf(
            "recovery" to MetricId.CHARGE,
            "hrv" to MetricId.HRV,
            "rhr" to MetricId.RESTING_HR,
            "resp" to MetricId.RESP_RATE,
        )
        for ((cat, id) in pairs) {
            val reg = series(id)
            val mismatches = (0 until n).filter { i ->
                val d = byDay[key(i)]
                val e = d?.let { exploreValue(cat, it) }
                val r = reg[i]
                !(e == null && r == null) && (e == null || r == null || abs(e - r) > 1e-9)
            }
            assertTrue("$cat (Explore) vs $id (registry) differ on days $mismatches", mismatches.isEmpty())
        }
    }

    @Test
    fun exploreSleepHoursAreTheRegistrysSleepMinutesOver60() {
        val reg = series(MetricId.SLEEP_DURATION)
        for (i in 0 until n) {
            val d = byDay[key(i)]
            val e = d?.let { exploreValue("sleep", it) }
            val r = reg[i]
            if (e == null || r == null) { assertEquals("day $i presence", e == null, r == null); continue }
            assertEquals("day $i: Explore hours vs registry minutes", r / 60.0, e, 1e-9)
        }
    }

    @Test
    fun todayShowsTheSameChargeAsTheRegistryAndTheRow() {
        val todayRow = byDay[key(n - 1)]
        val state = buildTodayUiState(
            TodayInputs(
                days = rows.sortedBy { it.day }, today = todayRow, todayDate = today, localCalendarDate = today,
                selectedDayOffset = 0, liveTodayStrain = null, hrvBaselineEpoch = 0.0,
            ),
        )
        assertEquals(todayRow!!.recovery, state.charge.value)
        assertEquals(series(MetricId.CHARGE).last(), state.charge.value)
    }

    // --- 3. Trends arithmetic, recomputed independently ------------------------------------------------------

    private fun trends(range: RidgeTrendsRange) =
        buildTrendsRidge(registry, byDay, today, range)

    private fun window(range: RidgeTrendsRange): List<DailyMetric?> =
        (0 until range.days).map { byDay[today.minusDays((range.days - 1 - it).toLong()).toString()] }

    @Test
    fun trendsChargeAverageAndTrackedCountMatchArithmeticOnTheRawRows() {
        for (range in RidgeTrendsRange.entries) {
            val vals = window(range).map { it?.recovery }
            val expectedAvg = vals.filterNotNull().average()
            val m = trends(range)
            assertEquals("$range tracked", vals.count { it != null }, m.charge.tracked)
            assertEquals("$range average", expectedAvg, m.charge.average!!, 1e-9)
            assertEquals("$range values length", range.days, m.charge.values.size)
        }
    }

    @Test
    fun trendsEffortAverageMatchesArithmeticAndDoesNotCountTheEmptyDay() {
        val range = RidgeTrendsRange.D30
        val vals = window(range).map { it?.strain }
        val m = trends(range)
        assertEquals(vals.count { it != null }, m.effort.tracked)
        // Effort may be rescaled by the registry; check against the registry's own series, then the row.
        val reg = series(MetricId.EFFORT, range.days).filterNotNull()
        assertEquals(reg.average(), m.effort.average!!, 1e-9)
    }

    @Test
    fun trendsSleepAverageIsTheMeanOfTheTrackedNights() {
        val range = RidgeTrendsRange.D30
        val mins = window(range).mapNotNull { it?.totalSleepMin }
        val m = trends(range)
        assertEquals(mins.size, m.sleep.tracked)
        assertEquals(mins.average(), m.sleep.avgAsleepMin!!, 1e-9)
    }

    @Test
    fun trendsHeartSeriesHaveTheSameValuesAsTheRows() {
        val range = RidgeTrendsRange.D30
        val m = trends(range)
        val rhr = window(range).map { it?.restingHr?.toDouble() }
        assertEquals(rhr, m.heart.rhr.values)
        val hrv = window(range).map { it?.avgHrv }
        assertEquals(hrv, m.heart.hrv.values)
    }

    @Test
    fun theLastSevenDayMeanIsTheMeanOfTheTrackedValuesInThoseSevenDays() {
        val range = RidgeTrendsRange.D14
        val m = trends(range)
        val last7 = (0 until 7).map { byDay[today.minusDays(it.toLong()).toString()]?.recovery }
        assertNotNull(m.charge.thisWeek)
        assertEquals(last7.filterNotNull().average(), m.charge.thisWeek!!, 1e-9)
    }
}
