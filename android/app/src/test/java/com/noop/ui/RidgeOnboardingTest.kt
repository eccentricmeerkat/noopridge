package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R8a (H-01, H-02, D-09): the Ridge first run. Terms comes before every step that asks for Bluetooth or notifications
 * or talks to the strap; a resumed run never lands past Terms before acceptance; Skip is never offered before Terms.
 */
class RidgeOnboardingTest {

    private val pages = RIDGE_ONBOARDING_PAGES
    private val terms = pages.indexOf(OnboardingPage.RidgeTerms)

    private fun source(name: String): String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/noop/ui/$name").takeIf { f -> f.isFile } ?: File(it, "src/main/java/com/noop/ui/$name") }
        .first { it.isFile }.readText()

    @Test
    fun termsPrecedesEveryStrapAndPermissionStep() {
        val gated = listOf(OnboardingPage.RidgeStrap, OnboardingPage.Import, OnboardingPage.Notifications)
        assertTrue(terms > 0)
        for (p in gated) assertTrue("$p must come after Terms", pages.indexOf(p) > terms)
        // Before Terms: content only (D-09).
        assertEquals(
            listOf(OnboardingPage.RidgeWelcome, OnboardingPage.RidgeIntro, OnboardingPage.RidgeAims,
                OnboardingPage.RidgeNumbers, OnboardingPage.RidgeSynthesis),
            pages.subList(0, terms),
        )
        assertEquals(OnboardingPage.RidgeFirstNights, pages.last())
        assertEquals(12, pages.size)
        assertFalse("Ridge is dark only (D-10)", OnboardingPage.Appearance in pages)
        for (gone in listOf(OnboardingPage.Welcome, OnboardingPage.WhatItDoes, OnboardingPage.Expectations,
            OnboardingPage.Bluetooth, OnboardingPage.Wear, OnboardingPage.Connect, OnboardingPage.Bonded, OnboardingPage.Done)) {
            assertFalse("$gone is replaced by a Ridge step", gone in pages)
        }
    }

    @Test
    fun resumeNeverPassesTermsBeforeAcceptance() {
        assertEquals(0, ridgeResumeIndex(null, pages, termsAccepted = false))
        assertEquals(0, ridgeResumeIndex("NoSuchStep", pages, termsAccepted = false))
        assertEquals(pages.indexOf(OnboardingPage.RidgeNumbers), ridgeResumeIndex("RidgeNumbers", pages, false))
        assertEquals(terms, ridgeResumeIndex("Profile", pages, termsAccepted = false))
        assertEquals(terms, ridgeResumeIndex("RidgeTerms", pages, termsAccepted = false))
        assertEquals(terms + 1, ridgeResumeIndex("RidgeTerms", pages, termsAccepted = true))
        assertEquals(pages.indexOf(OnboardingPage.Profile), ridgeResumeIndex("Profile", pages, termsAccepted = true))
    }

    @Test
    fun skipIsOfferedAfterTermsOnlyAndNotOnTheLastStep() {
        for (i in pages.indices) {
            assertEquals("step $i (${pages[i]})", i > terms && i < pages.lastIndex, ridgeCanSkip(i, pages))
        }
    }

    @Test
    fun stepsPassOverAcceptedTerms() {
        val synthesis = pages.indexOf(OnboardingPage.RidgeSynthesis)
        val strap = pages.indexOf(OnboardingPage.RidgeStrap)
        assertEquals(terms, ridgeNextIndex(synthesis, pages, termsAccepted = false, bonded = false))
        assertEquals(strap, ridgeNextIndex(synthesis, pages, termsAccepted = true, bonded = false))
        assertEquals(synthesis, ridgeBackIndex(strap, pages, termsAccepted = true, bonded = false))
        assertEquals(pages.lastIndex, ridgeNextIndex(pages.lastIndex, pages, true, false))
        assertEquals(0, ridgeBackIndex(0, pages, false, false))
    }

    @Test
    fun aStepSavedByAnOlderBuildResumesSafely() {
        // R8a saved v10 step names such as "Bluetooth"; they are gone from the R8b order.
        assertEquals(0, ridgeResumeIndex("Bluetooth", pages, termsAccepted = false))
        assertEquals(terms + 1, ridgeResumeIndex("Bluetooth", pages, termsAccepted = true))
        assertEquals(terms + 1, ridgeResumeIndex("Expectations", pages, termsAccepted = true))
    }

    @Test
    fun noBluetoothPromptAtLaunchBeforeCurrentTerms() {
        val main = source("MainActivity.kt")
        val i = main.indexOf("requestBlePermissions()\n")
        assertTrue(i > 0)
        val guard = main.substring(main.lastIndexOf("if (", i), i)
        assertTrue("the launch pre-warm needs onboarding", "KEY_ONBOARDED" in guard)
        assertTrue("the launch pre-warm needs current terms", "Terms.CURRENT_VERSION" in guard)
    }

    @Test
    fun theStepCtaReceivesTheStepItWasTappedOn() {
        // A function reference (::advance) kept an earlier step's handler on the Done step's CTA (R8a, on device).
        val flow = source("OnboardingScreen.kt").substringAfter("private fun RidgeOnboardingFlow(")
        assertTrue(flow.contains("onCta = { advance(page) }"))
        assertFalse(flow.substringBefore("\n}\n").contains("::"))
    }
}
