package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * RS-1-2 item 6 (`Sleep.dc.html`, Hours asleep): the last 14 calendar nights as bars against the need line, one night
 * selected (tap or drag), and the average, shortest and longest under it. A night with no sleep has no bar (never a zero
 * bar) and is left out of the three figures.
 */

/** One calendar night: its wake day and the minutes asleep, null when nothing was recorded. */
internal data class AsleepNight(val day: LocalDate, val min: Double?)

internal data class HoursAsleep(val nights: List<AsleepNight>, val avgMin: Double, val minMin: Double, val maxMin: Double) {
    /** The newest night with sleep: the card's default selection. */
    val defaultIndex: Int get() = nights.indexOfLast { it.min != null }
}

internal object HoursAsleepChart {
    const val NIGHTS = 14
    const val MIN_NIGHTS = 3
    /** Under this many minutes a bar turns orange (the board's 6 h rule). */
    const val SHORT_MIN = 360.0
    /** The chart's top is at least this many minutes (the board's 9 h), raised to fit a longer night or the need. */
    const val FLOOR_TOP_MIN = 540.0

    /** The [NIGHTS] calendar nights ending on [lastDay]; null under [MIN_NIGHTS] nights with sleep. */
    fun build(asleepMinByDay: Map<String, Double?>, lastDay: LocalDate): HoursAsleep? {
        val nights = (NIGHTS - 1 downTo 0).map { back ->
            val d = lastDay.minusDays(back.toLong())
            AsleepNight(d, asleepMinByDay[d.toString()]?.takeIf { it > 0.0 })
        }
        val have = nights.mapNotNull { it.min }
        if (have.size < MIN_NIGHTS) return null
        return HoursAsleep(nights, have.average(), have.min(), have.max())
    }

    fun topMin(h: HoursAsleep, needMin: Double?): Double = maxOf(FLOOR_TOP_MIN, h.maxMin, needMin ?: 0.0)

    fun isShort(min: Double): Boolean = min < SHORT_MIN
}

@Composable
internal fun RidgeHoursAsleepCard(h: HoursAsleep?, needMin: Double?) {
    GlassCard(Modifier.fillMaxWidth(), tone = com.noop.ui.ridge.GlassTone.Strong, contentPadding = PaddingValues(20.dp)) {
        if (h == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                RidgeEyebrow(stringResource(R.string.sleep_ridge_ha_title), Modifier.semantics { heading() })
                Text(stringResource(R.string.sleep_ridge_ha_empty, HoursAsleepChart.MIN_NIGHTS), style = TRN_SUB, color = Ridge.inkMuted)
            }
            return@GlassCard
        }
        var sel by remember(h) { mutableStateOf(h.defaultIndex) }
        val night = h.nights[sel]
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RidgeEyebrow(stringResource(R.string.sleep_ridge_ha_title), Modifier.semantics { heading() })
                    Text(night.min?.let { hoursMinutesPadded(it) } ?: "—", style = RidgeType.numeralLg.copy(lineHeight = 48.sp), color = Ridge.ink)
                    Text(
                        if (sel == h.nights.lastIndex) stringResource(R.string.sleep_ridge_ha_last)
                        else night.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = RidgeType.bodySm.copy(fontSize = 14.sp), color = Ridge.inkMuted,
                    )
                }
                Row(
                    Modifier.border(1.dp, Ridge.brand.copy(alpha = 0.4f), RoundedCornerShape(Ridge.radiusPill)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(6.dp).background(Ridge.brand, CircleShape))
                    Text(
                        stringResource(R.string.sleep_ridge_ha_nights, HoursAsleepChart.NIGHTS),
                        style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = Ridge.brand, maxLines = 1,
                    )
                }
            }
            HoursAsleepBars(h, needMin, sel) { sel = it }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HaStat(Modifier.weight(1f), stringResource(R.string.sleep_ridge_ha_avg), hoursMinutesPadded(h.avgMin), Ridge.ink)
                HaStat(Modifier.weight(1f), stringResource(R.string.sleep_ridge_ha_short), hoursMinutesPadded(h.minMin), Ridge.stateLow)
                HaStat(Modifier.weight(1f), stringResource(R.string.sleep_ridge_ha_long), hoursMinutesPadded(h.maxMin), Ridge.brand)
            }
        }
    }
}

@Composable
private fun HaStat(modifier: Modifier, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RidgeEyebrow(label)
        Text(value, style = RidgeType.numeralMd.copy(fontSize = 20.sp, lineHeight = 24.sp), color = color, maxLines = 1)
    }
}

/** 150 dp: bars rise from a baseline 32 dp above the foot, the day of month under each, the dashed need line across. */
@Composable
private fun HoursAsleepBars(h: HoursAsleep, needMin: Double?, sel: Int, onSelect: (Int) -> Unit) {
    val top = HoursAsleepChart.topMin(h, needMin)
    val plotH = 104.dp
    val baseFromBottom = 32.dp
    val needColor = Ridge.stateModerate.copy(alpha = 0.55f)
    val hoursFmt = stringResource(R.string.sleep_ridge_hvn_hours)
    val count = h.nights.size
    Box(
        Modifier.fillMaxWidth().height(150.dp)
            .pointerInput(h) {
                detectTapGestures { o -> onSelect((o.x / size.width * count).toInt().coerceIn(0, count - 1)) }
            }
            .pointerInput(h) {
                detectHorizontalDragGestures { change, _ ->
                    onSelect((change.position.x / size.width * count).toInt().coerceIn(0, count - 1))
                }
            }
            .drawBehind {
                val baseY = size.height - baseFromBottom.toPx()
                drawLine(Ridge.glassStroke.copy(alpha = 0.22f), Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
                if (needMin != null && needMin > 0.0) {
                    val y = baseY - (needMin / top * plotH.toPx()).toFloat()
                    drawLine(needColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
                }
            },
    ) {
        if (needMin != null && needMin > 0.0) {
            val needY = 150.dp - baseFromBottom - (plotH * (needMin / top).toFloat())
            Text(
                stringResource(R.string.sleep_ridge_ha_need, hoursFmt.format(String.format(Locale.getDefault(), "%.1f", needMin / 60.0))),
                style = TRN_LEGEND.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Ridge.stateModerate,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = needY - 18.dp),
            )
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            h.nights.forEachIndexed { i, n ->
                val on = i == sel
                val a11y = n.min?.let { stringResource(R.string.sleep_ridge_ha_bar_a11y, n.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), hoursMinutesPadded(it)) }
                    ?: stringResource(R.string.sleep_ridge_ha_bar_none, n.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = a11y }) {
                    n.min?.let { m ->
                        val barH = maxOf(6.dp, plotH * (m / top).toFloat())
                        val short = HoursAsleepChart.isShort(m)
                        val brush = if (short) {
                            Brush.verticalGradient(listOf(lerp(Ridge.stateLow, Ridge.ink, 0.25f), lerp(Ridge.stateLow, Ridge.bg000, 0.25f)))
                        } else {
                            Brush.verticalGradient(listOf(lerp(Ridge.brand, Ridge.ink, 0.35f), Ridge.brandDeep))
                        }
                        Box(
                            Modifier.align(Alignment.BottomCenter).offset(y = -baseFromBottom - 1.dp)
                                .width(12.dp).height(barH)
                                .then(if (on) Modifier.ridgeSoftGlow(Ridge.brand, 6.dp) else Modifier)
                                .alpha(if (on) 1f else 0.75f)
                                .background(brush, RoundedCornerShape(6.dp)),
                        )
                    }
                    Text(
                        n.day.dayOfMonth.toString(),
                        style = TRN_LEGEND.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                        color = if (on) Ridge.ink else Ridge.inkSubtle, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * A soft glow drawn behind a rounded bar ([radius] corners): three widening rings of [color] at falling alpha. Drawn, not
 * an elevation shadow, which lit a band across the translucent glass card behind it.
 */
internal fun Modifier.ridgeSoftGlow(color: androidx.compose.ui.graphics.Color, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    listOf(6.dp to 0.06f, 4.dp to 0.10f, 2.dp to 0.16f).forEach { (spread, a) ->
        val p = spread.toPx()
        drawRoundRect(
            color.copy(alpha = a),
            topLeft = Offset(-p, -p),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * p, size.height + 2 * p),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx() + p),
        )
    }
}

