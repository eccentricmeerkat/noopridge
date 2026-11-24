package com.noop.ui.ridge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import com.noop.ui.rememberPoseStill

/** What the strap battery orb shows (SQ8a, A4). */
internal enum class StrapOrbState {
    /** Connected with a reading: liquid at the battery level and the number. */
    LEVEL,
    /** Connected, no reading yet: the glass and a neutral ring, no liquid, no number. */
    UNKNOWN,
    /** Not connected: a grey-blue ring and the Bluetooth glyph (owner, 15 Nov 2026: the orb is also the way to connect). */
    DISCONNECTED,
    /** On the charger: a mint ring with a glow and the bolt. */
    CHARGING,
}

/** The battery tiers of `SQ8_HEADER_NAV_TODAY_SPACING_HUB_TILES.md` A4. Full and High share their colours. */
internal enum class StrapBatteryTier(val ring: Color, val liquid: Color, val glow: Color) {
    FULL(Ridge.strapOrbHighRing, Ridge.strapOrbHighLiquid, Ridge.strapOrbHighGlow),
    HIGH(Ridge.strapOrbHighRing, Ridge.strapOrbHighLiquid, Ridge.strapOrbHighGlow),
    MEDIUM(Ridge.strapOrbMediumRing, Ridge.strapOrbMediumLiquid, Ridge.strapOrbMediumGlow),
    LOW(Ridge.strapOrbLowRing, Ridge.strapOrbLowLiquid, Ridge.strapOrbLowGlow),
    CRITICAL(Ridge.strapOrbCriticalRing, Ridge.strapOrbCriticalLiquid, Ridge.strapOrbCriticalGlow),
}

/** The tier for a whole battery percentage: 100 full, 70–99 high, 40–69 medium, 10–39 low, 9 or less critical. */
internal fun strapBatteryTier(pct: Int): StrapBatteryTier = when {
    pct >= 100 -> StrapBatteryTier.FULL
    pct >= 70 -> StrapBatteryTier.HIGH
    pct >= 40 -> StrapBatteryTier.MEDIUM
    pct >= 10 -> StrapBatteryTier.LOW
    else -> StrapBatteryTier.CRITICAL
}

/**
 * The strap battery orb (SQ8a, A4; Main.dc.html): navy-grey glass, a 2 dp ring with a soft glow in the tier colour, and
 * in [StrapOrbState.LEVEL] a liquid fill to the battery level with a gentle wave and the number in Playfair. No gloss.
 * [syncing] adds a thin spinning arc inside the ring (still under reduced motion); [scanning] dims the disconnected glyph to
 * Bluetooth-searching. Shared by the Today header (31 dp)
 * and the Hub Device tile (34 dp). The caller owns the tap target and the content description.
 */
@Composable
internal fun StrapBatteryOrb(
    pct: Int?,
    state: StrapOrbState,
    size: Dp,
    syncing: Boolean = false,
    scanning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val level = pct?.coerceIn(0, 100)
    val tier = level?.let(::strapBatteryTier)
    val ring = when (state) {
        StrapOrbState.LEVEL -> tier?.ring ?: Ridge.strapOrbOffRing
        StrapOrbState.CHARGING -> Ridge.strapOrbChargingRing
        StrapOrbState.UNKNOWN, StrapOrbState.DISCONNECTED -> Ridge.strapOrbOffRing
    }
    val glow = when (state) {
        StrapOrbState.LEVEL -> tier?.glow
        StrapOrbState.CHARGING -> Ridge.strapOrbChargingGlow
        else -> null
    }
    // The prototype scales from a 37 px orb: 2 px ring, 19 px glyphs, a 14.5 px number.
    val unit = size / 37f
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                // The soft glow (box-shadow 0 0 12px): about half strength at the rim, gone 12 px (scaled) beyond it.
                if (glow != null) {
                    val r = this.size.minDimension / 2f
                    val outer = r + 12.dp.toPx() * unit.value
                    val rim = glow.copy(alpha = glow.alpha * 0.5f)
                    drawCircle(
                        Brush.radialGradient(
                            0f to rim, r / outer to rim, 1f to Color.Transparent,
                            center = center, radius = outer,
                        ),
                        radius = outer,
                    )
                }
            }
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Ridge.strapOrbGlassTop, Ridge.strapOrbGlassBottom)))
            .border(2.dp * unit.value, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StrapOrbState.LEVEL -> if (level != null && tier != null) {
                Canvas(Modifier.size(size - 4.dp * unit.value)) {
                    // Main.dc.html: in a 40-unit box, level y = 3 + (1 − p) × 34, one soft quadratic wave 3 units deep.
                    val k = this.size.width / 40f
                    val y = (3f + (1f - level / 100f) * 34f) * k
                    val wave = Path().apply {
                        moveTo(0f, y)
                        quadraticBezierTo(10f * k, y - 3f * k, 20f * k, y)
                        quadraticBezierTo(30f * k, y + 3f * k, 40f * k, y)
                        lineTo(40f * k, 40f * k)
                        lineTo(0f, 40f * k)
                        close()
                    }
                    drawPath(wave, tier.liquid.copy(alpha = 0.92f))
                }
                Text(
                    level.toString(),
                    style = TextStyle(
                        fontFamily = RidgeType.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = (14.5f * unit.value).sp,
                        lineHeight = (14.5f * unit.value).sp,
                        color = Ridge.strapOrbNumber,
                        shadow = Shadow(Ridge.strapOrbNumberShadow, Offset(0f, 1f), blurRadius = 4f),
                        // Lining numerals (`font-variant-numeric: lining-nums`): Playfair's default figures are old-style.
                        fontFeatureSettings = "lnum",
                    ),
                )
            }
            // Searching while a scan runs, dimmed, as the old connect disc did (#2169).
            StrapOrbState.DISCONNECTED -> Icon(
                if (scanning) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth, contentDescription = null,
                tint = Ridge.strapOrbOffGlyph.copy(alpha = if (scanning) 0.55f else 1f), modifier = Modifier.size(19.dp * unit.value),
            )
            StrapOrbState.CHARGING -> Icon(
                painterResource(R.drawable.ic_ridge_bolt), contentDescription = null,
                tint = Ridge.strapOrbChargingBolt, modifier = Modifier.size(19.dp * unit.value),
            )
            StrapOrbState.UNKNOWN -> Unit
        }
        if (syncing && state == StrapOrbState.LEVEL) SyncArc(size - 4.dp * unit.value)
    }
}

/** The syncing spinner (Main.dc.html `np-spin`): a quarter arc inside the ring, one turn a second; still under reduced motion. */
@Composable
private fun SyncArc(diameter: Dp) {
    val still = rememberPoseStill()
    val turn = if (still) 0f else {
        val t = rememberInfiniteTransition(label = "sync")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")
        a
    }
    Canvas(Modifier.size(diameter)) {
        // The prototype's arc: radius 18 of a 44-unit box, stroke 2, 90°.
        val k = size.width / 44f
        val r = 18f * k
        drawArc(
            color = Ridge.strapOrbNumber.copy(alpha = 0.9f), startAngle = turn - 90f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r),
            style = Stroke(width = 2f * k, cap = StrokeCap.Round),
        )
    }
}
