package com.noop.ui

import com.noop.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** B2-Ra (D3): the Ridge workout page's source chip, against the stored origins the importers and detector write. */
class WorkoutSourceChipTest {

    @Test
    fun importedOriginsReadAsTheirSource() {
        assertEquals(R.string.workout_src_chip_whoop, workoutSourceChipRes("my-whoop", "my-whoop"))
        assertEquals(R.string.workout_src_chip_apple, workoutSourceChipRes("apple-health", "Apple Health"))
        assertEquals(R.string.workout_src_chip_hc, workoutSourceChipRes("health-connect", "health-connect"))
    }

    @Test
    fun aDetectedBoutReadsAsTheStrapItCameFrom() {
        assertEquals(R.string.workout_src_chip_whoop, workoutSourceChipRes("my-whoop", "my-whoop-noop"))
        // A non-WHOOP strap's detected bout must not fall through to the Apple label.
        assertEquals(R.string.workout_src_chip_strap, workoutSourceChipRes("polar-h10", "polar-h10-noop"))
    }

    @Test
    fun userAndFileOriginsAreNotCalledHeartRate() {
        assertEquals(R.string.workout_src_chip_manual, workoutSourceChipRes("my-whoop", "manual"))
        assertEquals(R.string.workout_src_chip_lifting, workoutSourceChipRes("my-whoop", "lifting"))
        assertEquals(R.string.workout_src_chip_file, workoutSourceChipRes("my-whoop", "activity-file"))
    }
}
