package com.noop.ui

import com.noop.update.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Test

/** RS-1-1 item 1: the Hub > About Updates card's status line and button label for every state. */
class AboutUpdateStatusTest {
    @Test fun neverCheckedSaysSoAndNeverInventsATime() {
        val s = aboutUpdateStatus(checking = false, result = null, lastCheckedAtMs = 0L)
        assertEquals(AboutUpdateStatus.NeverChecked, s)
        assertEquals(AboutUpdateButton.CHECK, aboutUpdateButton(s))
    }

    @Test fun anEarlierCheckShowsItsTime() {
        val s = aboutUpdateStatus(checking = false, result = null, lastCheckedAtMs = 1_700_000_000_000L)
        assertEquals(AboutUpdateStatus.LastChecked(1_700_000_000_000L), s)
        assertEquals(AboutUpdateButton.CHECK, aboutUpdateButton(s))
    }

    @Test fun aCheckInFlightWinsOverAnyResult() {
        val s = aboutUpdateStatus(checking = true, result = UpdateCheck.Result.Failed, lastCheckedAtMs = 5L)
        assertEquals(AboutUpdateStatus.Checking, s)
        assertEquals(AboutUpdateButton.CHECKING, aboutUpdateButton(s))
    }

    @Test fun upToDateFailedAndAvailableEachOfferCheckAgain() {
        val up = aboutUpdateStatus(false, UpdateCheck.Result.UpToDate("12.1.2"), 5L)
        assertEquals(AboutUpdateStatus.UpToDate("12.1.2"), up)
        val failed = aboutUpdateStatus(false, UpdateCheck.Result.Failed, 5L)
        assertEquals(AboutUpdateStatus.Failed, failed)
        val avail = aboutUpdateStatus(false, UpdateCheck.Result.Available("12.2.0", "https://example.invalid/r", "notes"), 5L)
        assertEquals(AboutUpdateStatus.Available("12.2.0", "https://example.invalid/r", "notes"), avail)
        listOf(up, failed, avail).forEach { assertEquals(AboutUpdateButton.CHECK_AGAIN, aboutUpdateButton(it)) }
    }
}
