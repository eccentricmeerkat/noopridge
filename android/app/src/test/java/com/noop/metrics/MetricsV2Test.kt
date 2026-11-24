package com.noop.metrics

import com.noop.analytics.ReadinessEngine
import com.noop.data.DailyMetric
import com.noop.ui.hoursVsNeededPct
import com.noop.ui.sleepTabNeedMin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * R9a (`metricsV2`): each correction is off until approved, off reproduces v10, and on gives the value its decision
 * describes (releases/R9a/R9a_PLAN.md).
 */
class MetricsV2Test {

    private val today = LocalDate.of(2026, 3, 31)
    private fun key(back: Int) = today.minusDays(back.toLong()).toString()

    /** 40 nights ending today, asleep 380–499 min, every vital present. */
    private fun history(): List<DailyMetric> = (39 downTo 0).map { back ->
        val i = 39 - back
        DailyMetric(
            deviceId = "my-whoop", day = key(back), totalSleepMin = 380.0 + (i * 37 % 120),
            restingHr = 52 + (i * 7 % 9), avgHrv = 55.0 + (i * 13 % 30), recovery = 20.0 + (i * 23 % 75),
            strain = 30.0 + (i * 11 % 50), respRateBpm = 14.0 + (i % 5) * 0.3, skinTempDevC = (i % 7 - 3) * 0.1,
        )
    }

    private fun registry(v2: Set<V2Change>, days: List<DailyMetric> = history(), age: Int? = 40, rest: Map<String, Double> = emptyMap()) =
        V10MetricRegistry(V10MetricInputs(days = days, restByDay = rest, today = today, age = age, v2 = v2))

    // MARK: - the switch

    @Test
    fun everyChangeIsApprovedExceptTheOnesAwaitingReview() {
        // The owner approved V2-1 to V2-8 (R9a), V2-9 (R9c) and V2-10 (R9d) line by line (2026-11-14). V2-11 (SQ5, the nap
        // off tonight's target) waits for its own "V2-11 ok".
        assertEquals(V2Change.entries.toSet() - V2Change.NAP_PLAN_TARGET, MetricsV2.APPROVED)
        assertEquals(MetricsV2.APPROVED, V10MetricInputs(days = emptyList(), today = today).v2)
    }

    @Test
    fun theNeedReadersRequireTheOneNeed() {
        assertFalse(MetricsV2.isOn(V2Change.PLAN_SLEEP_HALF, setOf(V2Change.PLAN_SLEEP_HALF)))
        assertFalse(MetricsV2.isOn(V2Change.SLEPT_ON_TARGET, setOf(V2Change.SLEPT_ON_TARGET)))
        assertTrue(MetricsV2.isOn(V2Change.PLAN_SLEEP_HALF, setOf(V2Change.PLAN_SLEEP_HALF, V2Change.ONE_SLEEP_NEED)))
        assertTrue(MetricsV2.isOn(V2Change.NORMAL_BAND_60, setOf(V2Change.NORMAL_BAND_60)))
    }

    @Test
    fun everyChangeOffIsV10() {
        val off = registry(emptySet())
        val v10 = V10MetricRegistry(V10MetricInputs(days = history(), today = today, age = 40, v2 = emptySet()))
        for (id in MetricId.entries) {
            for (back in 0..35) {
                val d = DayKey.of(key(back))
                assertEquals("$id $d", v10.value(id, d), off.value(id, d))
            }
        }
        for (id in listOf(MetricId.HRV, MetricId.RESTING_HR, MetricId.RESP_RATE, MetricId.SPO2, MetricId.SKIN_TEMP_DEV)) {
            assertEquals(id.name, v10.normalRange(id, DayKey(today)), off.normalRange(id, DayKey(today)))
        }
    }

    // MARK: - V2-1 · one sleep need

    @Test
    fun oneNeedIsPersonalizedOverTheTwentyOneNightsEndingThatNight() {
        val d = LocalDate.of(2026, 3, 1)
        fun nights(vararg h: Double) = h.mapIndexed { i, x -> d.minusDays(i.toLong()).toString() to (x * 60.0) }.toMap()
        // Fewer than 7 nights: the population default, 8 h; under 18 the floor is 9 h.
        assertEquals(8.0, oneSleepNeedHours(nights(7.0, 7.0), d, 40), 1e-9)
        assertEquals(9.0, oneSleepNeedHours(nights(7.0, 7.0), d, 16), 1e-9)
        // A short sleeper never reads below the 8 h floor.
        assertEquals(8.0, oneSleepNeedHours(nights(*DoubleArray(21) { 7.0 }), d, 40), 1e-9)
        // P75 of 7, 7.5, 8, 8.5, 9, 9, 9, 9 = 9.0; capped at 9.5.
        assertEquals(9.0, oneSleepNeedHours(nights(7.0, 7.5, 8.0, 8.5, 9.0, 9.0, 9.0, 9.0), d, 40), 1e-9)
        assertEquals(9.5, oneSleepNeedHours(nights(*DoubleArray(10) { 11.0 }), d, 40), 1e-9)
        // The 22nd night back is outside the window.
        val withOld = nights(*DoubleArray(21) { 7.0 }) + (d.minusDays(21).toString() to 12.0 * 60.0)
        assertEquals(8.0, oneSleepNeedHours(withOld, d, 40), 1e-9)
        // A night later than the day is not counted either.
        val withLater = nights(*DoubleArray(8) { 8.5 }) + (d.plusDays(1).toString() to 11.0 * 60.0)
        assertEquals(8.5, oneSleepNeedHours(withLater, d, 40), 1e-9)
    }

    @Test
    fun hoursVsNeededAndDebtMeasureAgainstTheOneNeed() {
        val days = history()
        val on = registry(setOf(V2Change.ONE_SLEEP_NEED), days)
        val off = registry(emptySet(), days)
        val d = DayKey(today)
        val asleep = days.last().totalSleepMin
        val need = oneSleepNeedHours(days.associate { it.day to it.totalSleepMin }, today, 40)
        assertEquals(need, on.value(MetricId.SLEEP_NEED, d).value!!, 1e-9)
        assertEquals(hoursVsNeededPct(asleep, need * 60.0)!!, on.value(MetricId.HOURS_VS_NEEDED, d).value!!, 1e-9)
        assertEquals(hoursVsNeededPct(asleep, sleepTabNeedMin(days))!!, off.value(MetricId.HOURS_VS_NEEDED, d).value!!, 1e-9)
        assertNotNull(on.value(MetricId.SLEEP_DEBT, d).value)
        // The v10 needs stay readable side by side (the inspector).
        assertEquals(off.value(MetricId.SLEEP_NEED_SLEEP_TAB, d), on.value(MetricId.SLEEP_NEED_SLEEP_TAB, d))
    }

    @Test
    fun anImportedNeedNoLongerWinsWithTheOneNeed() {
        // R9a-f (owner, 2026-11-14): with V2-1 on, every surface shows the one need, imported days included.
        fun reg(v2: Set<V2Change>) = V10MetricRegistry(
            V10MetricInputs(days = history(), today = today, age = 40, importedNeedMin = mapOf(key(0) to 500.0), v2 = v2),
        )
        val need = oneSleepNeedHours(history().associate { it.day to it.totalSleepMin }, today, 40) * 60.0
        assertEquals(hoursVsNeededPct(history().last().totalSleepMin, need)!!, reg(setOf(V2Change.ONE_SLEEP_NEED)).value(MetricId.HOURS_VS_NEEDED, DayKey(today)).value!!, 1e-9)
        assertEquals(hoursVsNeededPct(history().last().totalSleepMin, 500.0)!!, reg(emptySet()).value(MetricId.HOURS_VS_NEEDED, DayKey(today)).value!!, 1e-9)
    }

    // MARK: - V2-4 · the 60-night normal band

    @Test
    fun theBandIsPersonalFromNightFourteen() {
        assertNull(normalBand60(List(13) { 50.0 }, 1.0))
        assertNull("untracked nights don't count", normalBand60(List(13) { 50.0 } + List(20) { null }, 1.0))
        assertNotNull(normalBand60(List(14) { 50.0 }, 1.0))
    }

    @Test
    fun constantValuesGetTheMinimumWidth() {
        val rhr = normalBand60(List(20) { 50.0 }, normalBandMinWidth(MetricId.RESTING_HR))!!
        assertEquals(49.5, rhr.low, 1e-9)
        assertEquals(50.5, rhr.high, 1e-9)
        val hrv = normalBand60(List(20) { 60.0 }, normalBandMinWidth(MetricId.HRV))!!
        assertEquals(59.0, hrv.low, 1e-9)
        assertEquals(61.0, hrv.high, 1e-9)
    }

    @Test
    fun anOutlierIsDroppedBeforeTheBandIsDrawn() {
        val xs = List(30) { if (it % 2 == 0) 50.0 else 52.0 } + 200.0
        val band = normalBand60(xs, 1.0)!!
        assertEquals(51.0, band.mean!!, 1e-9)
        val sd = kotlin.math.sqrt(30.0 / 29.0)
        assertEquals(sd, band.sd!!, 1e-9)
        assertEquals(51.0 - sd, band.low, 1e-9)
        assertEquals(31, band.nights)
    }

    @Test
    fun onlyTheLastSixtyTrackedNightsCount() {
        val band = normalBand60(List(10) { 100.0 } + List(60) { if (it % 2 == 0) 49.0 else 51.0 }, 1.0)!!
        assertEquals(50.0, band.mean!!, 1e-9)
        assertEquals(60, band.nights)
    }

    @Test
    fun theRegistryServesTheSixtyNightBandBeforeTheDay() {
        val days = history()
        val on = registry(setOf(V2Change.NORMAL_BAND_60), days)
        val d = DayKey(today)
        val expected = normalBand60(days.filter { it.day < today.toString() }.map { it.restingHr?.toDouble() }, 1.0)
        assertEquals(expected, on.normalRange(MetricId.RESTING_HR, d))
        assertNotNull(on.normalRange(MetricId.SKIN_TEMP_DEV, d))
        // Under 14 tracked nights: v10's population range, with the tracked count.
        val early = registry(setOf(V2Change.NORMAL_BAND_60), days.takeLast(10))
        val pop = early.normalRange(MetricId.HRV, d)!!
        assertEquals(NormalRange.Basis.POPULATION, pop.basis)
        assertEquals(9, pop.nights)
        assertNull("no population range for skin temperature", early.normalRange(MetricId.SKIN_TEMP_DEV, d))
        // SpO₂ keeps v10's rule.
        assertEquals(registry(emptySet(), days).normalRange(MetricId.SPO2, d), on.normalRange(MetricId.SPO2, d))
    }

    // MARK: - V2-5 · a past day's load ratio

    @Test
    fun aPastDaysLoadRatioSeesOnlyItsOwnHistory() {
        val days = history()
        val past = key(10)
        val on = registry(setOf(V2Change.PAST_DAY_LOAD), days).value(MetricId.ACWR, DayKey.of(past)).value
        val expected = ReadinessEngine.evaluate(days.filter { it.day <= past }, today = past).acwr
        assertEquals(expected, on)
        val off = registry(emptySet(), days).value(MetricId.ACWR, DayKey.of(past)).value
        assertEquals(ReadinessEngine.evaluate(days, today = past).acwr, off)
    }

    // MARK: - V2-6 · Rest carry bound

    @Test
    fun todaysRestNeverCarriesAFutureNight() {
        val rest = mapOf(key(1) to 70.0, today.plusDays(3).toString() to 90.0)
        val days = history().dropLast(1)
        val off = registry(emptySet(), days, rest = rest).value(MetricId.REST, DayKey(today))
        val on = registry(setOf(V2Change.REST_CARRY_BOUND), days, rest = rest).value(MetricId.REST, DayKey(today))
        assertEquals(90.0, off.value)
        assertEquals(70.0, on.value)
        assertEquals(DayKey.of(key(1)), on.carriedFrom)
    }

    // MARK: - V2-7 · no assumed age

    @Test
    fun noAgeMeansTheAgeReadingsNeedOne() {
        val weekly = mapOf("vo2max_est" to mapOf(key(3) to 44.0), "vitality" to mapOf(key(3) to 61.0),
            "fitness_age" to mapOf(key(3) to 35.0), "body_age" to mapOf(key(3) to 33.0))
        fun reg(v2: Set<V2Change>) = V10MetricRegistry(
            V10MetricInputs(days = history(), today = today, age = null, computedSeries = weekly, v2 = v2),
        )
        val d = DayKey(today)
        val on = reg(setOf(V2Change.NO_ASSUMED_AGE))
        for (id in listOf(MetricId.VO2_MAX, MetricId.FITNESS_AGE, MetricId.BODY_AGE, MetricId.MAX_HR)) {
            assertNull(id.name, on.value(id, d).value)
            assertEquals(id.name, MissingReason.NEEDS_AGE, on.value(id, d).missing)
        }
        assertEquals(61.0, on.value(MetricId.VITALITY, d).value)
        assertEquals(44.0, reg(emptySet()).value(MetricId.VO2_MAX, d).value)
    }

    // MARK: - V2-8 · one logical day (J-02)

    private val london = ZoneId.of("Europe/London")
    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    @Test
    fun theDayRollsAtFourLocal() {
        assertEquals(LocalDate.of(2026, 6, 9), dayKeyFor(at(london, 2026, 6, 10, 3, 59), london).date)
        assertEquals(LocalDate.of(2026, 6, 10), dayKeyFor(at(london, 2026, 6, 10, 4, 0), london).date)
        assertEquals(LocalDate.of(2026, 6, 10), dayKeyFor(at(london, 2026, 6, 10, 23, 59), london).date)
    }

    @Test
    fun dstMorningsKeepOneKeyPerDay() {
        // Spring forward, 29 Mar 2026 (01:00 → 02:00): 04:30 is the new day, by the wall clock.
        assertEquals(LocalDate.of(2026, 3, 29), dayKeyFor(at(london, 2026, 3, 29, 4, 30), london).date)
        assertEquals(LocalDate.of(2026, 3, 28), dayKeyFor(at(london, 2026, 3, 29, 3, 30), london).date)
        // Fall back, 25 Oct 2026 (02:00 → 01:00): 01:30 happens twice and both are the 24th; 03:30 is still the 24th.
        val first = LocalDateTime.of(2026, 10, 25, 1, 30).atZone(london).withEarlierOffsetAtOverlap().toInstant()
        val second = LocalDateTime.of(2026, 10, 25, 1, 30).atZone(london).withLaterOffsetAtOverlap().toInstant()
        assertEquals(LocalDate.of(2026, 10, 24), dayKeyFor(first, london).date)
        assertEquals(LocalDate.of(2026, 10, 24), dayKeyFor(second, london).date)
        assertEquals(LocalDate.of(2026, 10, 24), dayKeyFor(at(london, 2026, 10, 25, 3, 30), london).date)
        assertEquals(LocalDate.of(2026, 10, 25), dayKeyFor(at(london, 2026, 10, 25, 4, 0), london).date)
    }

    @Test
    fun aSampleKeepsTheZoneItWasRecordedIn() {
        val dubai = ZoneId.of("Asia/Dubai")
        // 02:00 in Dubai on 12 Jun is 23:00 in London on 11 Jun; both belong to the 11th.
        val sample = at(dubai, 2026, 6, 12, 2, 0)
        assertEquals(LocalDate.of(2026, 6, 11), dayKeyFor(sample, dubai).date)
        // An overnight flight: the night's wake at 07:10 London time is the 12th, one day, wherever it began.
        assertEquals(LocalDate.of(2026, 6, 12), dayKeyFor(at(london, 2026, 6, 12, 7, 10), london).date)
        assertEquals(LocalDate.of(2026, 6, 12), dayKeyFor(at(dubai, 2026, 6, 12, 10, 10), dubai).date)
    }

    // MARK: - J-06 · why Blood Oxygen is blank (R9b)

    @Test
    fun aBlankBloodOxygenSaysWhy() {
        val noSpo2 = history().map { it.copy(spo2Pct = null) }
        fun reason(unsupported: Boolean, estimateOn: Boolean, candidate: Map<String, Double> = emptyMap()) =
            V10MetricRegistry(
                V10MetricInputs(days = noSpo2, today = today, spo2DeviceUnsupported = unsupported,
                    spo2EstimateOn = estimateOn, spo2CandidateByDay = candidate),
            ).value(MetricId.SPO2, DayKey(today))
        assertEquals(MissingReason.DEVICE_UNSUPPORTED, reason(unsupported = true, estimateOn = true).missing)
        assertEquals(MissingReason.ESTIMATOR_OFF, reason(unsupported = false, estimateOn = false).missing)
        assertEquals(MissingReason.NOT_SYNCED, reason(unsupported = false, estimateOn = true).missing)
        // A strap estimate is a value, flagged as one, so there is no reason to give.
        val est = reason(unsupported = false, estimateOn = true, candidate = mapOf(key(0) to 96.0))
        assertEquals(96.0, est.value!!, 0.0)
        assertEquals(ValueNote.STRAP_ESTIMATE, est.provenanceNote)
        assertNull(est.missing)
    }
}
