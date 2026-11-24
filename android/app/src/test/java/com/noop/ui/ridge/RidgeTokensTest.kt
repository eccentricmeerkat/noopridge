package com.noop.ui.ridge

import androidx.compose.ui.graphics.Color
import com.noop.ui.Ridge
import com.noop.ui.RidgeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * B-01: `design/prototype/noop-tokens.css` is the token source of truth (Sprint 1, Q5). Every colour token the
 * CSS defines must equal its [Ridge] binding, so a token is changed in one place and this test says where it
 * drifted. Also pins that [RidgeTokens] reads the ground, ink and liquids from [Ridge].
 */
class RidgeTokensTest {

    private val css: Map<String, String> by lazy {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "design/prototype/noop-tokens.css") }.first { it.exists() }
        Regex("""--([a-z0-9-]+):\s*([^;]+);""").findAll(f.readText())
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    private fun resolve(name: String): String {
        var v = css.getValue(name)
        while (v.startsWith("var(")) v = css.getValue(v.removePrefix("var(--").removeSuffix(")"))
        return v
    }

    /** RGBA 0..255 + alpha to 2 dp, from `#rrggbb` or `rgba(r, g, b, a)`. */
    private fun cssRgba(name: String): List<Int> {
        val v = resolve(name)
        return if (v.startsWith("#")) {
            listOf(1, 3, 5).map { v.substring(it, it + 2).toInt(16) } + 100
        } else {
            val p = v.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
            p.take(3).map { it.toInt() } + (p[3].toDouble() * 100).roundToInt()
        }
    }

    private fun rgba(c: Color): List<Int> =
        listOf(c.red, c.green, c.blue).map { (it * 255).roundToInt() } + (c.alpha * 100).roundToInt()

    private val bindings: Map<String, Color> = mapOf(
        "bg-000" to Ridge.bg000, "bg-100" to Ridge.bg100, "bg-200" to Ridge.bg200,
        "bg-300" to Ridge.bg300, "bg-400" to Ridge.bg400,
        "glass-fill" to Ridge.glassFill, "glass-fill-strong" to Ridge.glassFillStrong,
        "glass-stroke" to Ridge.glassStroke, "glass-highlight" to Ridge.glassHighlight,
        "ink" to Ridge.ink, "ink-muted" to Ridge.inkMuted, "ink-subtle" to Ridge.inkSubtle,
        "ink-on-liquid" to Ridge.inkOnLiquid,
        "brand" to Ridge.brand, "brand-deep" to Ridge.brandDeep,
        "charge" to Ridge.charge, "charge-deep" to Ridge.chargeDeep,
        "effort" to Ridge.effort, "effort-deep" to Ridge.effortDeep,
        "rest" to Ridge.rest, "rest-deep" to Ridge.restDeep,
        "battery" to Ridge.battery, "battery-deep" to Ridge.batteryDeep,
        "state-depleted" to Ridge.stateDepleted, "state-low" to Ridge.stateLow,
        "state-moderate" to Ridge.stateModerate, "state-good" to Ridge.stateGood, "state-ready" to Ridge.stateReady,
        "data-hrv" to Ridge.dataHrv, "data-heart" to Ridge.dataHeart,
        "data-energy" to Ridge.dataEnergy, "data-breath" to Ridge.dataBreath,
        "focus-ring" to Ridge.focusRing,
    )

    @Test
    fun everyCssColourTokenMatchesItsRidgeBinding() {
        val colourTokens = css.keys.filter { k ->
            val v = resolve(k); v.startsWith("#") || v.startsWith("rgba(")
        }.filterNot { it.startsWith("shadow") || it.startsWith("glow") }
        assertEquals("CSS colour tokens without a Ridge binding", emptyList<String>(), colourTokens - bindings.keys)
        for ((name, color) in bindings) assertEquals(name, cssRgba(name), rgba(color))
    }

    @Test
    fun spacingAndRadiiMatchTheCss() {
        fun px(name: String) = resolve(name).removeSuffix("px").toFloat()
        assertEquals(px("space-1"), Ridge.space1.value); assertEquals(px("space-2"), Ridge.space2.value)
        assertEquals(px("space-3"), Ridge.space3.value); assertEquals(px("space-4"), Ridge.space4.value)
        assertEquals(px("space-5"), Ridge.space5.value); assertEquals(px("space-6"), Ridge.space6.value)
        assertEquals(px("space-8"), Ridge.space8.value); assertEquals(px("space-10"), Ridge.space10.value)
        assertEquals(px("radius-sm"), Ridge.radiusSm.value); assertEquals(px("radius-md"), Ridge.radiusMd.value)
        assertEquals(px("radius-lg"), Ridge.radiusLg.value); assertEquals(px("radius-xl"), Ridge.radiusXl.value)
        assertEquals(px("radius-pill"), Ridge.radiusPill.value)
    }

    @Test
    fun paletteRidgeSetReadsTheRidgeGroundInkAndLiquids() {
        assertEquals(Ridge.bg000, RidgeTokens.surfaceBase)
        assertEquals(Ridge.ink, RidgeTokens.textPrimary)
        assertEquals(Ridge.inkMuted, RidgeTokens.textSecondary)
        assertEquals(Ridge.inkSubtle, RidgeTokens.textTertiary)
        assertEquals(Ridge.brand, RidgeTokens.accent)
        assertEquals(Ridge.charge, RidgeTokens.chargeColor)
        assertEquals(Ridge.effort, RidgeTokens.effortColor)
        assertEquals(Ridge.rest, RidgeTokens.restColor)
        assertTrue(Ridge.states == listOf(RidgeTokens.recovery000, RidgeTokens.recovery030,
            RidgeTokens.recovery055, RidgeTokens.recovery078, RidgeTokens.recovery100))
    }
}
