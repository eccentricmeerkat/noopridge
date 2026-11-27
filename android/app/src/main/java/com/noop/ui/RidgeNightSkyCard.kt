package com.noop.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

// RS-3g: the Sleep score's night-sky card, from the Sleep page design; RS-3h turns its lights down.

/** One star: its place as a share of the drifting layer, its size in dp, and how it twinkles. */
internal data class SkyStar(val x: Float, val y: Float, val sizeDp: Float, val peak: Float, val periodMs: Float, val delayMs: Float)

/** The design's seeded generator, so the stars sit where its artboard puts them. Its two layers share one sequence. */
internal fun skyStarLayers(): Pair<List<SkyStar>, List<SkyStar>> {
    var seed = 7
    fun next(): Float {
        seed = (seed * 9301 + 49297) % 233280
        return seed / 233280f
    }
    fun layer(count: Int, bright: Boolean) = List(count) { i ->
        val size = if (bright && i % 7 == 0) 3.4f else if (next() > 0.7f) 2.2f else 1.6f
        val peak = 0.7f + next() * 0.3f
        val x = next()
        val y = next()
        val period = 3f + next() * 5f
        val delay = next() * 8f
        SkyStar(x, y, size, peak, period * 1000f, delay * 1000f)
    }
    val first = layer(42, bright = true)
    return first to layer(34, bright = false)
}

/** A star's opacity at [timeMs]: the design's twinkle, easing from 0.12 up to 1 and back each period. */
internal fun twinkle(star: SkyStar, timeMs: Long): Float {
    val phase = ((timeMs + star.delayMs) % star.periodMs) / star.periodMs
    return 0.12f + 0.88f * (0.5f - 0.5f * cos(phase * 2f * PI.toFloat()))
}

/** How far along a drifting layer is, 0 to 1 and back again, taking [legMs] each way. */
internal fun drift(timeMs: Long, legMs: Long): Float {
    val t = (timeMs % (2 * legMs)).toFloat() / legMs
    return if (t <= 1f) t else 2f - t
}

/** The card's shell: outer glow, night gradient, nebula, stars and the two lime flares, with [content] on top. */
@Composable
internal fun NightSkyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val still = rememberPoseStill()
    val (near, far) = remember { skyStarLayers() }
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(still) {
        while (!still) withInfiniteAnimationFrameMillis { time = it }
    }
    val shape = RoundedCornerShape(Ridge.radiusLg)
    Box(modifier.drawBehind { drawOuterGlow() }.drawWithContent { drawContent(); drawFlares() }) {
        Canvas(Modifier.matchParentSize().clip(shape).clearAndSetSemantics {}) {
            // 165° in CSS: nearly straight down, leaning a little to the right.
            drawRect(
                Brush.linearGradient(
                    0f to RidgeNightSky.top, 0.55f to RidgeNightSky.middle, 1f to RidgeNightSky.bottom,
                    start = Offset(size.width * 0.35f, 0f), end = Offset(size.width * 0.65f, size.height),
                ),
            )
            drawNebula()
            // The time is read here, in the draw pass, so a frame redraws the sky without recomposing the card.
            val t = if (still) 0L else time
            drawStars(near, t, if (still) 0f else drift(t, 70_000L), -26f, 12f, still)
            drawStars(far, t, if (still) 0f else drift(t, 95_000L), 18f, -9f, still)
            drawInnerGlow()
        }
        content()
    }
}

/** RS-3h: the stars at a little over half the design's brightness. */
private const val STAR_STRENGTH = 0.6f

private fun DrawScope.drawStars(stars: List<SkyStar>, timeMs: Long, at: Float, dxDp: Float, dyDp: Float, still: Boolean) {
    // Each layer reaches 24 dp past the card, so its drift never shows an edge.
    val bleed = 24.dp.toPx()
    val w = size.width + 2 * bleed
    val h = size.height + 2 * bleed
    stars.forEach { star ->
        val alpha = STAR_STRENGTH * if (still) star.peak else twinkle(star, timeMs)
        val r = star.sizeDp.dp.toPx() / 2f
        val c = Offset(-bleed + star.x * w + at * dxDp.dp.toPx() + r, -bleed + star.y * h + at * dyDp.dp.toPx() + r)
        val halo = r * 5f
        drawCircle(
            Brush.radialGradient(
                0f to RidgeNightSky.starGlow.copy(alpha = 0.45f * alpha), 1f to Color.Transparent, center = c, radius = halo,
            ),
            radius = halo, center = c,
        )
        drawCircle(RidgeNightSky.star.copy(alpha = alpha), radius = r, center = c)
    }
}

/** A soft ellipse: [color] at the centre fading to nothing at the edge of [width] by [height]. */
private fun DrawScope.softEllipse(color: Color, center: Offset, width: Float, height: Float, fadeBy: Float = 1f) {
    val radius = width / 2f
    scale(1f, height / width, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to color, fadeBy to color.copy(alpha = 0f), center = center, radius = radius),
            radius = radius, center = center,
        )
    }
}

private fun DrawScope.drawNebula() {
    // The design's box: 20% past the left edge, 30% below the foot, 70% by 80% of the card.
    val w = size.width * 0.7f
    val h = size.height * 0.8f
    softEllipse(RidgeNightSky.nebula.copy(alpha = 0.18f), Offset(-size.width * 0.2f + w / 2f, size.height * 1.3f - h / 2f), w, h)
}

private fun DrawScope.blurredCard(color: Color, blur: Float, stroke: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        val r = Ridge.radiusLg.toPx()
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}

private fun DrawScope.drawOuterGlow() {
    blurredCard(RidgeNightSky.glow.copy(alpha = 0.16f), 14.dp.toPx(), 8.dp.toPx())
}

/** Drawn inside the clip: the inset glow, then the blue hairline. */
private fun DrawScope.drawInnerGlow() {
    blurredCard(RidgeNightSky.glowFar.copy(alpha = 0.22f), 20.dp.toPx(), 24.dp.toPx())
    // Twice the hairline's width, as the clip keeps only the inner half.
    blurredCard(RidgeNightSky.stroke, 0.5f, 2.dp.toPx())
}

private fun DrawScope.drawFlares() {
    val thick = 6.dp.toPx()
    softEllipse(RidgeNightSky.flare.copy(alpha = 0.28f), Offset(size.width / 2f, -0.5.dp.toPx()), 150.dp.toPx(), thick, 0.9f)
    softEllipse(RidgeNightSky.flare.copy(alpha = 0.18f), Offset(size.width / 2f, size.height + 0.5.dp.toPx()), 130.dp.toPx(), thick, 0.9f)
}

/** The orb's halo: a wide blue light with a fainter lime one close in, [reach] out from the orb's centre. */
internal fun DrawScope.drawOrbHalo(reach: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    fun ring(color: Color, alpha: Float, at: Float, radius: Float) = drawCircle(
        Brush.radialGradient(0f to color.copy(alpha = alpha), at to color.copy(alpha = alpha), 1f to color.copy(alpha = 0f), center = c, radius = radius),
        radius = radius, center = c,
    )
    ring(RidgeNightSky.orbGlow, 0.22f, 0.74f, reach)
    ring(RidgeNightSky.flare, 0.06f, 0.86f, reach * 0.9f)
}

