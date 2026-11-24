package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.data.DemoSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B2-4a-5: the numbers behind the board's "Sleep stages" card. */
class SleepRidgeStagesTest {

    private fun night(day: String, deep: Double?, rem: Double?, light: Double?, total: Double? = null, eff: Double? = null, dist: Int? = null) =
        DailyMetric("d", day, deepMin = deep, remMin = rem, lightMin = light, totalSleepMin = total, efficiency = eff, disturbances = dist)

    @Test
    fun awakeIsWhatEfficiencyImpliesElseSixMinutesPerDisturbance() {
        // 420 asleep at 90% efficiency: in bed 466.67, awake 46.67.
        assertEquals(420.0 / 0.9 - 420.0, awakeMinOf(night("2026-11-01", 80.0, 90.0, 250.0, total = 420.0, eff = 90.0))!!, 1e-9)
        assertEquals(30.0, awakeMinOf(night("2026-11-01", 80.0, 90.0, 250.0, total = 420.0, dist = 5))!!, 1e-9)
        assertEquals(0.0, awakeMinOf(night("2026-11-01", 80.0, 90.0, 250.0, total = 420.0))!!, 1e-9)
        assertNull(awakeMinOf(night("2026-11-01", null, null, null)))
    }

    @Test
    fun averageIsTheFourteenNightsBeforeTheShownOne() {
        // 20 nights with deep = day number; the shown night is the 20th, so the average is nights 6..19 (mean 12.5).
        val days = (1..20).map { night("2026-11-%02d".format(it), it.toDouble(), 90.0, 240.0, total = 400.0, eff = 0.9) }
        val a = stageAveragesBefore(days, "2026-11-20")!!
        assertEquals(12.5, a.deep!!, 1e-9)
        assertEquals(90.0, a.rem!!, 1e-9)
        assertEquals(400.0 / 0.9 - 400.0, a.awake!!, 1e-9)
        // Too few earlier nights: no average, so no difference is shown.
        assertNull(stageAveragesBefore(days.take(3), "2026-11-03"))
        assertNotNull(stageAveragesBefore(days.take(4), "2026-11-04"))
        // A night with no stage minutes is not a night with data.
        assertNull(stageAveragesBefore(listOf(night("2026-11-01", null, null, null), night("2026-11-02", 0.0, 0.0, 0.0)), null))
    }

    @Test
    fun wakesAreAwakeStretchesOfFiveMinutesInsideTheNight() {
        fun iv(stage: String, a: Int, b: Int) = StageInterval(stage, a * 60.0, b * 60.0)
        val night = listOf(
            iv("wake", 0, 10),     // awake before sleep: not a wake
            iv("light", 10, 60),
            iv("wake", 60, 66),    // 6 min: a wake
            iv("deep", 66, 120),
            iv("wake", 120, 123),  // 3 min: too short
            iv("rem", 123, 180),
            iv("wake", 180, 200),  // the final waking up: not a wake
        )
        assertEquals(1, wakeCount(night))
        assertEquals(0, wakeCount(emptyList()))
        assertEquals(0, wakeCount(listOf(iv("wake", 0, 30), iv("wake", 30, 60))))
    }

    @Test
    fun differenceHasASameBandAndSensibleTones() {
        assertEquals(4, stageDeltaMin(82.0, 78.0))
        assertNull(stageDeltaMin(80.0, 78.0))         // 2 minutes: the same night
        assertEquals(-14, stageDeltaMin(96.0, 110.0))
        assertNull(stageDeltaMin(96.0, null))
        assertEquals(DeltaTone.GOOD, deltaTone("deep", 4))
        assertEquals(DeltaTone.WATCH, deltaTone("rem", -14))
        assertEquals(DeltaTone.GOOD, deltaTone("awake", -8))
        assertEquals(DeltaTone.WATCH, deltaTone("awake", 8))
        assertEquals(DeltaTone.NEUTRAL, deltaTone("light", 12))
    }

    @Test
    fun theDemoNightIsTimedContiguousAndHasTwoWakesInsideIt() {
        val onset = 1_800_000_000L
        val json = DemoSeeder.timedStagesJson(onset, deep = 82.0, rem = 96.0, light = 226.0, inBedMin = 438.0)
        val segs = parsePersistedSegments(json)!!
        assertEquals(onset, segs.first().start)
        segs.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }       // no gaps, no overlaps
        assertEquals(438.0, (segs.last().end - onset) / 60.0, 0.1)                   // fills the whole time in bed
        val minutes = segs.groupBy { canonicalStage(it.stage) }.mapValues { (_, v) -> v.sumOf { it.end - it.start } / 60.0 }
        assertEquals(82.0, minutes.getValue("deep"), 0.1)
        assertEquals(96.0, minutes.getValue("rem"), 0.1)
        assertEquals(226.0, minutes.getValue("light"), 0.1)
        assertEquals(34.0, minutes.getValue("awake"), 0.1)
        val intervals = segs.map { StageInterval(it.stage, (it.start - onset).toDouble(), (it.end - onset).toDouble()) }
        assertEquals(2, wakeCount(intervals)) // two wakes inside the night; the third awake stretch closes it
        assertTrue(segs.size >= 8)
    }

    /** B2-4b-1: the board's typical ranges (percent of time in bed), labelled as typical, with its tone rules. */
    @Test
    fun typicalRangesAndTheirTones() {
        assertEquals(TypicalRange(4, 12), typicalRange("awake"))
        assertEquals(TypicalRange(18, 26), typicalRange("rem"))
        assertEquals(TypicalRange(38, 50), typicalRange("light"))
        assertEquals(TypicalRange(10, 18), typicalRange("deep"))
        assertEquals(RangeStatus.TYPICAL, rangeStatus("rem", 22))
        assertEquals(RangeStatus.TYPICAL, rangeStatus("rem", 18)) // the edges are inside
        assertEquals(RangeStatus.TYPICAL, rangeStatus("rem", 26))
        assertEquals(RangeStatus.BELOW, rangeStatus("rem", 17))
        assertEquals(RangeStatus.ABOVE, rangeStatus("awake", 13))
        // Awake above, Light off either side, Deep or REM below: the amber chip. Everything else is green.
        assertEquals(false, rangeStatusIsGood("awake", RangeStatus.ABOVE))
        assertEquals(true, rangeStatusIsGood("awake", RangeStatus.BELOW))
        assertEquals(false, rangeStatusIsGood("light", RangeStatus.ABOVE))
        assertEquals(false, rangeStatusIsGood("light", RangeStatus.BELOW))
        assertEquals(true, rangeStatusIsGood("light", RangeStatus.TYPICAL))
        assertEquals(false, rangeStatusIsGood("deep", RangeStatus.BELOW))
        assertEquals(true, rangeStatusIsGood("deep", RangeStatus.ABOVE))
    }

    /** B2-4b-1: the 30-night marker is each stage's share of the mean night, and the window is configurable. */
    @Test
    fun thirtyNightShareAndWindow() {
        val a = StageAverages(deep = 80.0, rem = 100.0, light = 220.0, awake = 40.0) // 440 in bed
        assertEquals(80.0 / 440 * 100, averageSharePct("deep", a)!!, 1e-9)
        assertEquals(40.0 / 440 * 100, averageSharePct("awake", a)!!, 1e-9)
        assertNull(averageSharePct("rem", StageAverages(80.0, null, 220.0, 40.0))) // a missing stage: no marker, no guess
        assertNull(averageSharePct("rem", null))
        // The window: 40 nights of deep = day number, shown night 41: last 30 (11..40) average 25.5, last 14 (27..40) 33.5.
        val days = (1..40).map { night("2026-%02d-%02d".format(1 + (it - 1) / 28, 1 + (it - 1) % 28), it.toDouble(), 90.0, 240.0, total = 400.0, eff = 0.9) }
        assertEquals(25.5, stageAveragesBefore(days, "2026-03-01", STAGE_COMPARE_NIGHTS)!!.deep!!, 1e-9)
        assertEquals(33.5, stageAveragesBefore(days, "2026-03-01")!!.deep!!, 1e-9)
    }

    @Test
    fun signedMinutesUsesATrueMinus() {
        assertEquals("+4m", signedMinutes(4))
        assertEquals("\u221221m", signedMinutes(-21))
    }
}
