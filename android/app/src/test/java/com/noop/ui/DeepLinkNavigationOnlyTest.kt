package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B2-Rb security audit S3: the exported `noop://` entry point may only NAVIGATE. Another app or a web page can fire
 * any such link, so a link must never delete, export, start a session or write anything.
 */
class DeepLinkNavigationOnlyTest {

    private val source: String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/noop/ui/RidgeDeepLinks.kt").takeIf { f -> f.isFile } ?: File(it, "src/main/java/com/noop/ui/RidgeDeepLinks.kt") }
        .first { it.isFile }.readText()

    @Test
    fun aLinkResolvesToATabOrAPushedScreenAndNothingElse() {
        // The whole result type: if someone adds an "action" variant, this fails and the change gets a security look.
        val block = source.substringAfter("internal sealed interface DeepLinkTarget {").substringBefore("\n}")
        val variants = Regex("""data class (\w+)\(""").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("Tab", "Push"), variants)
    }

    @Test
    fun linksThatNameNoScreenDoNothing() {
        assertNull(parseNoopLink("https://example.com"))
        assertNull(parseNoopLink("noop://"))
        assertNull(parseNoopLink("noop://delete_everything"))
        assertNull(parseNoopLink("noop://export?to=http://evil.example"))
        assertNull(parseNoopLink(null))
        assertNull(parseNoopLink("not a uri at all"))
    }

    @Test
    fun queryTextIsOnlyEverUsedAsAMetricKeyOrNumber() {
        val t = parseNoopLink("noop://insights?m=hrv&evil=1")
        assertTrue(t is DeepLinkTarget.Push)
        assertEquals(exploreRoute("hrv"), (t as DeepLinkTarget.Push).route)
        assertNotNull(parseNoopLink("noop://workout/1700000000"))
        // A bad id or a missing metric falls back to a list screen, which is still only a navigation.
        assertEquals(DeepLinkTarget.Push(Destination.Workouts.route), parseNoopLink("noop://workout/abc"))
        assertEquals(DeepLinkTarget.Push(Destination.Explore.route), parseNoopLink("noop://insights"))
    }
}
