package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.analytics.HrZoneSet
import kotlin.math.roundToInt

/** "m:ss", or "h:mm:ss" from an hour, as the board's axis and read-out write elapsed time. */
internal fun hrAxisClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * The board's workout heart-rate chart (`WorkoutDetail.dc.html`, "Heart rate"): zone boundary hairlines (no coloured bands behind the line) with their Z1–Z5
 * labels down the left, a line that takes its colour from the zone it is in, a ringed peak with its bpm above it, short
 * bars along the axis wherever the heart rate sat in Zone 4 or 5, elapsed-time labels, and a press-and-drag read-out.
 */
@Composable
internal fun RidgeWorkoutHrChart(
    values: List<Double>,
    timestamps: List<Long>,
    zones: HrZoneSet,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    val zs = zones.zones
    val zoneColors = zs.map { Ridge.hrZone(it.number) }
    val peak = values.max()
    val yMin = minOf(zs.first().lower, values.min()) - 9.0
    val yMax = maxOf(zs.last().upper, peak) + 7.0
    val t0 = timestamps.firstOrNull() ?: 0L
    val tEnd = timestamps.lastOrNull() ?: (t0 + values.size * 60L)
    val span = (tEnd - t0).coerceAtLeast(1L).toDouble()
    fun tAt(i: Int): Double = if (timestamps.size == values.size) (timestamps[i] - t0).toDouble() else i * span / (values.size - 1)
    var pressed by remember(values) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val peakIndex = values.indices.maxBy { values[it] }
    val a11y = stringResource(R.string.workouts_ridge_hr_a11y)
    val ink = Ridge.ink; val subtle = Ridge.inkSubtle; val bg = Ridge.bg000
    val peakRing = zoneColors.last()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
        Box(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = a11y }) {
            var widthPx by remember { mutableStateOf(1f) }
            Canvas(
                Modifier
                    .matchParentSizeCompat()
                    .pointerInput(values) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun pick(x: Float) {
                                val left = 26.dp.toPx()
                                val f = ((x - left) / (size.width - left)).coerceIn(0f, 1f)
                                pressed = nearestIndex(values.indices.map { tAt(it) }, f * span)
                            }
                            pick(down.position.x)
                            drag(down.id) { change -> pick(change.position.x); change.consume() }
                            pressed = null
                        }
                    },
            ) {
                widthPx = size.width
                val left = 26.dp.toPx()
                val right = size.width
                val top = 8.dp.toPx()
                val bottom = 168.dp.toPx()
                fun x(t: Double) = left + (t / span).toFloat() * (right - left)
                fun y(b: Double) = bottom - ((b - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f) * (bottom - top)
                // Zone bands, Z5 at the top, with the zone name in its own colour at the left.
                zs.forEachIndexed { i, z ->
                    val y0 = y(if (i == zs.lastIndex) yMax else z.upper); val y1 = y(z.lower)
                    // BS-x2-3 item 15: no coloured band behind the line - a plain background with a hairline at each zone
                    // boundary; colour lives on the line, the Z labels and the Zone 4-5 bars only.
                    drawLine(ink.copy(alpha = 0.10f), Offset(left, y0), Offset(right, y0), 1.dp.toPx())
                    val label = measurer.measure("Z${z.number}", androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = zoneColors[i]))
                    drawText(label, topLeft = Offset(0f, (y0 + y1) / 2 - label.size.height / 2f))
                }
                // Short bars on the axis where the heart rate sat in Zone 4 or 5.
                val z4 = zs.getOrNull(3)?.lower ?: Double.MAX_VALUE
                val z5 = zs.getOrNull(4)?.lower ?: Double.MAX_VALUE
                for (i in 0 until values.size - 1) {
                    val v = values[i]
                    if (v < z4) continue
                    val x0 = x(tAt(i)); val x1 = x(tAt(i + 1))
                    drawRoundRect(
                        if (v >= z5) zoneColors[4] else zoneColors[3],
                        Offset(x0, 172.dp.toPx()), Size((x1 - x0).coerceAtLeast(2.dp.toPx()), 5.dp.toPx()), CornerRadius(2.dp.toPx()),
                    )
                }
                // The line, coloured by the zone each bpm falls in.
                val path = Path()
                values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(tAt(i)), y(v)) else path.lineTo(x(tAt(i)), y(v)) }
                val stops = zs.indices.reversed().map { i ->
                    val mid = (zs[i].lower + (if (i == zs.lastIndex) yMax else zs[i].upper)) / 2
                    ((y(mid) - top) / (bottom - top)).coerceIn(0f, 1f) to zoneColors[i]
                }
                drawPath(
                    path, Brush.verticalGradient(*stops.toTypedArray(), startY = top, endY = bottom),
                    style = Stroke(2.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // The peak: a ring and its bpm.
                val px = x(tAt(peakIndex)); val py = y(peak)
                drawCircle(bg, 4.dp.toPx(), Offset(px, py))
                drawCircle(peakRing, 4.dp.toPx(), Offset(px, py), style = Stroke(2.dp.toPx()))
                val peakLabel = measurer.measure(peak.roundToInt().toString(), androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink))
                drawText(peakLabel, topLeft = Offset((px - peakLabel.size.width / 2f).coerceIn(left, right - peakLabel.size.width), py - 9.dp.toPx() - peakLabel.size.height))
                // Elapsed-time labels.
                fun axis(t: Long, anchorX: Float, align: Int) {
                    val l = measurer.measure(hrAxisClock(t), androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = subtle))
                    val tx = when (align) { 0 -> anchorX; 1 -> anchorX - l.size.width / 2f; else -> anchorX - l.size.width }
                    drawText(l, topLeft = Offset(tx, 182.dp.toPx()))
                }
                axis(0, left, 0); axis((span / 2).toLong(), (left + right) / 2, 1); axis(span.toLong(), right, 2)
                // The read-out: a vertical line and a dot on the pressed reading.
                pressed?.let { i ->
                    val sx = x(tAt(i)); val sy = y(values[i])
                    drawLine(ink.copy(alpha = 0.45f), Offset(sx, top), Offset(sx, bottom), 1.dp.toPx())
                    drawCircle(bg, 5.dp.toPx(), Offset(sx, sy))
                    drawCircle(zoneColors[(zones.zoneNumber(values[i]) - 1).coerceIn(0, 4)], 5.dp.toPx(), Offset(sx, sy), style = Stroke(2.dp.toPx()))
                }
            }
            pressed?.let { i ->
                val frac = (tAt(i) / span).toFloat()
                val xDp = 26f + frac * (with(density) { widthPx.toDp().value } - 26f)
                val zn = zones.zoneNumber(values[i]).coerceAtLeast(1)
                Column(
                    Modifier
                        .offset { IntOffset((xDp.dp.toPx() - 44.dp.toPx()).roundToInt().coerceIn(0, (widthPx - 88.dp.toPx()).roundToInt().coerceAtLeast(0)), 0) }
                        .background(Ridge.bg000, RoundedCornerShape(Ridge.radiusSm))
                        .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusSm))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.today_driver_value_bpm, values[i].roundToInt()), style = TRN_LEGEND.copy(fontWeight = FontWeight.Bold), color = zoneColors[zn - 1])
                    Text(stringResource(R.string.workouts_ridge_hr_readout, hrAxisClock(tAt(i).toLong()), zn), style = TRN_LEGEND.copy(fontSize = 11.sp), color = subtle)
                }
            }
        }
        Text(stringResource(R.string.workouts_ridge_hr_legend), style = TRN_LEGEND, color = Ridge.inkSubtle, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().height(200.dp)

/** The index of the reading whose time is closest to [t]. */
internal fun nearestIndex(times: List<Double>, t: Double): Int {
    var best = 0
    var bestD = Double.MAX_VALUE
    for (i in times.indices) {
        val d = kotlin.math.abs(times[i] - t)
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}
