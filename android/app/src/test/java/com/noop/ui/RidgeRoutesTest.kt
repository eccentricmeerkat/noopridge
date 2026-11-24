package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R7a-2 (A-02, A-03): no dead links. Reads the NavHost registrations from AppRoot.kt and checks every Destination,
 * every Hub row, every deep-link target and every retired-route redirect lands on a registered route.
 */
class RidgeRoutesTest {

    private val appRoot: String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/noop/ui/AppRoot.kt").takeIf { f -> f.isFile } ?: File(it, "src/main/java/com/noop/ui/AppRoot.kt") }
        .first { it.isFile }.readText()

    /** Every `composable(...)` the NavHost registers, as route patterns. */
    // A `ridgeTool(route, headerAction = …)` registration names its route first, so the match ends at `,` or `)`.
    private val registered: Set<String> by lazy {
        val consts = mapOf("HUB_SECTION_ROUTE" to HUB_SECTION_ROUTE, "EXPLORE_METRIC_ROUTE" to EXPLORE_METRIC_ROUTE, "WORKOUT_ROUTE" to WORKOUT_ROUTE)
        Regex("""(?:composable|ridgeTool)\(\s*(Destination\.(\w+)\.route|"([^"]+)"|([A-Z_]+))\s*[,)]""").findAll(appRoot).mapNotNull { m ->
            when {
                m.groupValues[2].isNotEmpty() -> Destination.valueOf(m.groupValues[2]).route
                m.groupValues[3].isNotEmpty() -> m.groupValues[3]
                else -> consts[m.groupValues[4]]
            }
        }.toSet()
    }

    /** A route matches a pattern segment by segment; a `{x}` segment matches any one segment. */
    private fun resolves(route: String): Boolean = registered.any { pattern ->
        val p = pattern.split('/')
        val r = route.split('/')
        p.size == r.size && p.zip(r).all { (a, b) -> (a.startsWith("{") && a.endsWith("}") && b.isNotEmpty()) || a == b }
    }

    @Test
    fun everyDestinationIsRegistered() {
        val missing = Destination.entries.map { it.route }.filterNot { it in registered }
        assertTrue("Destinations with no composable(): $missing", missing.isEmpty())
    }

    @Test
    fun everyHubRowLandsOnARoute() {
        val rows = ridgeHubGroups().flatMap { it.rows } + HubSection.entries.flatMap { s -> hubSection(s).flatMap { it.rows } }
        val dead = rows.mapNotNull {
            when (val t = it.target) {
                is HubTarget.Screen -> t.dest.route
                is HubTarget.Section -> t.key.route
                is HubTarget.Sheet -> null
            }
        }.filterNot(::resolves)
        assertTrue("Hub rows with no route: $dead", dead.isEmpty())
    }

    @Test
    fun retiredRoutesRedirectToExplore() {
        assertTrue(resolves(exploreRoute("hrv")))
        assertTrue(resolves(Destination.Explore.route))
        // The three screens still have their routes (so old links land). Health and Vital signs detail redirect under
        // Ridge; the Vital signs list is a Ridge page of its own since Hub3a, so it no longer redirects.
        for (d in listOf(Destination.Health, Destination.VitalSigns, Destination.VitalSignsDetail)) {
            assertTrue("${d.route} not registered", d.route in registered)
        }
        assertEquals(2, Regex("RetiredRouteRedirect\\(nav, Destination\\.(Health|VitalSignsDetail)\\.route").findAll(appRoot).count())
    }

    @Test
    fun deepLinksParseAndLand() {
        val cases = mapOf(
            "noop://today" to DeepLinkTarget.Tab("today"),
            "noop://trends?mode=7" to DeepLinkTarget.Tab("trends"),
            "noop://baseline" to DeepLinkTarget.Tab("baseline"),
            "noop://sleep" to DeepLinkTarget.Tab("sleep"),
            "noop://hub" to DeepLinkTarget.Tab("more"),
            "noop://insights?m=hrv" to DeepLinkTarget.Push("explore/hrv"),
            "noop://insights" to DeepLinkTarget.Push("explore"),
            "noop://workout/1762862400" to DeepLinkTarget.Push("workout/1762862400"),
            "noop://workout/abc" to DeepLinkTarget.Push("workouts"),
            "noop://stress" to DeepLinkTarget.Push("stress"),
            "noop://coach_settings" to DeepLinkTarget.Push("coach"),
            "noop://lab_book" to DeepLinkTarget.Push("lab_book"),
        )
        for ((link, want) in cases) {
            val got = parseNoopLink(link)
            assertEquals(link, want, got)
            val route = when (got) { is DeepLinkTarget.Tab -> got.route; is DeepLinkTarget.Push -> got.route; null -> "" }
            assertTrue("$link -> $route not registered", resolves(route))
        }
        assertNull(parseNoopLink("https://example.com"))
        assertNull(parseNoopLink("noop://nowhere"))
        assertNull(parseNoopLink("noop://vital_detail")) // parameterised routes are not linkable by name
        assertEquals(RidgeTrendsRange.D30, trendsRangeFor("30"))
        assertEquals(RidgeTrendsRange.D7, trendsRangeFor("7d"))
        assertNull(trendsRangeFor("90"))
    }
}
