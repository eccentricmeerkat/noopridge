package com.noop.ui

import com.noop.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7a-1 (A-01, G-01): the Ridge Hub reaches every v10 More destination, and its sub-lists resolve. */
class RidgeHubTest {

    @Test
    fun everyMoreDestinationIsReachableFromTheHub() {
        // Health and Vital Signs are retired in R7a-2 (A-03): they redirect to Insights.
        val retired = setOf(Destination.Health, Destination.VitalSigns)
        val missing = drawerGroups.flatMap { it.items }.toSet() - retired - hubReachable()
        assertTrue("Not reachable from the Hub: $missing", missing.isEmpty())
        assertTrue(Destination.Coach in hubReachable())
    }

    @Test
    fun testCentreSitsBehindAboutAdvanced() {
        val topLevel = ridgeHubGroups().flatMap { it.rows }.mapNotNull { (it.target as? HubTarget.Screen)?.dest }
        assertTrue(Destination.TestCentre !in topLevel)
        val advanced = hubSection(HubSection.ABOUT).last().rows.mapNotNull { (it.target as? HubTarget.Screen)?.dest }
        assertEquals(listOf(Destination.TestCentre), advanced)
    }

    @Test
    fun bodyMoreIsTheBoardsBodyToolsPage() {
        // BodyMore.dc.html: "Body" over "More", then one "Body tools" card whose rows carry the board's icon, hue and
        // one-line subtitle. Cycle awareness is the board's fourth row; the app has no Cycle surface yet, so the page
        // carries the three tools it can open.
        assertEquals(R.string.more_group_body, HubSection.BODY_MORE.eyebrowRes)
        assertEquals(R.string.nav_hub, HubSection.ABOUT.eyebrowRes)

        val groups = hubSection(HubSection.BODY_MORE, hydrationSub = "1,250 ml today")
        assertEquals(listOf(R.string.hub_body_tools), groups.map { it.headerRes })
        val rows = groups.single().rows
        assertEquals(
            listOf(Destination.LabBook, Destination.Hydration, Destination.Rhythm),
            rows.mapNotNull { (it.target as? HubTarget.Screen)?.dest },
        )
        assertTrue("Every Body tools row takes the board's menu icon", rows.all { it.icon != null })
        assertEquals(R.string.hub_lab_book_sub, rows[0].subtitleRes)
        assertEquals(R.string.hub_rhythm_sub, rows[2].subtitleRes)
        // The Hydration row's subtitle is supplied per composition — today's total when tracking is on, the board's off
        // line when it is not — and none until the first read lands.
        assertEquals("1,250 ml today", rows[1].subtitle)
        assertNull(hubSection(HubSection.BODY_MORE).single().rows[1].subtitle)
    }

    @Test
    fun sectionsRoundTripAndLightTheHub() {
        for (s in HubSection.entries) {
            assertEquals(s, HubSection.of(s.route.removePrefix("hub/")))
            assertEquals(Destination.More, Destination.forRoute(s.route))
        }
        assertEquals(Destination.More, Destination.forRoute(HUB_SECTION_ROUTE))
        assertNull(HubSection.of("nope"))
    }

    @Test
    fun sheetsAreHubPagesNotPopups() {
        // SQ9 S1: each sheet row navigates to its own route, which keeps the Hub lit and never collides with a sub-list.
        val sheetRows = ridgeHubGroups().flatMap { it.rows } + HubSection.entries.flatMap { hubSection(it).flatMap { g -> g.rows } }
        val sheets = sheetRows.mapNotNull { (it.target as? HubTarget.Sheet)?.kind }.toSet()
        assertEquals(HubSheet.entries.toSet(), sheets)
        assertEquals(HubSheet.entries.size, HubSheet.entries.map { it.route }.toSet().size)
        for (k in HubSheet.entries) {
            assertTrue(k.route.startsWith("hub/sheet/"))
            assertNull(HubSection.of(k.route.removePrefix("hub/")))
            assertEquals(Destination.More, Destination.forRoute(k.route))
        }
    }

    @Test
    fun boardGroupsInOrder() {
        assertEquals(
            listOf(R.string.more_group_insights, R.string.more_group_body, R.string.more_group_data, R.string.more_group_app),
            ridgeHubGroups().map { it.headerRes },
        )
    }
}
