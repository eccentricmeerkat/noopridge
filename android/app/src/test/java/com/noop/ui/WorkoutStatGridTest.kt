package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** BS-x2-3 item 14: the workout summary tiles form a 2 x 2 grid with no empty cell. */
class WorkoutStatGridTest {
    @Test fun fourTilesAreTwoByTwo() = assertEquals(listOf(2, 2), workoutStatRowSizes(4))

    @Test fun fiveTilesEndWithAFullWidthTile() = assertEquals(listOf(2, 2, 1), workoutStatRowSizes(5))

    @Test fun oneAndThreeTilesNeverLeaveAGap() {
        assertEquals(listOf(1), workoutStatRowSizes(1))
        assertEquals(listOf(2, 1), workoutStatRowSizes(3))
    }

    @Test fun noTilesNoRows() = assertEquals(emptyList<Int>(), workoutStatRowSizes(0))

    @Test fun theGridIsTwoColumns() = assertEquals(2, WORKOUT_STAT_COLUMNS)
}
