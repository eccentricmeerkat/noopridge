package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.data.SleepSession
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.metrics.V2Change
import com.noop.metrics.MetricsV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** RS-3c: the Sleep score's parts (against an independent oracle), its bed and wake times, and the registry metric. */
class SleepScoreTest {

    @Test
    fun matchesTheGoldenFixture() {
        val rows = javaClass.getResourceAsStream("/sleep_score_golden/sleep_score.csv")!!.bufferedReader().readLines().filterNot { it.startsWith("#") }
        assertEquals(7, rows.size)
        for (line in rows) {
            val f = line.split(",")
            val bed = f[3].split(";").map { it.toDouble() }
            val wake = f[4].split(";").map { it.toDouble() }
            val nights = bed.indices.map { SleepScore.BedWake(bed[it], wake[it]) }
            val consistency = SleepScore.consistency(nights.last(), nights.dropLast(1))
            val parts = SleepScore.parts(f[0].toDouble(), f[1].toDouble(), f[2].toDouble(), consistency)!!
            assertEquals(line, f[5].toDouble(), parts.hours, 1e-5)
            assertEquals(line, f[6].toDouble(), parts.consistency, 1e-5)
            assertEquals(line, f[7].toDouble(), parts.efficiency, 1e-5)
            assertEquals(line, f[8].toDouble(), parts.score, 1e-5)
        }
    }

    @Test
    fun consistencyNeedsTwoOfThePreviousThreeNights() {
        val n = SleepScore.BedWake(600.0, 780.0)
        assertNull(SleepScore.consistency(n, emptyList()))
        assertNull(SleepScore.consistency(n, listOf(n)))
        assertEquals(100.0, SleepScore.consistency(n, listOf(n, n))!!, 1e-9)
    }

    @Test
    fun noPartIsEverGuessed() {
        assertNull(SleepScore.parts(null, 480.0, 90.0, 80.0))
        assertNull(SleepScore.parts(0.0, 480.0, 90.0, 80.0))
        assertNull(SleepScore.parts(420.0, null, 90.0, 80.0))
        assertNull(SleepScore.parts(420.0, 480.0, null, 80.0))
        assertNull(SleepScore.parts(420.0, 480.0, 90.0, null))
    }

    private val zone = ZoneId.of("Europe/London")
    private fun ts(text: String) = LocalDateTime.parse(text).atZone(zone).toEpochSecond()

    @Test
    fun timesEitherSideOfMidnightAreCloseAndAClockChangeIsReadOnTheClock() {
        val before = SleepScore.bedWake(ts("2026-11-10T23:50:00"), ts("2026-11-11T07:00:00"), zone)
        val after = SleepScore.bedWake(ts("2026-11-12T00:10:00"), ts("2026-11-12T07:00:00"), zone)
        assertEquals(20.0, after.bedMin - before.bedMin, 0.0)
        assertEquals(before.wakeMin, after.wakeMin, 0.0)
        // 25 October 2026: the UK clocks go back. 23:00 to 07:00 reads the same clock times as any other night.
        val change = SleepScore.bedWake(ts("2026-10-24T23:00:00"), ts("2026-10-25T07:00:00"), zone)
        val plain = SleepScore.bedWake(ts("2026-10-20T23:00:00"), ts("2026-10-21T07:00:00"), zone)
        assertEquals(plain, change)
    }

    @Test
    fun bedAndWakeComeFromTheBridgedMainNightNotANap() {
        val zone = ZoneId.systemDefault()
        fun at(t: String) = LocalDateTime.parse(t).atZone(zone).toEpochSecond()
        fun s(a: String, b: String) = SleepSession(deviceId = "d", startTs = at(a), endTs = at(b))
        val sleeps = listOf(
            s("2026-11-09T23:00:00", "2026-11-10T03:00:00"), s("2026-11-10T03:20:00", "2026-11-10T07:30:00"),
            s("2026-11-10T14:00:00", "2026-11-10T14:40:00"),
        )
        val day = bedWakeByWakeDay(sleeps, null, zone).getValue("2026-11-10")
        assertEquals(11 * 60.0, day.bedMin, 0.0)   // 23:00 is 11 h after noon
        assertEquals((13 * 60 + 30).toDouble(), day.wakeMin, 0.0)   // 07:30 is 13 h 30 after 18:00
    }

    // MARK: - The registry metric

    private val today = LocalDate.of(2026, 11, 26)
    private fun key(back: Int) = today.minusDays(back.toLong()).toString()

    private fun inputs(v2: Set<V2Change> = MetricsV2.APPROVED, nights: Set<Int> = (0..29).toSet(), tonightAsleep: Double = 420.0) =
        V10MetricInputs(
            days = nights.sorted().reversed().map {
                DailyMetric("d", key(it), totalSleepMin = if (it == 0) tonightAsleep else 450.0, efficiency = 0.92, strain = 40.0)
            },
            today = today, age = 40, v2 = v2,
            bedWakeByDay = nights.associate { key(it) to SleepScore.BedWake(660.0, 780.0) },
        )

    private fun score(i: V10MetricInputs) = V10MetricRegistry(i).value(MetricId.SLEEP_SCORE, DayKey(today)).value

    @Test
    fun theRegistryServesTheScoreFromTheNightsItAlreadyHolds() {
        val i = inputs()
        val parts = V10MetricRegistry(i).sleepScoreParts(today)!!
        assertEquals(100.0, parts.consistency, 1e-9)
        assertEquals(92.0, parts.efficiency, 1e-9)
        // Need: 8 h floor, no extra for a flat Effort, plus the payback of the debt carried in.
        assertTrue(parts.hours < 420.0 / 480.0 * 100.0 + 1e-9 && parts.hours > 420.0 / 540.0 * 100.0 - 1e-9)
        assertEquals(parts.score, score(i)!!, 1e-9)
    }

    @Test
    fun aNightNeverRaisesItsOwnNeed() {
        // Ten hours tonight, or four: the need it is measured against is the same.
        fun need(asleep: Double) = asleep / (V10MetricRegistry(inputs(tonightAsleep = asleep)).sleepScoreParts(today)!!.hours / 100.0)
        assertEquals(need(240.0), need(300.0), 1e-6)
    }

    @Test
    fun itIsNullWhenOffOrWhenTooFewNightsBefore() {
        assertNull(score(inputs(v2 = MetricsV2.APPROVED - V2Change.SLEEP_SCORE)))
        assertNull(score(inputs(nights = setOf(0, 1) + (4..29))))
        assertNotNull(score(inputs(nights = setOf(0, 1, 3) + (4..29))))
    }

    @Test
    fun itChangesNoOtherNumber() {
        val with = V10MetricRegistry(inputs())
        val without = V10MetricRegistry(inputs(v2 = MetricsV2.APPROVED - V2Change.SLEEP_SCORE))
        for (id in MetricId.entries - MetricId.SLEEP_SCORE) {
            assertEquals(id.name, without.value(id, DayKey(today)), with.value(id, DayKey(today)))
        }
    }

    // MARK: - Insights, Key Metrics, the guide

    @Test
    fun insightsAndKeyMetricsOfferIt() {
        assertEquals(MetricId.SLEEP_SCORE, (MetricCatalog.forKey("sleep_score")!!.source as CatSource.Registry).id)
        assertEquals("sleep_score", keyMetricEntry(KeyMetric.SLEEP_SCORE)!!.key)
        assertTrue(KeyMetric.SLEEP_SCORE !in KeyMetric.defaultOrder)
    }

    @Test
    fun theGuideListsItAndTheCardsLinkOpensItOnce() {
        // RS-3e: its own tab, beside Core Metrics rather than under it.
        assertEquals(listOf(GuideTab.CORE, GuideTab.SLEEP_SCORE), guideTabs(sleepScore = true).take(2))
        assertTrue(GuideTab.SLEEP_SCORE !in guideTabs(sleepScore = false))
        assertEquals(GuideTab.SLEEP_SCORE, GuideScore.SLEEP_SCORE.tab())
        assertTrue(GuideScore.SLEEP_SCORE !in coreScores(readiness = true))
        ScoringGuideLink.pending = GuideScore.SLEEP_SCORE
        assertEquals(GuideScore.SLEEP_SCORE, ScoringGuideLink.take())
        assertNull(ScoringGuideLink.take())
    }
}
