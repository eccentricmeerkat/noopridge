package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** SQ3-3 (doc 4, as eased by the owner's review): the lightly frosted hero panel, its blur and the translucent orbs. */
class TodayHeroPanelTest {

    private fun source(path: String): String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/$path").takeIf { f -> f.isFile } ?: File(it, "src/$path") }
        .first { it.isFile }.readText()

    private fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(p: Int) = (p shr 16) and 0xFF

    @Test
    fun aUniformImageStaysUniform() {
        val img = IntArray(12 * 9) { px(40, 90, 200) }
        val out = gaussianBlurRgb(img, 12, 9, 3f)
        assertTrue(out.all { it == px(40, 90, 200) })
    }

    @Test
    fun aSinglePointSpreadsSymmetricallyAndKeepsItsLight() {
        val w = 41; val h = 41
        val img = IntArray(w * h) { px(0, 0, 0) }
        img[20 * w + 20] = px(255, 255, 255)
        val out = gaussianBlurRgb(img, w, h, 3f)
        assertTrue("the peak drops", red(out[20 * w + 20]) in 1..60)
        for (d in 1..8) {
            assertEquals(red(out[20 * w + 20 - d]), red(out[20 * w + 20 + d]))
            assertEquals(red(out[(20 - d) * w + 20]), red(out[(20 + d) * w + 20]))
        }
        assertTrue(red(out[20 * w + 22]) > red(out[20 * w + 26]))
        assertTrue(red(out[20 * w + 24]) > 0)
        val total = out.sumOf { red(it) }
        assertTrue("total $total", total in 220..290)
    }

    @Test
    fun aHardEdgeBecomesASoftRamp() {
        val w = 40; val h = 4
        val img = IntArray(w * h) { px(if (it % w < 20) 0 else 255, 0, 0) }
        val out = gaussianBlurRgb(img, w, h, 4f)
        val row = (0 until w).map { red(out[it]) }
        assertTrue(row.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(row[19] in 40..140 && row[20] in 115..215)
        assertEquals(0, row[0]); assertEquals(255, row[w - 1])
    }

    @Test
    fun boxSizesAreOddAndMatchTheGaussianVariance() {
        for (sigma in listOf(2f, 5f, 7.2f, 12f)) {
            val sizes = boxSizesForGaussian(sigma, 3)
            assertTrue(sizes.all { it % 2 == 1 && it >= 1 })
            val variance = sizes.sumOf { (it * it - 1) / 12.0 }
            assertEquals("sigma $sigma", sigma * sigma.toDouble(), variance, sigma * 0.9)
        }
    }

    @Test
    fun panelAndGapFollowTheBrief() {
        // B2-1a (Main.dc.html .np-orb-card): 28 radius, a 16% to 8% white-blue wash, a 28% border, blur 2, 28 below the header.
        assertEquals(28f, HERO_PANEL_RADIUS.value, 0f)
        assertEquals(0.16f, HERO_PANEL_FILL_TOP_ALPHA, 0f)
        assertEquals(0.08f, HERO_PANEL_FILL_BOTTOM_ALPHA, 0f)
        assertEquals(0.28f, HERO_PANEL_BORDER_ALPHA, 0f)
        assertEquals(2f, HERO_PANEL_BLUR.value, 0f)
        assertEquals(53f, HERO_GAP_BELOW_HEADER.value, 0f)
    }

    @Test
    fun todayUsesThePanelAndTheGapAndTheOrbsAreTranslucent() {
        val today = source("main/java/com/noop/ui/TodayScreen.kt")
        assertTrue(today.contains("RidgeHeroPanel("))
        assertTrue(today.contains("HERO_GAP_BELOW_HEADER - 12.dp - 2.dp"))
        assertTrue(today.contains("blurBackdrop = showDayCycleBackground && !BackgroundImageStore.isActive"))
        val theme = source("main/java/com/noop/ui/RidgeTheme.kt")
        val glass = theme.substringAfter("private fun DrawScope.drawGlassOrb(").substringBefore("// Surface height in the 100-unit box")
        assertFalse("no opaque disc behind the liquid", glass.contains("lerp(Ridge.bg200"))
        assertTrue(glass.contains("liquid.top.copy(alpha = 0.10f)"))
        // The panel always draws its own tint and border, whatever the blur is doing.
        val panel = source("main/java/com/noop/ui/TodayHeroPanel.kt")
        assertTrue(panel.contains("HERO_PANEL_FILL_TOP.copy(alpha = HERO_PANEL_FILL_TOP_ALPHA * ready)"))
        assertTrue(panel.contains(".border(1.dp, HERO_PANEL_BORDER.copy(alpha = HERO_PANEL_BORDER_ALPHA), shape)"))
    }
}
