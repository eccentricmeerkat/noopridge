package com.noop.ui

import com.noop.data.ImportProgress
import com.noop.data.ImportStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The estimate and the bar behind the backup import progress dialog. */
class BackupImportProgressLogicTest {

    @Test fun noEstimateBeforeTwoSecondsOrThreePercent() {
        assertNull(importSecondsLeft(elapsedMs = 1_500, done = 50, total = 100))
        assertNull(importSecondsLeft(elapsedMs = 5_000, done = 2, total = 100))
        assertNull(importSecondsLeft(elapsedMs = 5_000, done = 0, total = 100))
        assertNull("a step with no length has no estimate", importSecondsLeft(elapsedMs = 5_000, done = 0, total = 0))
    }

    @Test fun theEstimateExtrapolatesTheRateSoFar() {
        // Half done in 10 s: about 10 s more.
        assertEquals(10, importSecondsLeft(10_000, 50, 100))
        // A quarter done in 6 s: 18 s more.
        assertEquals(18, importSecondsLeft(6_000, 25, 100))
        assertEquals(0, importSecondsLeft(10_000, 100, 100))
    }

    @Test fun theBarFillsAsTheStepProgresses() {
        assertEquals(0.5f, importStepFraction(ImportProgress(ImportStage.REPLACING, 5, 6, 50, 100))!!, 0f)
        assertNull(importStepFraction(ImportProgress(ImportStage.CHECKING, 3, 6)))
        assertEquals(1f, importStepFraction(ImportProgress(ImportStage.REPLACING, 5, 6, 150, 100))!!, 0f)
    }

    @Test fun everyStageHasAWordingString() {
        assertTrue(ImportStage.entries.all { importStageLabel(it) != 0 })
    }
}
