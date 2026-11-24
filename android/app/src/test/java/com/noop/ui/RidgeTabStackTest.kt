package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** #72: the tab that owns the stack on screen, and what a tab tap does with it. */
class RidgeTabStackTest {

    private fun owner(vararg stack: Destination) = ridgeStackTab { it in stack }

    @Test
    fun aPageOpenedFromTodayBelongsToToday() {
        // Today → Effort orb → Insights: Today's stack, so Today stays lit (it used to light Hub).
        assertEquals(Destination.Today, owner(Destination.Today))
    }

    @Test
    fun theOtherTabOnTheStackOwnsIt() {
        assertEquals(Destination.Trends, owner(Destination.Today, Destination.Trends))
        assertEquals(Destination.More, owner(Destination.Today, Destination.More, Destination.Settings))
        assertEquals(Destination.Sleep, owner(Destination.Today, Destination.Sleep))
    }

    @Test
    fun tappingTheOwningTabReturnsToItsRoot() {
        // The bug: on Insights in Today's stack, tapping Today restored the same stack forever.
        assertEquals(RidgeTabTap.POP_TO_ROOT, ridgeTabTap(Destination.Today, Destination.Insights, Destination.Today))
        assertEquals(RidgeTabTap.POP_TO_ROOT, ridgeTabTap(Destination.More, Destination.Settings, Destination.More))
    }

    @Test
    fun tappingTheTabYouAreOnDoesNothingAndAnotherTabSwitches() {
        assertEquals(RidgeTabTap.STAY, ridgeTabTap(Destination.Today, Destination.Today, Destination.Today))
        assertEquals(RidgeTabTap.SWITCH, ridgeTabTap(Destination.Trends, Destination.Insights, Destination.Today))
        assertEquals(RidgeTabTap.SWITCH, ridgeTabTap(Destination.Today, Destination.Settings, Destination.More))
    }
}
