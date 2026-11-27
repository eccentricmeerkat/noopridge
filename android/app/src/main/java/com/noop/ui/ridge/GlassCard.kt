package com.noop.ui.ridge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.noop.ui.Ridge
import com.noop.ui.ridgeGlass

/** GlassCard tones (design system p. 11): neutral by default; a liquid tone only when the card is about that
 *  metric; one alert card per screen at most. [Strong] is the one card that stands forward (orb trio, sheets). */
enum class GlassTone { Neutral, Strong, Charge, Effort, Rest, Alert }

/**
 * RS-2b: true inside a group box that is itself a [GlassCard] (Automations & Alarms). A card drawn there keeps its padding and
 * content but paints no glass of its own, so the box is the one glass layer. Off everywhere else.
 */
val LocalNestedGlass = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * A Noop Ridge glass card (B-02): glass fill over the ground, a 1 dp hairline, [radius] corners, no glow and
 * no nested glass. Tones wash the top-left corner and tint the hairline. Built on [ridgeGlass] so Today's
 * existing cards and new screens draw the same surface.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.Neutral,
    radius: Dp = Ridge.radiusLg,
    contentPadding: PaddingValues = PaddingValues(Ridge.space5),
    content: @Composable BoxScope.() -> Unit,
) {
    val tint = when (tone) {
        GlassTone.Neutral, GlassTone.Strong -> null
        GlassTone.Charge -> Ridge.charge
        GlassTone.Effort -> Ridge.effort
        GlassTone.Rest -> Ridge.rest
        GlassTone.Alert -> Ridge.stateDepleted
    }
    Box(
        modifier = modifier
            .then(if (LocalNestedGlass.current) Modifier else Modifier.ridgeGlass(radius = radius, strong = tone == GlassTone.Strong, tint = tint))
            .padding(contentPadding),
        content = content,
    )
}
