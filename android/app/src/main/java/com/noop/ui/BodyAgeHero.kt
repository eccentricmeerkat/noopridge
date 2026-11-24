package com.noop.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

// MARK: - The Baseline body-age hero (SQ10, `design reference/body age.png` + the board's `Baseline` hero)
//
// Body age is the one slow marker that gets a picture rather than a chart: the number sits inside eight nested
// contours that morph and breathe, the way a topographic map of the ridge the rest of Ridge draws would look. The
// contours carry no data — the number is the value, and the card's detail line says how sure the engine is — so
// they are free to loop; that also means they must stop, which they do through [rememberPoseStill] (Reduce
// motion, battery saver, Quiet motion) and by pausing while the app is not resumed. They pose at their first frame
// like every other looping surface in the app.
//
// The geometry is the board's own ([BODY_AGE_RING_KEYS]): each ring blends between three keyframe shapes on its
// own period, and pulses in opacity on a 6.5 s beat. That makes a frame cheap: a lerp over 35 points per ring, no
// trigonometry per point, no allocation (the paths, point buffer and brushes are built once per size). The whole
// animated picture lives in its own graphics layer, so a tick re-records that layer and not the screen around it.

private const val TAU = (2.0 * PI).toFloat()

/** How often the clock steps. Every step is a redraw of the hero's layer, so five a second is the budget: the
 *  rings' own periods are 11–17 s, which is slow enough that this reads as continuous. */
private const val TICK_MS = 200L

/** How much slower than the board the contours move. The board's periods (11–17 s a morph, a 6.5 s pulse) are a
 *  gentle drift on a 60 fps surface; drawn at [TICK_MS] steps they read as stepping, so the whole animation is
 *  slowed until each step moves a ring by a fraction of a pixel and the picture is barely moving. */
private const val MOTION_SLOWDOWN = 5L

/** The board's drawing space is 300 x 300 units; its centre. */
private const val BOARD_CENTRE = 150f

/** The share of the hero's half-size the board's 150 half-units span. */
private const val RING_SPAN = 0.88f

/** The board's rings are wider than tall; the PNG's are close to round, with an inner contour tall enough to hold
 *  the numeral, label and detail. Stretching the rings' height by this much gets there without touching their shape. */
private const val VERT_STRETCH = 1.18f

/** The rings' opacity pulse: 0.55 → 1 → 0.55 over this long, each ring a further [PULSE_STAGGER_MS] behind. */
private const val PULSE_MS = 6_500L
private const val PULSE_STAGGER_MS = 700L

/** Each ring's morph period grows by this much outwards from [RING_BASE_MS], and starts [RING_STAGGER_MS] later. */
private const val RING_BASE_MS = 11_000L
private const val RING_STEP_MS = 900L
private const val RING_STAGGER_MS = 1_300L

/** Where the board's morph switches keyframe pairs (0 → 1, 1 → 2, 2 → 0). */
private const val KEY_SWITCH_A = 0.33f
private const val KEY_SWITCH_B = 0.66f

/** The board's `.45 0 .55 1` morph easing. */
private val MORPH_EASE = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Rings whose edge also carries a soft halo: the inner ones, where the reference's light gathers. */
private const val HALO_RINGS = 3

/** The two accent nodes the board scatters inside the rings: board x, y, radius, pulse period, pulse offset (ms). */
private val NODES = arrayOf(
    floatArrayOf(62f, 86f, 2.6f, 2_400f, 1_800f),
    floatArrayOf(86f, 236f, 2.8f, 2_800f, 0f),
)

private val BODY_AGE_NUMBER = RidgeType.numeralXl.copy(fontSize = 88.sp, lineHeight = 92.sp)

/** The numeral's own label: 13 sp Inter semibold, opened up to 0.26 em like the board's tracking. */
private val BODY_AGE_LABEL = RidgeType.eyebrow.copy(
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.26.em,
)

/** The board's detail line is a pale mint — the ink pulled a third of the way to the brand. */
private val BODY_AGE_DETAIL = lerp(Ridge.ink, Ridge.brand, 0.35f)

/**
 * The body-age hero: [bodyAge] as a Playfair numeral inside eight morphing contours, its [label]
 * ("body age") beneath, and [detail] ("give or take 5 years · 1 under your 32") under that.
 *
 * [bodyAge] counts up on first composition through [CountUpText], so the numeral arrives the same way every
 * other headline score in Ridge does. The whole thing is one accessibility node reading [a11y]: the contours
 * and the nodes are decoration and say nothing of their own, so they are not announced.
 *
 * Sized square against the card's width (capped at 320 dp tall) so the contours keep their proportion from a
 * 320 dp phone to a tablet.
 */
@Composable
internal fun BodyAgeHero(
    bodyAge: Double,
    label: String,
    detail: String,
    a11y: String,
    modifier: Modifier = Modifier,
) {
    // A slow clock, not a 60 fps one: the contours loop over many seconds, so a few steps a second look the same
    // as a smooth sweep at a fraction of the cost. It only runs while the app is resumed (a backgrounded app keeps
    // its composition alive, and would otherwise keep stepping), and it poses at 0 when motion is off.
    val still = rememberPoseStill()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(still, lifecycle) {
        if (still) {
            clock.longValue = 0L
        } else {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(TICK_MS)
                    clock.longValue += TICK_MS
                }
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        // Its own layer, so a tick re-records this picture and not the card, the page, or the mountains behind
        // them. The clock is read inside the draw block: a tick invalidates the draw, never the composition. The
        // artwork (paths, brushes, point buffer) is built once per size by drawWithCache.
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer()
                .drawWithCache {
                    val art = BodyAgeArt(size, density)
                    onDrawBehind { art.draw(this, clock.longValue) }
                },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CountUpText(
                value = bodyAge,
                format = { it.roundToInt().toString() },
                style = BODY_AGE_NUMBER,
                color = Ridge.ink,
            )
            Text(label.uppercase(), style = BODY_AGE_LABEL, color = Ridge.inkMuted, maxLines = 1)
            Text(detail, style = TRN_SUB, color = BODY_AGE_DETAIL, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The hero's picture for one size: everything that depends only on the size is built here, once, so a frame is
 * only the lerps and the draw calls. [density] converts the few dp stroke widths.
 */
private class BodyAgeArt(private val size: Size, density: Float) {
    private val c = Offset(size.width / 2f, size.height / 2f)
    private val base = size.minDimension / 2f

    /** Pixels per board unit. */
    private val unit = base * RING_SPAN / BOARD_CENTRE

    private val paths = Array(RING_COUNT) { Path() }
    private val pts = FloatArray(RING_POINTS * 2)

    private val lineWidth = 1.2f * density
    private val haloWidth = 6f * density
    private val ridgeLight = lerp(Ridge.brand, Ridge.stateGood, 0.55f)

    // A contour is lightest at its top-left and deepest at its bottom-right, as the board's gradient runs.
    private val haloBrush = Brush.linearGradient(
        colors = listOf(Ridge.stateGood, Ridge.brand, Ridge.brandDeep),
        start = Offset(c.x - base, c.y - base),
        end = Offset(c.x + base, c.y + base),
    )

    // Mint light spread behind the rings — the board's soft glow ellipse, 150 x 128 units — as a radial fade.
    private val glowBrush = Brush.radialGradient(
        0f to Ridge.brand.copy(alpha = 0.08f),
        0.55f to Ridge.brand.copy(alpha = 0.06f),
        1f to Color.Transparent,
        center = c,
        radius = 150f * unit,
    )

    // The hollow the numeral sits in (the board's `agoCore`, 70 x 52 units): darkest right behind the number,
    // which is what lets the cream sit so cleanly on mint.
    private val coreBrush = Brush.radialGradient(
        0f to Ridge.bg000.copy(alpha = 0.95f),
        0.62f to Ridge.bg000.copy(alpha = 0.90f),
        1f to Color.Transparent,
        center = c,
        radius = 70f * unit,
    )

    fun draw(scope: DrawScope, clockMs: Long) {
        if (base <= 0f) return
        val elapsedMs = clockMs / MOTION_SLOWDOWN
        with(scope) {
            // Glow, as an ellipse: the radial fade drawn through a squash to the board's 150:128 ratio.
            withTransform({ scale(1f, 128f / 150f * VERT_STRETCH, pivot = c) }) {
                drawCircle(brush = glowBrush, radius = 150f * unit, center = c)
            }

            // Rings, innermost first, so each outer one lies behind the last. Inner rings are the brightest and
            // each outward is fainter, which is how the reference reads: light gathered at the core's edge.
            for (i in 0 until RING_COUNT) {
                val pulse = pulse(elapsedMs + PULSE_STAGGER_MS * i)
                morph(i, elapsedMs)
                val path = paths[i]
                buildPath(path)
                drawPath(path, color = Ridge.brand.copy(alpha = 0.025f * pulse))
                val edge = (0.62f - 0.05f * i) * pulse
                if (i < HALO_RINGS) {
                    drawPath(
                        path,
                        brush = haloBrush,
                        alpha = edge * 0.10f,
                        style = Stroke(width = haloWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                drawPath(
                    path,
                    color = ridgeLight.copy(alpha = edge),
                    style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            withTransform({ scale(1f, 52f / 70f * VERT_STRETCH, pivot = c) }) {
                drawCircle(brush = coreBrush, radius = 70f * unit, center = c)
            }

            drawNodes(elapsedMs)
        }
    }

    /**
     * Writes ring [ring]'s shape at [elapsedMs] into [pts], in pixels: the board's three keyframes blended with
     * its `.45 0 .55 1` easing, switching pair at 0.33 and 0.66 of the ring's period, the last pair closing the loop.
     */
    private fun morph(ring: Int, elapsedMs: Long) {
        val period = RING_BASE_MS + RING_STEP_MS * ring
        val t = ((elapsedMs + RING_STAGGER_MS * ring) % period).toFloat() / period
        val from: Int
        val to: Int
        val f: Float
        when {
            t < KEY_SWITCH_A -> { from = 0; to = 1; f = t / KEY_SWITCH_A }
            t < KEY_SWITCH_B -> { from = 1; to = 2; f = (t - KEY_SWITCH_A) / (KEY_SWITCH_B - KEY_SWITCH_A) }
            else -> { from = 2; to = 0; f = (t - KEY_SWITCH_B) / (1f - KEY_SWITCH_B) }
        }
        val e = MORPH_EASE.transform(f.coerceIn(0f, 1f))
        val a = BODY_AGE_RING_KEYS[ring][from]
        val b = BODY_AGE_RING_KEYS[ring][to]
        var i = 0
        while (i < pts.size) {
            pts[i] = c.x + (a[i] + (b[i] - a[i]) * e - BOARD_CENTRE) * unit
            pts[i + 1] = c.y + (a[i + 1] + (b[i + 1] - a[i + 1]) * e - BOARD_CENTRE) * unit * VERT_STRETCH
            i += 2
        }
    }

    /**
     * Rewinds [path] and traces [pts] as a closed Catmull-Rom spline (as cubics), so the line stays smooth at the
     * low sample count the battery budget allows. The same [Path] object is reused every frame.
     */
    private fun buildPath(path: Path) {
        val n = RING_POINTS
        path.rewind()
        path.moveTo(pts[0], pts[1])
        for (i in 0 until n) {
            val i0 = ((i + n - 1) % n) * 2
            val i1 = i * 2
            val i2 = ((i + 1) % n) * 2
            val i3 = ((i + 2) % n) * 2
            path.cubicTo(
                pts[i1] + (pts[i2] - pts[i0]) / 6f, pts[i1 + 1] + (pts[i2 + 1] - pts[i0 + 1]) / 6f,
                pts[i2] - (pts[i3] - pts[i1]) / 6f, pts[i2 + 1] - (pts[i3 + 1] - pts[i1 + 1]) / 6f,
                pts[i2], pts[i2 + 1],
            )
        }
        path.close()
    }

    /** The board's two floating nodes: each pulses 0.25 → 1 in opacity and 0.7 → 1.3 in size on its own beat. */
    private fun DrawScope.drawNodes(elapsedMs: Long) {
        for (node in NODES) {
            val period = node[3].toLong()
            val s = 0.5f - 0.5f * cos(TAU * ((elapsedMs + node[4].toLong()) % period).toFloat() / period)
            val at = Offset(c.x + (node[0] - BOARD_CENTRE) * unit, c.y + (node[1] - BOARD_CENTRE) * unit * VERT_STRETCH)
            val r = node[2] * unit * (0.7f + 0.6f * s)
            val a = 0.25f + 0.75f * s
            drawCircle(Ridge.brand.copy(alpha = 0.16f * a), radius = r * 2.6f, center = at)
            drawCircle(Ridge.ink.copy(alpha = 0.9f * a), radius = r, center = at)
        }
    }
}

/** The rings' opacity at [timeMs] on the pulse clock: 0.55 → 1 → 0.55 over [PULSE_MS], eased like the board. */
private fun pulse(timeMs: Long): Float =
    0.775f - 0.225f * cos(TAU * (timeMs % PULSE_MS).toFloat() / PULSE_MS)

/**
 * The faint ridge silhouettes behind the card, so the hero sits on ground rather than on flat glass: three
 * rolling layers, each a sum of sines tapering away below its crest. Sized by the caller to the top of the
 * card. Decorative.
 */
@Composable
internal fun BodyAgeCardRidges(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        ridgeLayer(baseY = size.height * 0.50f, amp = size.height * 0.18f, k = 5.6f, seed = 0.6f,
            color = Ridge.bg400.copy(alpha = 0.22f))
        ridgeLayer(baseY = size.height * 0.70f, amp = size.height * 0.22f, k = 3.6f, seed = 2.2f,
            color = Ridge.bg300.copy(alpha = 0.30f))
        ridgeLayer(baseY = size.height * 0.92f, amp = size.height * 0.24f, k = 2.3f, seed = 4.1f,
            color = Ridge.bg200.copy(alpha = 0.42f))
    }
}

private fun DrawScope.ridgeLayer(baseY: Float, amp: Float, k: Float, seed: Float, color: Color) {
    val path = Path().apply {
        moveTo(0f, baseY)
        val steps = 48
        for (i in 1..steps) {
            val x = size.width * i / steps
            val t = x / size.width
            val y = baseY + amp * (
                sin(t * k + seed) * 0.62f +
                    sin(t * k * 2.3f + seed * 1.7f) * 0.28f +
                    sin(t * k * 4.4f + seed * 2.9f) * 0.10f
                )
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(
        path,
        brush = Brush.verticalGradient(
            0f to color,
            1f to Color.Transparent,
            startY = baseY - amp,
            // Coerced so the fade always reaches transparent inside the canvas: a gradient that ran past the
            // bottom would leave a hard edge where the canvas ends.
            endY = (baseY + amp * 2.6f).coerceAtMost(size.height),
        ),
    )
}
