package com.noop.ui

import com.noop.data.SleepSession
import com.noop.data.WorkoutRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C-06 (C06-1): Today's Activities lists the day's main sleep plus the workouts that START that day. */
class TodayActivitiesTest {
    private val dayStart = 1_762_800_000L
    private val dayEnd = dayStart + 86_399L
    private fun w(start: Long, dur: Long = 2400, src: String = "my-whoop", sport: String = "Cycling") =
        WorkoutRow(deviceId = src, startTs = start, endTs = start + dur, sport = sport, source = src)
    private val sleep = SleepSession(deviceId = "my-whoop", startTs = dayStart - 4 * 3600, endTs = dayStart + 3 * 3600)

    @Test
    fun sleepComesFirstThenTheDaysWorkoutsInOrder() {
        val day = DayActivity(dayStart, dayEnd, sleep, listOf(w(dayStart + 18 * 3600), w(dayStart + 9 * 3600, sport = "Running")))
        val a = todayActivities(day)
        assertTrue(a[0] is TodayActivity.Sleep)
        assertEquals(listOf("Running", "Cycling"), a.drop(1).map { (it as TodayActivity.Workout).row.sport })
    }

    @Test
    fun aWorkoutThatStartedTheEveningBeforeIsNotToday() {
        val day = DayActivity(dayStart, dayEnd, null, listOf(w(dayStart - 1800, dur = 3600)))
        assertTrue(todayActivities(day).isEmpty())
    }

    @Test
    fun aRestDayWithANightIsNeverEmpty() {
        val a = todayActivities(DayActivity(dayStart, dayEnd, sleep, emptyList()))
        assertEquals(1, a.size)
        assertTrue(a[0] is TodayActivity.Sleep)
    }

    @Test
    fun theSameSessionFromTwoSourcesIsOneRow() {
        val t = dayStart + 18 * 3600
        val day = DayActivity(dayStart, dayEnd, null, listOf(w(t, src = "my-whoop"), w(t + 30, src = "health-connect")))
        assertEquals(1, todayActivities(day).size)
    }
}
