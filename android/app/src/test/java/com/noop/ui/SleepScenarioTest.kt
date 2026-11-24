package com.noop.ui

import com.noop.analytics.RecoveryForecaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SQ9 S3: the forecast's "If I sleep" lengths, and that asking about more sleep never lowers the forecast. */
class SleepScenarioTest {

    @Test
    fun theTypicalLengthIsRoundedToHalfAnHourAndJoinsTheFixedOnes() {
        val s = sleepScenarioHours(7.4)
        assertEquals(7.5, s.typical, 0.0)
        assertEquals(listOf(6.0, 7.0, 7.5, 8.0, 9.0), s.hours)
    }

    @Test
    fun aTypicalLengthThatIsAlreadyListedIsNotRepeated() {
        val s = sleepScenarioHours(7.0)
        assertEquals(listOf(6.0, 7.0, 8.0, 9.0), s.hours)
        assertEquals(7.0, s.typical, 0.0)
    }

    @Test
    fun moreSleepNeverLowersTheForecast() {
        val charge = List(14) { 55.0 + it % 5 }
        val effort = List(14) { 40.0 + it % 3 }
        val by = listOf(6.0, 7.0, 8.0, 9.0).map { h ->
            RecoveryForecaster.forecast(charge, effort, todayEffort = 45.0, plannedSleepHours = h)!!.charge
        }
        assertTrue("forecast by sleep length: $by", by.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("sleep should matter: $by", by.last() > by.first())
    }
}
