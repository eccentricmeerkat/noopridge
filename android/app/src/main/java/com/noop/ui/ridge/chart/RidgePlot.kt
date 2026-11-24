package com.noop.ui.ridge.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.ui.Ridge
import com.noop.ui.RidgeChart
import com.noop.ui.RidgeType
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the scrub tooltip reads for one slot (R2): the value with its unit, the date, and an optional extra line. */
data class ScrubTip(val value: String, val detail: String, val extra: String? = null)

/**
 * The plot's geometry, handed to the draw lambda: [x] for a slot, [y] for a value, and the bar width. Both map
 * inside [top]..[bottom] so marks never clip.
 */
class PlotGeometry(
    val width: Float,
    val top: Float,
    val bottom: Float,
    val n: Int,
    val bars: Boolean,
    val axis: Axis,
) {
    fun x(i: Int): Float = slotFrac(i, n, bars) * width
    fun y(v: Double): Float = bottom - axis.frac(v) * (bottom - top)

    /** A bar fills 62% of its slot (`Trends.dc.html`), at least 2 px. */
    val barWidth: Float get() = (width / n.coerceAtLeast(1) * 0.62f).coerceAtLeast(2f)
}

private val Y_LABEL_WIDTH = 30.dp
private val PLOT_INSET = 6.dp

/**
 * A Ridge chart frame (R-01): y-axis tick labels on the left (R1), dashed grid lines, the plot drawn by [draw], up
 * to five date labels underneath (R8), and press-and-drag scrubbing (R2). The scrub reads only its own state, so
 * dragging recomposes the overlay and never the data marks (R7). [scrubY] places the scrub dot; null draws the line
 * only. [contentDescription] is the chart's takeaway for TalkBack (R6); the tooltip is a polite live region.
 *
 * B2-2a-2 adds the board's axis-less frame: [yLabels] off drops the label column (the plot takes the full width),
 * [gridlines] off drops the dashed rules, [baseline] draws the one hairline under the plot, and [caption] is a single
 * right-aligned word under it ("Today") for the cards that carry no date labels (checkpoint Q6).
 */
@Composable
fun RidgePlot(
    n: Int,
    axis: Axis,
    height: Dp,
    yLabel: (Double) -> String,
    xLabels: List<Pair<Int, String>>,
    contentDescription: String,
    accent: Color,
    tip: (Int) -> ScrubTip?,
    modifier: Modifier = Modifier,
    bars: Boolean = false,
    scrubY: ((Int) -> Double?)? = null,
    yLabels: Boolean = true,
    gridlines: Boolean = true,
    baseline: Boolean = false,
    caption: String? = null,
    /** Draws the last x label bold and in full ink, as the Sleep debt board marks its latest night. */
    xLabelBoldLast: Boolean = false,
    draw: DrawScope.(PlotGeometry) -> Unit,
) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        Row(Modifier.fillMaxWidth()) {
            // R1: the labelled ticks, each centred on its grid line.
            if (yLabels) {
                Box(Modifier.width(Y_LABEL_WIDTH).height(height)) {
                    FracColumn(height, axis.ticks.map { 1f - axis.frac(it) }) {
                        axis.ticks.forEach { Text(yLabel(it), style = AXIS_TEXT, color = Ridge.inkSubtle, maxLines = 1) }
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .semantics { this.contentDescription = contentDescription }
                    .pointerInput(n, bars) { scrubGestures(n, bars) { scrub = it } },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val inset = PLOT_INSET.toPx()
                    val g = PlotGeometry(size.width, inset, size.height - inset, n, bars, axis)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                    if (gridlines) {
                        axis.ticks.forEach { t ->
                            val y = g.y(t)
                            drawLine(RidgeChart.grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
                        }
                    }
                    if (baseline) {
                        drawLine(RidgeChart.axis, Offset(0f, g.bottom), Offset(size.width, g.bottom), 1.dp.toPx())
                    }
                    draw(g)
                }
                ScrubOverlay(scrub, n, bars, axis, accent, scrubY, tip)
            }
        }
        if (xLabels.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(start = if (yLabels) Y_LABEL_WIDTH else 0.dp)) {
                FracRow(xLabels.map { slotFrac(it.first, n, bars) }) {
                    xLabels.forEachIndexed { k, label ->
                        val bold = xLabelBoldLast && k == xLabels.lastIndex
                        Text(
                            label.second,
                            style = if (bold) AXIS_TEXT.copy(fontWeight = FontWeight.Bold) else AXIS_TEXT,
                            color = if (bold) Ridge.ink else Ridge.inkSubtle,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (caption != null) {
            Text(
                caption, style = AXIS_TEXT, color = Ridge.inkSubtle, maxLines = 1, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(start = if (yLabels) Y_LABEL_WIDTH else 0.dp),
            )
        }
    }
}

private val AXIS_TEXT = RidgeType.bodySm.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal)

/** The scrub line, dot and tooltip for slot [index]; nothing when not scrubbing. */
@Composable
private fun ScrubOverlay(
    index: Int?,
    n: Int,
    bars: Boolean,
    axis: Axis,
    accent: Color,
    scrubY: ((Int) -> Double?)?,
    tip: (Int) -> ScrubTip?,
) {
    if (index == null || n == 0) return
    val i = index.coerceIn(0, n - 1)
    val t = tip(i) ?: return
    val yValue = scrubY?.invoke(i)
    Canvas(Modifier.fillMaxSize()) {
        val inset = PLOT_INSET.toPx()
        val g = PlotGeometry(size.width, inset, size.height - inset, n, bars, axis)
        val x = g.x(i)
        drawLine(RidgeChart.scrubLine, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
        if (yValue != null) {
            val c = Offset(x, g.y(yValue))
            drawCircle(accent.copy(alpha = 0.35f), 9.dp.toPx(), c)
            drawCircle(Ridge.bg100, 5.dp.toPx(), c)
            drawCircle(accent, 5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
    }
    // The tooltip sits above the plot, centred on the line and held inside the card at the edges.
    FracRow(listOf(slotFrac(i, n, bars)), Modifier.layoutAbove()) {
        Column(
            Modifier
                .background(RidgeChart.tooltipFill, RoundedCornerShape(Ridge.radiusSm))
                .border(1.dp, RidgeChart.tooltipStroke, RoundedCornerShape(Ridge.radiusSm))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(t.value, style = RidgeType.numeralMd.copy(fontSize = 14.sp, lineHeight = 18.sp), color = accent, maxLines = 1)
            Text(t.detail, style = AXIS_TEXT, color = Ridge.inkSubtle, maxLines = 1)
            t.extra?.let { Text(it, style = AXIS_TEXT, color = Ridge.ink, maxLines = 1, textAlign = TextAlign.Center) }
        }
    }
}

/** Lifts the tooltip row so its bottom sits just above the plot's top edge, taking no height itself. */
private fun Modifier.layoutAbove(): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0))
    layout(p.width, 0) { p.place(0, -p.height - 4.dp.roundToPx()) }
}

/**
 * Press-and-drag scrubbing (R2) that leaves vertical scrolling to the page: a horizontal drag past touch slop, or a
 * long press, starts the scrub and consumes the gesture; a vertical drag first hands it back to the list. Lifting
 * the finger ends the scrub.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.scrubGestures(
    n: Int,
    bars: Boolean,
    onScrub: (Int?) -> Unit,
) {
    if (n == 0) return
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat().coerceAtLeast(1f)
        fun index(x: Float) = scrubIndex(x / w, n, bars)
        var totalX = 0f
        var totalY = 0f
        // A long press with no movement claims too, so a reader can hold to read one day.
        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                val ch = ev.changes.first()
                if (!ch.pressed) return@withTimeoutOrNull false
                val d = ch.position - ch.previousPosition
                totalX += d.x
                totalY += d.y
                if (abs(totalY) > viewConfiguration.touchSlop && abs(totalY) > abs(totalX)) return@withTimeoutOrNull false
                if (abs(totalX) > viewConfiguration.touchSlop) {
                    ch.consume()
                    onScrub(index(ch.position.x))
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        when (held) {
            false -> return@awaitEachGesture
            null -> onScrub(index(down.position.x))
            true -> Unit
        }
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val ch = ev.changes.first()
            if (!ch.pressed) break
            ch.consume()
            onScrub(index(ch.position.x))
        }
        onScrub(null)
    }
}

/** Places each child centred at its x fraction of the row width, clamped so it never leaves the row. */
@Composable
private fun FracRow(fracs: List<Float>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val w = constraints.maxWidth
        val h = placeables.maxOfOrNull { it.height } ?: 0
        layout(w, h) {
            placeables.forEachIndexed { i, p ->
                val cx = (fracs.getOrElse(i) { 0f } * w).roundToInt()
                p.place((cx - p.width / 2).coerceIn(0, (w - p.width).coerceAtLeast(0)), 0)
            }
        }
    }
}

/** Places each child centred at its y fraction of [height], clamped inside it. */
@Composable
private fun FracColumn(height: Dp, fracs: List<Float>, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val h = height.roundToPx()
        val inset = PLOT_INSET.roundToPx()
        layout(constraints.maxWidth, h) {
            placeables.forEachIndexed { i, p ->
                val cy = inset + (fracs.getOrElse(i) { 0f } * (h - 2 * inset)).roundToInt()
                p.place(0, (cy - p.height / 2).coerceIn(0, (h - p.height).coerceAtLeast(0)))
            }
        }
    }
}

// MARK: - Draw helpers (R3 gaps, R4 colour: no glow on data marks)

/** A line through [values], broken across gaps longer than one day (R3); a lone point draws as a dot. */
fun DrawScope.ridgeLine(g: PlotGeometry, values: List<Double?>, color: Color, width: Dp = 2.dp, dashed: Boolean = false) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
    for (run in lineRuns(values)) {
        if (run.size == 1) {
            drawCircle(color, width.toPx(), Offset(g.x(run[0]), g.y(values[run[0]]!!)))
            continue
        }
        val path = androidx.compose.ui.graphics.Path()
        run.forEachIndexed { k, i ->
            val p = Offset(g.x(i), g.y(values[i]!!))
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path, color,
            style = Stroke(width.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round, pathEffect = effect),
        )
    }
}

/** One dot per tracked day; [alpha] and [hollow] pick each dot's emphasis. */
fun DrawScope.ridgeDots(
    g: PlotGeometry,
    values: List<Double?>,
    color: Color,
    radius: Dp = 3.dp,
    alpha: (Int, Double) -> Float = { _, _ -> 0.45f },
    hollow: (Int, Double) -> Boolean = { _, _ -> false },
) {
    values.forEachIndexed { i, v ->
        if (v == null) return@forEachIndexed
        val c = Offset(g.x(i), g.y(v))
        if (hollow(i, v)) drawCircle(color, radius.toPx(), c, style = Stroke(1.5.dp.toPx()))
        else drawCircle(color.copy(alpha = alpha(i, v)), radius.toPx(), c)
    }
}

/** A shaded normal band from [lo] to [hi] across the plot. */
fun DrawScope.ridgeBand(g: PlotGeometry, lo: Double, hi: Double, color: Color) {
    val top = g.y(hi)
    drawRect(color, Offset(0f, top), Size(g.width, (g.y(lo) - top).coerceAtLeast(1f)))
}

/** The latest point's ring (the one mark allowed emphasis, R4). */
fun DrawScope.ridgeEndRing(g: PlotGeometry, i: Int, v: Double, color: Color) {
    val c = Offset(g.x(i), g.y(v))
    drawCircle(Ridge.ink, 4.5.dp.toPx(), c)
    drawCircle(color, 4.5.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
}

/**
 * A stacked bar at slot [i]: [segments] bottom-up from zero as (value, colour), each separated from the next by a ground gap
 * (D-13) so every segment meets contrast against the card.
 */
fun DrawScope.ridgeStack(g: PlotGeometry, i: Int, segments: List<Pair<Double, Color>>) {
    val bw = g.barWidth
    val left = g.x(i) - bw / 2
    val gap = 1.5.dp.toPx()
    var base = 0.0
    val drawn = segments.filter { it.first > 0.0 }
    drawn.forEachIndexed { k, (v, color) ->
        val y0 = g.y(base)
        val y1 = g.y(base + v)
        val h = (y0 - y1 - if (k < drawn.lastIndex) gap else 0f).coerceAtLeast(0f)
        val isTop = k == drawn.lastIndex
        if (isTop) {
            drawRoundRect(color, Offset(left, y1), Size(bw, h), CornerRadius(2.dp.toPx()))
        } else {
            drawRect(color, Offset(left, y0 - h), Size(bw, h))
        }
        base += v
    }
}

/** An untracked day (R3): a dashed outline bar up to [v]. */
fun DrawScope.ridgeGapBar(g: PlotGeometry, i: Int, v: Double) {
    val bw = g.barWidth
    val top = g.y(v)
    drawRoundRect(
        RidgeChart.neutral, Offset(g.x(i) - bw / 2, top), Size(bw, g.y(g.axis.lo) - top), CornerRadius(2.dp.toPx()),
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
    )
}
