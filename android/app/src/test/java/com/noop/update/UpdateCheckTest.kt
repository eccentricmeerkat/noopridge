package com.noop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the version comparison that drives "Check for updates". The headline case is the string-compare
 * trap: "1.40" must be NEWER than "1.39" (lexicographically it isn't), and "1.9" must be OLDER than
 * "1.10". Also covers the demo flavour's "-demo" suffix and a leading "v".
 */
class UpdateCheckTest {

    @Test
    fun newer() {
        assertTrue(UpdateCheck.isNewer("1.40", "1.39"))   // the trap: "1.40" < "1.39" as strings
        assertTrue(UpdateCheck.isNewer("1.10", "1.9"))    // and "1.10" < "1.9" as strings
        assertTrue(UpdateCheck.isNewer("2.0", "1.39"))
        assertTrue(UpdateCheck.isNewer("1.39.1", "1.39")) // extra patch segment
        assertTrue(UpdateCheck.isNewer("v1.40", "1.39"))  // tolerant of the tag's "v"
    }

    @Test
    fun notNewer() {
        assertFalse(UpdateCheck.isNewer("1.39", "1.39"))      // equal
        assertFalse(UpdateCheck.isNewer("1.38", "1.39"))      // older
        assertFalse(UpdateCheck.isNewer("1.9", "1.10"))
        assertFalse(UpdateCheck.isNewer("1.39-demo", "1.39")) // demo flavour vs the same release
        assertFalse(UpdateCheck.isNewer("garbage", "1.39"))   // unparseable → not newer (no false alarm)
    }

    @Test
    fun noReleasesYetIsUpToDateNotAFailure() {
        assertEquals(UpdateCheck.Result.UpToDate("12.1.0"), UpdateCheck.classify("[]", "12.1.0"))
        assertEquals(
            UpdateCheck.Result.UpToDate("12.1.0"),
            UpdateCheck.classify("""[{"tag_name":"v13.0.0","html_url":"u","draft":true}]""", "12.1.0"),
        )
    }

    @Test
    fun aNewerReleaseIsAvailableAndTheSameOneIsUpToDate() {
        val body = """[{"tag_name":"v12.1.1","html_url":"https://x/r","body":"Notes","draft":false}]"""
        assertTrue(UpdateCheck.classify(body, "12.1.0") is UpdateCheck.Result.Available)
        assertEquals(UpdateCheck.Result.UpToDate("12.1.1"), UpdateCheck.classify(body, "12.1.1"))
    }

    @Test(expected = org.json.JSONException::class)
    fun malformedJsonStillThrowsSoCheckReportsFailed() {
        UpdateCheck.classify("not json", "12.1.0")
    }
}
