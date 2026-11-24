package com.noop.ui

import com.noop.R
import com.noop.analytics.HRVReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** R8b (H-03, H-04, H-05, J-05): the strap step's states, the plan step's bands, age from a date of birth, the copy. */
class RidgeOnboardingStepsTest {

    private fun english(): String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/res/values/strings.xml").takeIf { f -> f.isFile } ?: File(it, "src/main/res/values/strings.xml") }
        .first { it.isFile }.readText()

    private fun string(name: String): String =
        Regex("""<string name="$name">([^<]*)</string>""").find(english())!!.groupValues[1]

    @Test
    fun strapStateFollowsConnectionThenPermissionThenRadioThenScan() {
        fun s(p: Boolean = true, d: Boolean = false, bt: Boolean = true, sc: Boolean = false, c: Boolean = false, b: Boolean = false, e: Boolean = false) =
            strapStepState(p, d, bt, sc, c, b, e)
        assertEquals(StrapStepState.BONDED, s(b = true, e = true, c = true))
        assertEquals(StrapStepState.LIVE_HR_UNPAIRED, s(b = true, c = true))
        assertEquals(StrapStepState.PAIRING, s(c = true))
        assertEquals(StrapStepState.NEEDS_PERMISSION, s(p = false))
        assertEquals(StrapStepState.PERMISSION_DENIED, s(p = false, d = true))
        // A missing permission is the first thing to fix, even with the radio off.
        assertEquals(StrapStepState.NEEDS_PERMISSION, s(p = false, bt = false))
        assertEquals(StrapStepState.BLUETOOTH_OFF, s(bt = false))
        assertEquals(StrapStepState.SEARCHING, s(sc = true))
        assertEquals(StrapStepState.READY, s())
        // A live link outranks a stale "denied" (the user granted it in Settings and came back).
        assertEquals(StrapStepState.PAIRING, s(p = false, d = true, c = true))
    }

    @Test
    fun planStepUsesV10sSynthesisBands() {
        // D-02: v10's floors 0/25/50/70/88, contiguous and covering 0..100.
        assertEquals(listOf(0..24, 25..49, 50..69, 70..87, 88..100), synthesisBandRanges())
    }

    @Test
    fun ageFromADateOfBirthCountsALeapDayOnTheFirstOfMarch() {
        val dob = LocalDate.of(2000, 2, 29)
        assertEquals(25, ProfileStore.yearsBetween(dob, LocalDate.of(2026, 2, 28)))
        assertEquals("D-06b: 29 Feb counts on 1 Mar", 26, ProfileStore.yearsBetween(dob, LocalDate.of(2026, 3, 1)))
        assertEquals(26, ProfileStore.yearsBetween(LocalDate.of(2000, 5, 10), LocalDate.of(2026, 5, 10)))
        assertEquals(25, ProfileStore.yearsBetween(LocalDate.of(2000, 5, 10), LocalDate.of(2026, 5, 9)))
    }

    @Test
    fun theFirstNightsCopyMatchesV10() {
        // H-05: "4 nights for Charge, 14 nights for normal ranges".
        assertTrue(string("onb_ridge_cal_n4_d").contains("4 nights"))
        assertTrue(string("onb_ridge_cal_n4_when").endsWith("4"))
        assertEquals(14, HRVReadiness.MIN_NIGHTS)
        assertTrue(string("onb_ridge_cal_n14_when").endsWith("${HRVReadiness.MIN_NIGHTS}"))
        assertEquals(4, FIRST_NIGHTS_UNLOCKS.size)
    }

    @Test
    fun theNumbersStepReadsTheScoringGuide() {
        // D-04-R: Rest is v10's composite, so its line is the scoring guide's, never "got ÷ needed".
        assertEquals(R.string.scoring_guide_rest_body, OnboardingScore.REST.line)
        assertEquals(R.string.scoring_guide_charge_body, OnboardingScore.CHARGE.line)
        assertEquals(R.string.scoring_guide_effort_body, OnboardingScore.EFFORT.line)
        assertTrue(string("scoring_guide_rest_body").contains("duration versus your need"))
    }
}
