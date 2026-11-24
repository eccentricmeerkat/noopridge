package com.noop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SQ3-1: the Ridge canvas. The shell no longer pads the status bar; each page's scaffold does and draws its backdrop
 * from the very top; no black system-bar strip; Today's header carries no wordmark, tagline or script line.
 */
class RidgeCanvasTest {

    private fun source(path: String): String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/$path").takeIf { f -> f.isFile } ?: File(it, "src/$path") }
        .first { it.isFile }.readText()

    private val appRoot get() = source("main/java/com/noop/ui/AppRoot.kt")
    private val components get() = source("main/java/com/noop/ui/Components.kt")

    @Test
    fun shellInsetsNothingAndPagesOwnTheStatusBar() {
        assertTrue(appRoot.contains("contentWindowInsets = if (ridgeShell) WindowInsets(0, 0, 0, 0)"))
        assertTrue(appRoot.contains("CompositionLocalProvider(LocalPageOwnsStatusBar provides ridgeShell)"))
        // Both shared scaffolds add the status bar to their content and drop the backdrop's upward offset.
        assertTrue(components.contains("top = topPadding + statusInset"))
        assertTrue(components.split("top = topPadding + statusInset").size == 3 || components.contains("start = 28.dp, top = topPadding + statusInset"))
        assertTrue(components.contains("if (LocalPageOwnsStatusBar.current) 0.dp else -statusBarTop"))
    }

    @Test
    fun canvasIsRidgeNavyNotNearBlack() {
        assertTrue(components.contains("if (ridgeShell) Ridge.bg100 else Palette.surfaceBase"))
        assertFalse("scaffolds must not paint the v10 surface under the backdrop", components.contains("background(Palette.surfaceBase)) {\n            Box(\n                modifier = ("))
        assertTrue(source("main/res/values/themes.xml").contains("@color/ridge_bg100</item>"))
    }

    @Test
    fun navigationBarScrimIsOff() {
        assertTrue(source("main/java/com/noop/ui/MainActivity.kt").contains("isNavigationBarContrastEnforced = false"))
    }

    @Test
    fun todayHeaderHasNoBrandingAndItsStringsAreGone() {
        val header = source("main/java/com/noop/ui/TodayHeader.kt")
        assertFalse(header.contains("ridge_tagline"))
        assertFalse(header.contains("ridge_script_line"))
        assertFalse(header.contains("RidgeType.wordmark"))
        for (loc in listOf("values", "values-de", "values-es", "values-fr", "values-pl", "values-pt-rPT", "values-ru", "values-zh")) {
            val strings = source("main/res/$loc/strings.xml")
            assertFalse("$loc", strings.contains("name=\"ridge_script_line\"") || strings.contains("name=\"ridge_tagline\""))
        }
    }
}
