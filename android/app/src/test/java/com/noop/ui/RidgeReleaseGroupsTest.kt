package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RS-2c: What's new shows one card per major.minor, newest first, four at most, then the GitHub link. */
class RidgeReleaseGroupsTest {

    private fun r(v: String) = RidgeRelease(v, listOf("line $v"))

    @Test
    fun theShippedNotesMakeThreeCards() {
        val groups = groupReleases(RidgeReleaseNotes.releases)
        assertEquals(listOf("12.2", "12.1", "12.0"), groups.map { it.minor })
        assertEquals(listOf("12.1.2", "12.1.0"), groups[1].releases.map { it.version })
        assertFalse(hasOlderReleases(RidgeReleaseNotes.releases))
    }

    @Test
    fun onlyASharedCardShowsPatchHeadings() {
        val groups = groupReleases(RidgeReleaseNotes.releases)
        assertEquals(listOf("12.2.3", "12.2.2", "12.2.0"), groups[0].releases.map { it.version })
        assertTrue(groups[0].showsPatchHeadings)
        assertTrue(groups[1].showsPatchHeadings)
        assertFalse(groups[2].showsPatchHeadings)
    }

    @Test
    fun theLaunchCardKeepsItsSubtitleAndEveryLine() {
        val launch = groupReleases(RidgeReleaseNotes.releases).last()
        assertEquals("Ridge v1.0", launch.releases.single().subtitle)
        assertEquals(11, launch.releases.single().items.size)
        // No line carries the old "New in x.y." lead-in: the card title says it now.
        assertTrue(RidgeReleaseNotes.items.none { it.startsWith("**New in") })
    }

    @Test
    fun sixMinorsShowFourAndPointToGithub() {
        val rs = listOf(r("12.7.0"), r("12.6.1"), r("12.6.0"), r("12.5.0"), r("12.4.0"), r("12.3.0"), r("12.2.0"))
        val groups = groupReleases(rs)
        assertEquals(listOf("12.7", "12.6", "12.5", "12.4"), groups.map { it.minor })
        assertTrue(hasOlderReleases(rs))
    }

    @Test
    fun headingsDropOnlyAZeroPatch() {
        assertEquals("12.1", releaseHeading("12.1.0"))
        assertEquals("12.1.2", releaseHeading("12.1.2"))
        assertEquals("12.10", minorOf("12.10.3"))
    }
}
