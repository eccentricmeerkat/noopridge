package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

/** RS-3b: Readiness's formula (against an independent oracle), smoothing, card states and calendar. */
class ReadinessScoreTest {

    @Test
    fun matchesTheGoldenFixture() {
        val rows = javaClass.getResourceAsStream("/readiness_golden/readiness.csv")!!.bufferedReader().readLines().filterNot { it.startsWith("#") }
        assertEquals(25, rows.size)
        for (line in rows) {
            val f = line.split(",")
            val charge = f[0].toDouble()
            val effort = f[2].toDoubleOrNull() ?: 0.0
            val morning = ReadinessScore.morning(charge, f[1].toDouble())
            val target = ReadinessScore.effortTarget(charge)!!
            val now = ReadinessScore.now(morning, effort, target)
            assertEquals(line, f[3].toDouble(), morning, 1e-5)
            assertEquals(line, f[4].toInt(), target)
            assertEquals(line, f[5].toDouble(), now, 1e-5)
            assertEquals(line, f[6].toInt(), ReadinessScore.band(morning))
            assertEquals(line, f[7].toInt(), ReadinessScore.band(now))
        }
    }

    /** Charge 65, Rest 94, Strain 3.9 of 21: the worked example, at the chosen 80/20. */
    @Test
    fun theWorkedExampleReads70Then60() {
        val morning = ReadinessScore.morning(65.0, 94.0)
        assertEquals(70, morning.roundToInt())
        assertEquals(3, ReadinessScore.band(morning))
        val now = ReadinessScore.now(morning, 3.9 * 100 / 21, 67)
        assertEquals(60, now.roundToInt())
        assertEquals(2, ReadinessScore.band(now))
    }

    @Test
    fun theTargetIsThePlansTarget() {
        for (c in listOf(0.0, 33.0, 33.9, 34.0, 66.0, 66.9, 67.0, 100.0)) {
            assertEquals(planEffortCeiling(c, EffortScale.HUNDRED), ReadinessScore.effortTarget(c).toString())
        }
    }

    // MARK: - Smoothing

    private val min = 60_000L
    private fun step(prev: ReadinessShown?, reading: Double, at: Long, day: String = "d1", morning: Double = 80.0) =
        ReadinessSmoothing.step(prev, day, morning, reading, at)

    @Test
    fun aFirstReadingIsShownAsItIs() {
        val s = step(null, 69.0, 0)
        assertEquals(69.0, s.value, 0.0)
        assertEquals(2, s.band)
    }

    @Test
    fun theValueMovesHalfwayIn15MinutesHoweverOftenItIsRead() {
        val start = step(null, 80.0, 0)
        assertEquals(70.0, step(start, 60.0, 15 * min).value, 1e-9)
        var s = start
        for (m in 1..15) s = step(s, 60.0, m * min)
        assertEquals(70.0, s.value, 1e-9)
    }

    @Test
    fun theCategoryChangesOnlyTwoPointsPastAnEdge() {
        // Held Strong (3): 69 and 68 stay Strong, below 68 is Steady.
        assertEquals(3, ReadinessSmoothing.holdBand(3, 69.0))
        assertEquals(3, ReadinessSmoothing.holdBand(3, 68.0))
        assertEquals(2, ReadinessSmoothing.holdBand(3, 67.9))
        // Held Steady (2): 70 and 71.9 stay Steady, 72 is Strong.
        assertEquals(2, ReadinessSmoothing.holdBand(2, 71.9))
        assertEquals(3, ReadinessSmoothing.holdBand(2, 72.0))
        // A big move crosses several at once.
        assertEquals(0, ReadinessSmoothing.holdBand(4, 5.0))
        assertEquals(4, ReadinessSmoothing.holdBand(0, 99.0))
    }

    @Test
    fun aNewDayOrANewMorningScoreStartsAfresh() {
        val s = step(null, 80.0, 0)
        assertEquals(40.0, step(s, 40.0, min, day = "d2").value, 0.0)
        assertEquals(40.0, step(s, 40.0, min, morning = 50.0).value, 0.0)
        // A clock set backwards moves nothing.
        assertEquals(80.0, step(step(null, 80.0, 10 * min), 40.0, 0).value, 1e-9)
    }

    @Test
    fun theKnobStaysInsideTheHeldCategory() {
        assertEquals(70.0, sliderScoreIn(3, 69.0), 0.0)
        assertTrue(sliderScoreIn(2, 71.0) < 70.0)
        assertEquals(75.0, sliderScoreIn(3, 75.0), 0.0)
        assertEquals(100.0, sliderScoreIn(4, 100.0), 0.0)
    }

    // MARK: - The card

    private fun model(charge: Double? = 65.0, carried: String? = null, rest: Double? = 94.0, effort: Double? = 18.6, calibrating: Int? = null, today: Boolean = true) =
        readinessCardModel(charge, carried, rest, effort, calibrating, today)

    @Test
    fun noInputIsEverGuessed() {
        assertEquals(ReadinessCardModel.Calibrating(2), model(calibrating = 2))
        assertEquals(ReadinessCardModel.NoCharge, model(charge = null))
        assertEquals(ReadinessCardModel.NoRest, model(rest = null))
    }

    @Test
    fun todayIsLiveAndNoEffortReadsTheMorningScore() {
        val live = model() as ReadinessCardModel.Live
        assertEquals(67, live.target)
        assertEquals(60, live.reading.roundToInt())
        val early = model(effort = null) as ReadinessCardModel.Live
        assertEquals(early.morning, early.reading, 0.0)
    }

    @Test
    fun pastAndCarriedDaysShowTheMorningScoreWithoutTodaysEffort() {
        val past = model(today = false, carried = "2026-11-25") as ReadinessCardModel.Morning
        assertNull(past.carriedFrom)
        val carried = model(carried = "2026-11-25") as ReadinessCardModel.Morning
        assertEquals("2026-11-25", carried.carriedFrom)
        assertEquals(70, carried.morning.roundToInt())
    }

    // MARK: - The calendar

    private val today = LocalDate.of(2026, 11, 26)

    private fun registry(rows: List<DailyMetric>, rest: Map<String, Double>) =
        V10MetricRegistry(V10MetricInputs(days = rows.sortedBy { it.day }, restByDay = rest, today = today))

    @Test
    fun theCalendarColoursEachDayByTheSameMorningScoreAsTheCard() {
        // Back 0..9: Charge climbs by tens; Rest 94 on every day but the third, which has none.
        val rows = (0 until 10).map { DailyMetric("d", today.minusDays(it.toLong()).toString(), recovery = 5.0 + it * 10, totalSleepMin = null) }
        val rest = (0 until 10).filter { it != 2 }.associate { today.minusDays(it.toLong()).toString() to 94.0 }
        val cal = buildSynthesis(registry(rows, rest), today, readiness = true)
        val byDay = cal.cells.associate { it.date to it.band }
        for (i in 0 until 10) {
            val expected = if (i == 2) null else ReadinessScore.band(ReadinessScore.morning(5.0 + i * 10, 94.0))
            assertEquals("back $i", expected, byDay[today.minusDays(i.toLong())])
        }
        assertEquals(9, cal.tracked)
        val bands = byDay.values.filterNotNull()
        assertEquals(100, cal.strongOrPeak + cal.steady + cal.lowOrDepleted)
        assertEquals(bands.count { it == 2 } * 100 / 9.0, cal.steady.toDouble(), 1.0)
    }

    @Test
    fun readinessLeadsCoreMetricsOnlyWhileItIsOn() {
        assertEquals(listOf(GuideScore.READINESS, GuideScore.CHARGE, GuideScore.EFFORT, GuideScore.REST), coreScores(readiness = true))
        assertEquals(listOf(GuideScore.CHARGE, GuideScore.EFFORT, GuideScore.REST), coreScores(readiness = false))
    }
}
