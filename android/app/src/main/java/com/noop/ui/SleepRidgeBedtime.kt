package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.data.SleepSession
import com.noop.ui.ridge.GlassCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Ridge Bedtime & Wake (B2-4b-3, `Sleep.dc.html` "Bedtime & Wake")
//
// The board's card: the last 14 nights as floating bars from bedtime down to wake time, green when the bedtime sat
// within 45 minutes of the typical one, orange when it did not, over a dashed band for the typical window. The
// headline percent is the app's one consistency score (owner D16), not a new formula.

/** A bedtime this far from the typical one (in minutes) puts the night off target, as the board has it. */
internal const val BED_OFF_TARGET_MIN = 45

/** Fewest nights the card needs, as the classic card did. */
internal const val BED_WAKE_MIN_NIGHTS = 3

/** One night: its wake date, bedtime and wake time as minutes since midnight (an evening bedtime is negative). */
internal data class BedWakeNight(val wakeDate: LocalDate, val bedMin: Int, val wakeMin: Int, val offTarget: Boolean)

/** The nights plus the typical bedtime and wake time (the means, to the minute). */
internal data class BedWake(val nights: List<BedWakeNight>, val typicalBedMin: Int, val typicalWakeMin: Int) {
    val offCount: Int get() = nights.count { it.offTarget }
}

/**
 * The bridged bed-to-wake [spans] (oldest first, as [consistencyNightSpans] returns them) as the card's nights. Typical
 * is the mean over the shown nights; a night is off target when its bedtime is more than [BED_OFF_TARGET_MIN] from the
 * typical one. Null under [BED_WAKE_MIN_NIGHTS] nights.
 */
internal fun buildBedWake(spans: List<Pair<Long, Long>>, zone: ZoneId): BedWake? {
    if (spans.size < BED_WAKE_MIN_NIGHTS) return null
    fun minutes(ts: Long): Int = Instant.ofEpochSecond(ts).atZone(zone).let { it.hour * 60 + it.minute }
    val raw = spans.map { (onset, wake) ->
        val bed = minutes(onset).let { if (it > 12 * 60) it - 24 * 60 else it } // an evening bedtime sorts above the wake
        Triple(Instant.ofEpochSecond(wake).atZone(zone).toLocalDate(), bed, minutes(wake))
    }
    val typicalBed = raw.map { it.second }.average().roundToInt()
    val typicalWake = raw.map { it.third }.average().roundToInt()
    return BedWake(raw.map { (d, b, w) -> BedWakeNight(d, b, w, abs(b - typicalBed) > BED_OFF_TARGET_MIN) }, typicalBed, typicalWake)
}

private fun dayMinutes(minutes: Int): Int = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)

private val BAR_HEIGHT = 150.dp

/** Room for a 12-hour time ("11:06 PM") left of the bars, and the same inset under them so the day labels line up. */
private val AXIS_GUTTER = 62.dp

/**
 * The board's Bedtime & Wake card for the [spans] of the last nights. [consistencyScore] is the app's one score (0 to
 * 100); it reads "consistent", the opposite sense of the board's sample "irregular".
 */
@Composable
internal fun RidgeBedtimeWakeCard(sleeps: List<SleepSession>, habitualMidsleepSec: Long?, consistencyScore: Double?, uses24Hour: Boolean) {
    val data = remember(sleeps, habitualMidsleepSec) { buildBedWake(consistencyNightSpans(sleeps, habitualMidsleepSec), ZoneId.systemDefault()) } ?: return
    val n = data.nights.size
    var selected by remember(data) { mutableStateOf(n - 1) }
    val night = data.nights[selected.coerceIn(0, n - 1)]
    val clock = { m: Int -> clockOfMinutes(dayMinutes(m), uses24Hour) }
    val top = minOf(data.nights.minOf { it.bedMin }, data.typicalBedMin) - 30
    val bottom = maxOf(data.nights.maxOf { it.wakeMin }, data.typicalWakeMin) + 30
    val span = (bottom - top).coerceAtLeast(1).toFloat()
    val a11y = stringResource(R.string.sleep_ridge_bed_a11y, n, clock(data.typicalBedMin), clock(data.typicalWakeMin))
    GlassCard(Modifier.fillMaxWidth().semantics { contentDescription = a11y }, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                    Text(stringResource(R.string.sleep_ridge_bed_title), style = TRN_H2, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.sleep_ridge_bed_typical, clock(data.typicalBedMin), clock(data.typicalWakeMin)), style = TRN_SUB, color = Ridge.inkMuted)
                }
                consistencyScore?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.sleep_ridge_stage_percent, it.roundToInt().coerceIn(0, 100)), style = RidgeType.numeralMd.copy(fontSize = 26.sp, lineHeight = 30.sp), color = Ridge.ink)
                        Text(stringResource(R.string.sleep_ridge_bed_consistent), style = TRN_LEGEND, color = Ridge.inkMuted)
                    }
                }
            }
            // The selected night, as the board's pill.
            Row(
                Modifier.fillMaxWidth().background(Ridge.glassFill, RoundedCornerShape(Ridge.radiusSm)).border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusSm)).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(dayLabelWithWeekday(night.wakeDate), style = TRN_SUB.copy(fontWeight = FontWeight.SemiBold), color = Ridge.ink)
                Text(stringResource(R.string.sleep_ridge_bed_range, clock(night.bedMin), clock(night.wakeMin)), style = TRN_SUB.copy(fontWeight = FontWeight.SemiBold), color = Ridge.inkMuted)
            }
            Row(Modifier.fillMaxWidth()) {
                // The two typical times, at the band's edges.
                Box(Modifier.width(AXIS_GUTTER).height(BAR_HEIGHT)) {
                    listOf(data.typicalBedMin, data.typicalWakeMin).forEach { m ->
                        Text(
                            clock(m), style = TRN_LEGEND.copy(fontSize = 10.sp), color = Ridge.inkSubtle, maxLines = 1, softWrap = false,
                            modifier = Modifier.offset(y = BAR_HEIGHT * ((m - top) / span) - 8.dp),
                        )
                    }
                }
                Canvas(
                    Modifier.weight(1f).height(BAR_HEIGHT)
                        .pointerInput(n) { detectTapGestures { selected = (it.x / size.width * n).toInt().coerceIn(0, n - 1) } }
                        .pointerInput(n) { detectHorizontalDragGestures { change, _ -> selected = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1); change.consume() } },
                ) {
                    fun y(m: Int) = (m - top) / span * size.height
                    val slot = size.width / n
                    val bandTop = y(data.typicalBedMin); val bandBottom = y(data.typicalWakeMin)
                    drawRoundRect(Ridge.brand.copy(alpha = 0.07f), Offset(0f, bandTop), Size(size.width, bandBottom - bandTop), CornerRadius(8.dp.toPx()))
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    listOf(bandTop, bandBottom).forEach { drawLine(Ridge.brand.copy(alpha = 0.45f), Offset(0f, it), Offset(size.width, it), 1.dp.toPx(), pathEffect = dash) }
                    val bw = 12.dp.toPx()
                    data.nights.forEachIndexed { i, nt ->
                        val x = slot * (i + 0.5f) - bw / 2
                        val y0 = y(nt.bedMin); val y1 = y(nt.wakeMin).coerceAtLeast(y0 + bw)
                        val (hi, lo) = if (nt.offTarget) Ridge.stateLow to lerp(Ridge.stateLow, Ridge.bg000, 0.5f) else Ridge.brand to lerp(Ridge.brand, Ridge.bg000, 0.62f)
                        val on = i == selected
                        drawRoundRect(
                            Brush.verticalGradient(listOf(hi, lo), startY = y0, endY = y1), Offset(x, y0), Size(bw, y1 - y0), CornerRadius(bw / 2),
                            alpha = if (on) 1f else 0.7f,
                        )
                        if (on) drawRoundRect(Ridge.ink, Offset(x - 1.dp.toPx(), y0 - 1.dp.toPx()), Size(bw + 2.dp.toPx(), y1 - y0 + 2.dp.toPx()), CornerRadius(bw / 2 + 1.dp.toPx()), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = AXIS_GUTTER)) {
                data.nights.forEachIndexed { i, nt ->
                    Text(
                        nt.wakeDate.dayOfMonth.toString(), modifier = Modifier.weight(1f), maxLines = 1,
                        style = TRN_LEGEND.copy(fontSize = 11.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        color = if (i == selected) Ridge.ink else Ridge.inkSubtle,
                    )
                }
            }
            HorizontalDivider(color = Ridge.glassStroke)
            Text(
                when (data.offCount) {
                    0 -> stringResource(R.string.sleep_ridge_bed_footer_ok, BED_OFF_TARGET_MIN)
                    1 -> stringResource(R.string.sleep_ridge_bed_footer_one, BED_OFF_TARGET_MIN)
                    else -> stringResource(R.string.sleep_ridge_bed_footer, data.offCount, BED_OFF_TARGET_MIN)
                },
                style = TRN_SUB, color = Ridge.inkMuted,
            )
        }
    }
}
