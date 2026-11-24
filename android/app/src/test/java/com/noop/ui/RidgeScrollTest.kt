package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** SQ3-2: the backdrop's scroll maths and wiring (non-Today pages 1:1; Today leaves around the Synthesis card). */
class RidgeScrollTest {

    private fun source(path: String): String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/$path").takeIf { f -> f.isFile } ?: File(it, "src/$path") }
        .first { it.isFile }.readText()

    @Test
    fun scrollDistanceIsTheItemsAbovePlusTheOffset() {
        val sizes = mapOf(0 to 100, 1 to 300, 2 to 200)
        assertEquals(0f, listScrollPx(0, 0, 20f, { sizes[it] }, 150f), 0f)
        assertEquals(40f, listScrollPx(0, 40, 20f, { sizes[it] }, 150f), 0f)
        // Item 0 gone and 25 into item 1: 100 + 20 spacing + 25.
        assertEquals(145f, listScrollPx(1, 25, 20f, { sizes[it] }, 150f), 0f)
        // Item 2 at its top: (100+20) + (300+20).
        assertEquals(440f, listScrollPx(2, 0, 20f, { sizes[it] }, 150f), 0f)
    }

    @Test
    fun itemsNeverSeenFallBackToTheAverage() {
        // A list restored at item 3 with only item 1 ever measured: two of the three items above are guessed.
        val sizes = mapOf(1 to 300)
        assertEquals(300f + 20f + 2 * (150f + 20f) + 10f, listScrollPx(3, 10, 20f, { sizes[it] }, 150f), 0f)
    }

    @Test
    fun negativeOffsetsNeverMoveTheBackdropDown() {
        assertEquals(0f, listScrollPx(0, -30, 20f, { null }, 100f), 0f)
        assertEquals(0f, backdropExitProgress(-50f, 400f), 0f)
    }

    @Test
    fun exitProgressReachesOneExactlyAtTheExitDistance() {
        assertEquals(0f, backdropExitProgress(0f, 400f), 0f)
        assertEquals(0.5f, backdropExitProgress(200f, 400f), 0f)
        assertEquals(1f, backdropExitProgress(400f, 400f), 0f)
        assertEquals("stays gone past the point", 1f, backdropExitProgress(900f, 400f), 0f)
        assertEquals(1f, backdropExitProgress(5f, 0f), 0f)
        assertEquals(0f, backdropExitProgress(0f, 0f), 0f)
    }

    @Test
    fun todaysPhotoFollowsParallaxThenMovesOneToOne() {
        val d = 2f
        val synthTop = 1000f
        fun off(s: Float) = todayPhotoOffsetPx(s, synthTop, 2000, d)
        assertEquals(0f, off(0f), 0f)
        assertEquals(0f, off(-10f), 0f)
        // Early on the photo follows only a fraction of the scroll, so the orbs pass over the mountains.
        assertEquals(TODAY_PARALLAX_FOLLOW * 100f, off(100f), 0.001f)
        // Synthesis is "almost at the top" at scroll = synthTop - 100 dp; from there on the photo moves exactly with the content.
        val end = synthTop - TODAY_PARALLAX_END_BELOW_TOP_DP * d
        assertEquals(end, end + 1f - 1f, 0f)
        for (s in listOf(end, end + 50f, end + 400f)) {
            assertEquals("1:1 at $s", 50f, off(s + 50f) - off(s), 0.001f)
        }
        // Never faster than the content, never slower than the parallax, and never going backwards.
        var prev = 0f
        for (s in 1..1500) {
            val o = off(s.toFloat())
            assertTrue(o - prev >= TODAY_PARALLAX_FOLLOW - 0.001f && o - prev <= 1.001f)
            prev = o
        }
    }

    @Test
    fun aHiddenSynthesisCardFallsBackToTheListHeight() {
        assertEquals(
            todayPhotoOffsetPx(500f, 2000 * 0.45f, 2000, 2f),
            todayPhotoOffsetPx(500f, 0f, 2000, 2f), 0f,
        )
    }

    @Test
    fun wiringReadsTheSynthesisCardAndThePagesOwnScroll() {
        val today = source("main/java/com/noop/ui/TodayScreen.kt")
        assertTrue(today.contains("rememberListScrollPx(todayListState"))
        assertTrue(today.contains("c.positionInRoot().y + ridgeScrollPx()"))
        assertTrue(today.contains("exitPx = ridgeExitPx"))
        assertTrue(source("main/java/com/noop/ui/RidgeTheme.kt")
            .contains("translationY = -size.height * backdropExitProgress(scrollPx(), exitPx())"))
        assertTrue(source("main/java/com/noop/ui/ridge/RidgePageBackdrop.kt").contains("translationY = -scrollPx()"))
        val components = source("main/java/com/noop/ui/Components.kt")
        assertEquals("both shared scaffolds provide the scroll", 2, components.split("provides pageScrollPx").size - 1)
        assertTrue(components.contains("rememberListScrollPx(listState, rowSpacing)"))
        assertTrue(components.contains(".verticalScroll(pageScroll)"))
    }
}
