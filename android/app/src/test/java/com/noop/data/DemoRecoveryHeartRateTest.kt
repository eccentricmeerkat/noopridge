package com.noop.data

import com.noop.analytics.HeartRateRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** B2-3 (D17): the demo's recovery windows are measurable by the real engine, and trend upward. */
class DemoRecoveryHeartRateTest {
    private val now = 1_800_000_000L
    private val day = 86_400L

    private fun workout(daysAgo: Int, src: String = "my-whoop"): WorkoutRow {
        val end = now - daysAgo * day
        return WorkoutRow(deviceId = src, startTs = end - 3000, endTs = end, sport = "Running", source = src)
    }

    @Test
    fun everySeededSessionIsMeasurableAndTheDropGrows() {
        val workouts = (5..100 step 3).map { workout(it) }
        val hr = DemoSeeder.demoRecoveryHeartRate(workouts, now)
        val ends = hr.map { it.ts }.sorted()
        assertTrue(hr.isNotEmpty())
        // Group the windows by the workout each belongs to and read them with the real engine, at a low and a high max HR.
        val picked = workouts.filter { w -> hr.any { it.ts == w.endTs } }.sortedBy { it.endTs }
        assertEquals(12, picked.size)
        for (maxHr in listOf(180.0, 190.0)) {
            val drops = picked.map { w ->
                HeartRateRecovery.calculate(hr.filter { it.ts in (w.endTs - 400)..(w.endTs + 400) }, w.startTs, w.endTs, maxHr)?.after1Minute
            }
            assertTrue("maxHr $maxHr: every session measurable, got $drops", drops.all { it != null })
            val d = drops.map { it!! }
            assertTrue("drop grows: $d", d.takeLast(3).average() > d.take(3).average() + 4)
        }
        assertTrue(ends.size == hr.size)
    }

    @Test
    fun skipsRecentAndNonStrapWorkoutsAndHandlesNone() {
        assertTrue(DemoSeeder.demoRecoveryHeartRate(emptyList(), now).isEmpty())
        assertTrue(DemoSeeder.demoRecoveryHeartRate(listOf(workout(1), workout(3)), now).isEmpty())
        assertTrue(DemoSeeder.demoRecoveryHeartRate(listOf(workout(20, "apple-health")), now).isEmpty())
    }
}
