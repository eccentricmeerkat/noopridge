package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B2-4b-4 and RS-1-2: the removed sections are off the page, and saved layouts that name them still decode. */
class SleepSectionRemovalTest {

    @Test
    fun theTwoRemovedSectionsStayInTheEnumAndTheDefaultOrder() {
        assertEquals(
            setOf(SleepSection.SLEEP_MARKS, SleepSection.NIGHT_DETAIL, SleepSection.SLEEP_DEBT, SleepSection.STAGES_VS_TYPICAL),
            SleepSection.removedFromPage,
        )
        // Kept so a saved layout (or a restore) naming them decodes, and the order registry still covers every case.
        assertTrue(SleepSection.entries.all { it in SleepSection.defaultOrder })
    }

    @Test
    fun theArrangeSheetOffersNeitherRemovedSection() {
        for (ridge in listOf(true, false)) {
            assertFalse(SleepSection.SLEEP_MARKS.isOffered(ridge))
            assertFalse(SleepSection.NIGHT_DETAIL.isOffered(ridge))
            // RS-1-2 (owner, 25 Nov): the Sleep-debt ledger and Stages vs typical duplicated other cards.
            assertFalse(SleepSection.SLEEP_DEBT.isOffered(ridge))
            assertFalse(SleepSection.STAGES_VS_TYPICAL.isOffered(ridge))
            assertTrue(SleepSection.ASLEEP_DURATION.isOffered(ridge))
            assertTrue(SleepSection.STAGES.isOffered(ridge))
            assertTrue(SleepSection.CONSISTENCY.isOffered(ridge))
        }
    }

    @Test
    fun hoursVsNeededIsAFixedCardUnderRidgeOnly() {
        assertFalse(SleepSection.HOURS_VS_NEEDED.isOffered(ridge = true))
        assertTrue(SleepSection.HOURS_VS_NEEDED.isOffered(ridge = false))
    }

    @Test
    fun aSavedOrderWithoutTheOfferedSubsetStillDecodesToEverySection() {
        // The sheet saves only what it offered; decode puts the rest back, so no layout ever loses a case.
        val savedByTheSheet = SleepSection.entries.filter { it.isOffered(ridge = true) }.reversed()
        val decoded = SleepLayoutPrefs.decodeOrder(SleepLayoutPrefs.encode(savedByTheSheet))
        assertEquals(SleepSection.entries.toSet(), decoded.toSet())
        assertEquals(SleepSection.entries.size, decoded.size)
        // And the page filters the removed ones back out at render time.
        assertTrue(decoded.filterNot { it in SleepSection.removedFromPage }.none { it == SleepSection.SLEEP_MARKS || it == SleepSection.NIGHT_DETAIL })
    }
}
