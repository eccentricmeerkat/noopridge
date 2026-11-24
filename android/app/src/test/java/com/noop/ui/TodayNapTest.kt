package com.noop.ui

import com.noop.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Side quest 5: a nap is a Today activity and a band on the heart-rate chart, found by the Sleep tab's rule (a block
 * outside the day's main-night group), and tonight's plan target can drop by today's nap (V2-11).
 */
class TodayNapTest {

    private val zone = ZoneId.of("Asia/Dubai")
    private fun ts(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toEpochSecond()
    private fun session(id: String, s: Long, e: Long) = SleepSession(deviceId = id, startTs = s, endTs = e)

    private fun withZone(block: () -> Unit) {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try { block() } finally { TimeZone.setDefault(old) }
    }

    private val dayStart get() = ts(14, 0, 0)
    private val now get() = ts(14, 20, 0)
    private val night get() = session("my-whoop", ts(13, 23, 0), ts(14, 7, 0))

    /** Today's blocks as the loader builds them, then its naps. */
    private fun naps(imported: List<SleepSession>, computed: List<SleepSession>): List<SleepSession> {
        val merged = daySleepBlocks(imported, computed, dayStart, now)
        return dayNaps(napSourceBlocks(merged, imported + computed, dayStart, now), null, dayStart, now)
    }

    @Test
    fun aNapDayListsTheNapAndNotTheNight() = withZone {
        val nap = session("my-whoop-noop", ts(14, 15, 0), ts(14, 15, 40))
        assertEquals(listOf(nap), naps(listOf(night), listOf(nap)))
    }

    @Test
    fun aNoNapDayListsNone() = withZone {
        assertTrue(naps(listOf(night), emptyList()).isEmpty())
    }

    @Test
    fun anImportedNapIsListed() = withZone {
        // A WHOOP export stores its naps as sessions beside the night, under the same id.
        val nap = session("my-whoop", ts(14, 14, 10), ts(14, 14, 55))
        assertEquals(listOf(nap), naps(listOf(night, nap), emptyList()))
    }

    @Test
    fun aPhoneNapSurvivesAnImportedNightsDay() = withZone {
        // The merge keeps a wake-day from one source, so the accepted (computed) nap is dropped there…
        val nap = session("my-whoop-noop", ts(14, 15, 0), ts(14, 15, 40))
        assertTrue(nap !in daySleepBlocks(listOf(night), listOf(nap), dayStart, now))
        // …and the nap source blocks bring it back, so Today agrees with the Sleep tab and sleep debt.
        assertEquals(listOf(nap), naps(listOf(night), listOf(nap)))
    }

    @Test
    fun aNapRecordedByBothSourcesIsListedOnce() = withZone {
        val imported = session("my-whoop", ts(14, 15, 0), ts(14, 15, 40))
        val computed = session("my-whoop-noop", ts(14, 15, 2), ts(14, 15, 38))
        assertEquals(1, naps(listOf(night, imported), listOf(computed)).size)
    }

    @Test
    fun aSkippedNapIsNeverListed() = withZone {
        // A detected nap the wearer skips on the review card is never stored, so only the night is there.
        assertTrue(naps(listOf(night), emptyList()).isEmpty())
        // A nap from yesterday afternoon is not today's.
        val yesterday = session("my-whoop-noop", ts(13, 15, 0), ts(13, 15, 40))
        assertTrue(naps(listOf(night), listOf(yesterday)).isEmpty())
    }

    @Test
    fun activitiesListTheNightTheNapAndWorkoutsInOrder() = withZone {
        val nap = session("my-whoop-noop", ts(14, 15, 0), ts(14, 15, 40))
        val day = DayActivity(dayStart, now, night, emptyList(), naps = listOf(nap))
        val acts = todayActivities(day)
        assertEquals(2, acts.size)
        assertTrue(acts[0] is TodayActivity.Sleep)
        assertEquals(TodayActivity.Nap(nap), acts[1])
    }

    @Test
    fun tonightsTargetIsTheNeedLessTheNap() {
        assertEquals(440.0, planSleepTargetMin(needMin = 480.0, napAsleepMin = 40.0), 1e-9)
        assertEquals(480.0, planSleepTargetMin(needMin = 480.0, napAsleepMin = 0.0), 1e-9)
        assertEquals(0.0, planSleepTargetMin(needMin = 60.0, napAsleepMin = 90.0), 1e-9)
    }
}
