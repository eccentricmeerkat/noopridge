package com.noop.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.noop.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

// MARK: - Today's hero panel (SQ3-3, side quest #3 doc 4)
//
// The container behind Charge / Effort / Rest as lightly frosted glass: 90% transparent (a 10% navy tint) over a
// gently Gaussian-blurred copy of the very photograph behind it (the owner's review: only a slight frost), a 30 dp
// corner and a 1 dp white-blue border. Compose can only blur a node's own content, not
// what is behind it, and the photo is a known, cached image, so the panel draws a blurred copy of the same photo,
// lined up with the real one frame by frame. The blur is computed once (on a 1/8-scale copy, off the main thread), so
// scrolling costs nothing extra and the result is the same on every Android version. The panel itself (tint and
// border) never waits on it: until the copy is ready, or when the backdrop is not the photo, a deeper navy tint
// stands in, so the panel is always there.

/** The gap from the bottom of the header (now the stacked greeting, B2-1a-1) to the top of the panel: the board's 28 px
 *  (Main.dc.html .np-orb-card margin-top) plus 25 dp (owner, 19 Nov 2026) so the orbs sit lower over the mountains. */
internal val HERO_GAP_BELOW_HEADER: Dp = 53.dp

/** The panel's corner radius, tint opacity, border and blur: doc 4's, with the tint and blur eased to a slight frost
 *  (owner's review: the box is 90% transparent). */
internal val HERO_PANEL_RADIUS: Dp = 28.dp
internal const val HERO_PANEL_FILL_TOP_ALPHA = 0.16f
internal const val HERO_PANEL_FILL_BOTTOM_ALPHA = 0.08f
internal const val HERO_PANEL_BORDER_ALPHA = 0.28f
internal val HERO_PANEL_BLUR: Dp = 2.dp

/** The photo and the factor its blurred copy is scaled down by (1080 x 2430 → 135 x 304). */
private const val PHOTO_W = 1080f
private const val PHOTO_H = 2430f
private const val BLUR_SAMPLE = 8

/**
 * The frosted hero panel. [blurBackdrop] is true when the page backdrop is the Today photo (not a custom image, and
 * the day-cycle background is on); [scrollPx] and [exitPx] are the backdrop's own, so the blurred copy moves with it.
 */
@Composable
internal fun RidgeHeroPanel(
    blurBackdrop: Boolean,
    scrollPx: () -> Float,
    exitPx: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(HERO_PANEL_RADIUS)
    val blurred = RidgeHeroBlur.bitmap
    if (blurBackdrop) {
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val sigmaPx = with(density) { HERO_PANEL_BLUR.toPx() }
        val slot by rememberHeroSlot()
        LaunchedEffect(slot, screenWidthPx, sigmaPx) { RidgeHeroBlur.prewarm(context, slot, screenWidthPx, sigmaPx) }
    }
    // 0 → 1 as the blurred copy lands, so the panel eases from the stand-in tint to the frosted look.
    val ready by animateFloatAsState(
        targetValue = if (blurBackdrop && blurred != null) 1f else 0f,
        animationSpec = tween(180),
    )
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier = modifier
            .clip(shape)
            .onPlaced { placed[0] = it }
            .drawBehind {
                val coords = placed[0]
                if (blurred != null && ready > 0f && coords != null && coords.isAttached) {
                    // Reading the backdrop's scroll here redraws the copy on every scroll frame, lined up with the photo.
                    val progress = backdropExitProgress(scrollPx(), exitPx())
                    val rootWidth = coords.findRootCoordinates().size.width.toFloat()
                    val photoHeight = rootWidth * (PHOTO_H / PHOTO_W)
                    val at = coords.positionInRoot()
                    translate(left = -at.x, top = -photoHeight * progress - at.y) {
                        drawImage(
                            blurred, dstSize = IntSize(rootWidth.roundToInt(), photoHeight.roundToInt()),
                            alpha = ready, filterQuality = FilterQuality.High,
                        )
                    }
                }
                // The stand-in: a touch deeper navy that gives way to the 10% tint as the frosted copy fades in.
                val standIn = 1f - ready
                if (standIn > 0f) {
                    drawRect(
                        Brush.verticalGradient(listOf(Ridge.bg100.copy(alpha = 0.16f * standIn), Ridge.bg000.copy(alpha = 0.22f * standIn))),
                        size = Size(size.width, size.height),
                    )
                }
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            HERO_PANEL_FILL_TOP.copy(alpha = HERO_PANEL_FILL_TOP_ALPHA * ready),
                            HERO_PANEL_FILL_BOTTOM.copy(alpha = HERO_PANEL_FILL_BOTTOM_ALPHA * ready),
                        ),
                    ),
                )
            }
            .border(1.dp, HERO_PANEL_BORDER.copy(alpha = HERO_PANEL_BORDER_ALPHA), shape),
        content = content,
    )
}

/** The border's hue: the glass stroke's white-blue. */
private val HERO_PANEL_BORDER = Color(235, 245, 252)

/** B2-1a-3 (Main.dc.html .np-orb-card): the glass fill, a white-blue wash from 16% at the top to 8% at the bottom. */
private val HERO_PANEL_FILL_TOP = Color(225, 238, 248)
private val HERO_PANEL_FILL_BOTTOM = Color(200, 220, 235)

// MARK: - The blurred copy

/** One blurred copy of the Today photo per process and screen size; every panel shares it. */
internal object RidgeHeroBlur {
    private val lock = Mutex()
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    private var key: Triple<HeroSlot, Int, Int>? = null

    suspend fun prewarm(context: Context, slot: HeroSlot, screenWidthPx: Int, blurSigmaPx: Float) {
        if (screenWidthPx <= 0) return
        // The photo is drawn at screenWidthPx / 1080 of its size; the copy is 1/8 of that, so the sigma shrinks to match.
        val sigma = blurSigmaPx / (BLUR_SAMPLE * (screenWidthPx / PHOTO_W))
        val k = Triple(slot, screenWidthPx, (sigma * 10f).roundToInt())
        lock.withLock {
            if (key == k && bitmap != null) return
            val out = withContext(Dispatchers.Default) {
                val opts = BitmapFactory.Options().apply { inSampleSize = BLUR_SAMPLE }
                val small = BitmapFactory.decodeResource(context.resources, slot.drawable, opts) ?: return@withContext null
                val px = IntArray(small.width * small.height)
                small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                val blurred = gaussianBlurRgb(px, small.width, small.height, sigma)
                Bitmap.createBitmap(blurred, small.width, small.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            } ?: return
            key = k
            bitmap = out
        }
    }
}

/**
 * A Gaussian blur of an opaque ARGB image, as three box blurs (the standard approximation, within about 3% of a true
 * Gaussian), edges clamped. [sigma] is in pixels. Pure, so it is tested on the JVM.
 */
internal fun gaussianBlurRgb(pixels: IntArray, w: Int, h: Int, sigma: Float): IntArray {
    if (sigma < 0.5f || w == 0 || h == 0) return pixels.copyOf()
    val sizes = boxSizesForGaussian(sigma, 3)
    val channels = Array(3) { c ->
        FloatArray(w * h) { i -> ((pixels[i] shr (16 - 8 * c)) and 0xFF).toFloat() }
    }
    val tmp = FloatArray(w * h)
    for (ch in channels) for (size in sizes) {
        val r = (size - 1) / 2
        boxBlurLine(ch, tmp, w, h, r, horizontal = true)
        boxBlurLine(tmp, ch, w, h, r, horizontal = false)
    }
    return IntArray(w * h) { i ->
        (0xFF shl 24) or
            (channels[0][i].roundToInt().coerceIn(0, 255) shl 16) or
            (channels[1][i].roundToInt().coerceIn(0, 255) shl 8) or
            channels[2][i].roundToInt().coerceIn(0, 255)
    }
}

/** The widths of [n] successive box blurs whose combined variance matches a Gaussian of [sigma] (all odd). */
internal fun boxSizesForGaussian(sigma: Float, n: Int): IntArray {
    val ideal = sqrt(12f * sigma * sigma / n + 1f)
    var lower = floor(ideal).toInt()
    if (lower % 2 == 0) lower--
    val upper = lower + 2
    val m = ((12f * sigma * sigma - n * lower * lower - 4f * n * lower - 3f * n) / (-4f * lower - 4f)).roundToInt()
    return IntArray(n) { if (it < m) lower.coerceAtLeast(1) else upper }
}

/** One box-blur pass along rows ([horizontal]) or columns, radius [r], edge pixels repeated. */
private fun boxBlurLine(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val stride = if (horizontal) 1 else w
    val window = (2 * r + 1).toFloat()
    for (line in 0 until lines) {
        val base = if (horizontal) line * w else line
        val first = src[base]
        val last = src[base + (len - 1) * stride]
        var sum = first * r
        for (i in 0..r) sum += src[base + minOf(i, len - 1) * stride]
        for (i in 0 until len) {
            dst[base + i * stride] = sum / window
            val add = src[base + minOf(i + r + 1, len - 1) * stride].let { if (i + r + 1 >= len) last else it }
            val drop = src[base + maxOf(i - r, 0) * stride].let { if (i - r < 0) first else it }
            sum += add - drop
        }
    }
}
