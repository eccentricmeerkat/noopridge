package com.noop.ui.ridge

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.noop.ui.Ridge
import com.noop.ui.RidgeTokens
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-01 / D-10: dark-only Ridge must keep every text token readable on every surface it sits on. WCAG 2.x
 * contrast: ≥ 4.5:1 for body text (all three ink tokens), ≥ 3:1 for large or bold numerals. Translucent
 * surfaces are composited over the ground they float on, as they render.
 */
class RidgeContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble(); val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val surfaces: Map<String, Color> = mapOf(
        "surfaceBase" to RidgeTokens.surfaceBase,
        "surfaceOverlay" to RidgeTokens.surfaceOverlay,
        "surfaceRaised" to RidgeTokens.surfaceRaised,
        "surfaceInset" to RidgeTokens.surfaceInset,
        "hairlineStrong" to RidgeTokens.hairlineStrong,
        "glassFillStrong/bg100" to Ridge.glassFillStrong.compositeOver(Ridge.bg100),
        "heroFill/bg000" to RidgeTokens.heroFill.compositeOver(Ridge.bg000),
        "glassFillBottom/bg000" to RidgeTokens.glassFillBottom.compositeOver(Ridge.bg000),
    )

    @Test
    fun everyInkTokenPassesBodyContrastOnEverySurface() {
        val inks = mapOf("ink" to Ridge.ink, "inkMuted" to Ridge.inkMuted, "inkSubtle" to Ridge.inkSubtle)
        val failures = inks.flatMap { (iname, ink) ->
            surfaces.mapNotNull { (sname, s) ->
                ratio(ink, s).takeIf { it < 4.5 }?.let { "$iname on $sname = ${"%.2f".format(it)}" }
            }
        }
        assertTrue("Below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun accentAndStateColoursPassLargeTextContrastOnTheGround() {
        val large = mapOf(
            "brand" to Ridge.brand, "charge" to Ridge.charge, "effort" to Ridge.effort,
            "stateDepleted" to Ridge.stateDepleted, "stateLow" to Ridge.stateLow,
            "stateModerate" to Ridge.stateModerate, "stateGood" to Ridge.stateGood,
            "dataHrv" to Ridge.dataHrv, "dataHeart" to Ridge.dataHeart,
            "dataEnergy" to Ridge.dataEnergy, "dataBreath" to Ridge.dataBreath,
            // D-13 (R4a): sleep stage segments are separated by a ground gap, so each meets 3:1 on the ground.
            // Deep is the exception (BS-x2-2 item 8): see [sleepDeepStaysVisibleOnTheGround].
            "sleepCore" to Ridge.sleepCore, "sleepLight" to Ridge.sleepLight, "sleepRem" to Ridge.sleepRem, "sleepAwake" to Ridge.sleepAwake,
        )
        val failures = large.flatMap { (n, c) ->
            listOf("bg000" to Ridge.bg000, "bg100" to Ridge.bg100, "bg200" to Ridge.bg200).mapNotNull { (bn, b) ->
                ratio(c, b).takeIf { it < 3.0 }?.let { "$n on $bn = ${"%.2f".format(it)}" }
            }
        }
        assertTrue("Below 3:1: $failures", failures.isEmpty())
    }

    /**
     * BS-x2-2 item 8: Deep is the owner's dark indigo (#4A2FB0, from the reference image), which reaches about 2.0:1
     * on the darkest ground and 1.7:1 on the lightest, under the 3:1 the other stages meet. It is a deliberate,
     * owner-chosen exception; this keeps it from fading further.
     */
    @Test
    fun sleepDeepStaysVisibleOnTheGround() {
        val failures = listOf("bg000" to Ridge.bg000, "bg100" to Ridge.bg100, "bg200" to Ridge.bg200)
            .mapNotNull { (n, b) -> ratio(Ridge.sleepDeep, b).takeIf { it < 1.5 }?.let { "$n = ${"%.2f".format(it)}" } }
        assertTrue("sleepDeep below 1.5:1 on: $failures", failures.isEmpty())
    }

    @Test
    fun inkOnLiquidReadsOnEachOrbLiquid() {
        val failures = listOf("charge" to Ridge.charge, "effort" to Ridge.effort, "rest" to Ridge.rest)
            .mapNotNull { (n, c) -> ratio(Ridge.inkOnLiquid, c).takeIf { it < 4.5 }?.let { "$n = ${"%.2f".format(it)}" } }
        assertTrue("inkOnLiquid below 4.5:1 on: $failures", failures.isEmpty())
    }
}
