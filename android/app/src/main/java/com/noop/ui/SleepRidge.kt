package com.noop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.sp
import com.noop.ui.ridge.chart.PlotGeometry
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.chart.Axis
import com.noop.ui.ridge.chart.RidgePlot
import com.noop.ui.ridge.chart.ScrubTip
import com.noop.ui.ridge.chart.axisLabelSlots
import com.noop.ui.ridge.chart.ridgeGapBar
import com.noop.ui.ridge.chart.ridgeStack
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

// MARK: - Ridge Sleep (R6, F-01 / F-02, `Sleep.dc.html`)
//
// A reskin that keeps every v10 Sleep action and note (UX-SLP-1/2/3). R6-1: the Ridge header, the Rest hero orb and
// the sleep-debt chart (F-02). Every number is the one the v10 tab already shows: the night's Rest score, its time
// asleep, and the per-day debt [buildSleepModel] computes (SleepDebt.debtSeries, imported debt winning per day).

/** The debt chart's window (05 §11, the board's "14 nights"). */
internal const val DEBT_NIGHTS = 14

/** 05 §11: the debt scale is `max(120 min, max)`, so a small debt never fills the chart. */
internal const val DEBT_SCALE_FLOOR_MIN = 120.0

internal data class DebtChart(
    val days: List<LocalDate>,
    /** Debt in minutes per night; null when the night has no recorded sleep (drawn as a dashed outline, R3). */
    val debtMin: List<Double?>,
) {
    private val tracked: List<Double> get() = debtMin.filterNotNull()
    val trackedNights: Int get() = tracked.size
    val lastNight: Double? get() = debtMin.lastOrNull { it != null }
    val average: Double? get() = tracked.takeIf { it.isNotEmpty() }?.average()
    val worst: Double? get() = tracked.maxOrNull()
    val scaleMax: Double get() = maxOf(DEBT_SCALE_FLOOR_MIN, worst ?: 0.0)

    /** The night drawn burnt orange: the worst one, and only when it carries some debt (the first of equal nights). */
    val worstIndex: Int? get() = debtMin.withIndex().filter { (it.value ?: 0.0) > 0.0 }.maxByOrNull { it.value!! }?.index
}

/**
 * The 14 nights ending [lastDay], each its v10 per-day debt when that night has recorded sleep ([trackedDays]),
 * else a gap. A day with no sleep is a gap, never a zero-debt night.
 */
internal fun buildDebtChart(debtByDay: Map<String, Double>, trackedDays: Set<String>, lastDay: LocalDate): DebtChart {
    val days = (0 until DEBT_NIGHTS).map { lastDay.minusDays((DEBT_NIGHTS - 1 - it).toLong()) }
    return DebtChart(days, days.map { d -> d.toString().takeIf { it in trackedDays }?.let { debtByDay[it] } })
}

/** "Last night · Tue 10 Nov" over the Playfair "Sleep" (board header). */
@Composable
internal fun RidgeSleepHeader(eyebrow: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        RidgeEyebrow(eyebrow)
        Text(
            stringResource(R.string.nav_sleep), style = RidgeType.titleXl, color = Ridge.ink,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The Rest hero: the night's v10 Rest score (sleep performance, 0–100) in the glass Rest orb, its band word, time
 * asleep, and where the score came from (as plain text; the board has no source pill, as on Today in R3b). With no
 * score, the time asleep leads, as in v10.
 */
@Composable
internal fun RidgeRestHero(score: Double?, word: String?, asleep: String?, source: String) {
    TrendCard(
        eyebrow = stringResource(R.string.sleep_ridge_rest_eyebrow),
        title = word ?: asleep?.let { stringResource(R.string.sleep_ridge_asleep, it) } ?: stringResource(R.string.trends_ridge_not_tracked),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ridge.space5),
        ) {
            if (score != null) {
                LiquidOrb(
                    fraction = score / 100.0,
                    display = score.roundToInt().toString(),
                    liquid = RidgeLiquid.REST,
                    diameter = 120.dp,
                    glass = true,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                if (asleep != null && word != null) Text(stringResource(R.string.sleep_ridge_asleep, asleep), style = TRN_SUB, color = Ridge.inkMuted)
                Text(source, style = TRN_LEGEND, color = Ridge.inkSubtle)
            }
        }
    }
}

/**
 * A debt bar as the board draws it (B2-4a-2): a pill from the baseline up to [v], filled with a vertical gradient from
 * [bottom] (deep) to [top] (bright). The worst night takes the burnt-orange pair.
 */
private fun DrawScope.debtBar(g: PlotGeometry, i: Int, v: Double, top: Color, bottom: Color) {
    val bw = g.barWidth
    val y1 = g.y(v)
    val y0 = g.y(g.axis.lo)
    val h = (y0 - y1).coerceAtLeast(bw) // never thinner than a dot, so a small debt still reads
    val left = g.x(i) - bw / 2
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(top, bottom), startY = y0 - h, endY = y0),
        topLeft = Offset(left, y0 - h), size = Size(bw, h), cornerRadius = CornerRadius(bw / 2),
    )
}

/**
 * F-02 and B2-4a-2: the board's sleep-debt card. The latest night's debt as the headline over "last night", a
 * "N of 14 tracked" pill, a gradient pill bar per night (green; the worst night burnt orange; an untracked night a
 * full-height dashed outline), the day of the month under each with the last in bold, then Average · Worst · Last
 * night. The scale is still `max(120 min, worst)` (05 §11).
 */
@Composable
internal fun RidgeSleepDebtCard(c: DebtChart) {
    val fmt = { m: Double? -> m?.let { durationText(it) } ?: "—" }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                    RidgeEyebrow(stringResource(R.string.sleep_ridge_debt_eyebrow, DEBT_NIGHTS))
                    Text(
                        c.lastNight?.let(fmt) ?: stringResource(R.string.trends_ridge_not_tracked),
                        style = RidgeType.numeralXl.copy(fontSize = 44.sp, lineHeight = 48.sp), color = Ridge.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (c.lastNight != null) Text(stringResource(R.string.sleep_ridge_debt_caption), style = TRN_SUB, color = Ridge.inkMuted)
                }
                Row(
                    Modifier
                        .align(Alignment.Top)
                        .background(Ridge.glassFill, RoundedCornerShape(Ridge.radiusPill))
                        .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusPill))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).background(Ridge.brand, CircleShape))
                    Text(stringResource(R.string.sleep_ridge_debt_tracked, c.trackedNights, DEBT_NIGHTS), style = TRN_LEGEND, color = Ridge.ink, maxLines = 1)
                }
            }
            if (c.trackedNights < 1) {
                NeedsData(stringResource(R.string.trends_ridge_needs_days, 0), 120.dp)
                return@Column
            }
            // Whole-hour ticks: up to three equal steps of whole hours covering max(120 min, worst).
            val stepH = maxOf(1.0, ceil(c.scaleMax / 60.0 / 3.0))
            val steps = ceil(c.scaleMax / 60.0 / stepH).toInt().coerceAtLeast(2)
            val hi = steps * stepH * 60.0
            val axis = Axis(0.0, hi, (0..steps).map { it * stepH * 60.0 })
            val notTracked = stringResource(R.string.trends_ridge_not_tracked)
            val debtFmt = stringResource(R.string.sleep_ridge_debt_tip)
            val worstIndex = c.worstIndex
            RidgePlot(
                n = c.days.size,
                axis = axis,
                height = 170.dp,
                bars = true,
                yLabel = { "" },
                yLabels = false,
                gridlines = false,
                baseline = true,
                xLabels = c.days.indices.map { it to c.days[it].dayOfMonth.toString() },
                xLabelBoldLast = true,
                contentDescription = stringResource(R.string.sleep_ridge_debt_a11y, DEBT_NIGHTS, fmt(c.lastNight), fmt(c.average)),
                accent = Ridge.brand,
                scrubY = { c.debtMin[it] },
                tip = { i -> ScrubTip(c.debtMin[i]?.let { debtFmt.format(fmt(it)) } ?: notTracked, dayLabel(c.days[i])) },
            ) { g ->
                c.debtMin.forEachIndexed { i, m ->
                    when {
                        m == null -> ridgeGapBar(g, i, hi) // the whole column, as the board's dashed outline
                        i == worstIndex -> debtBar(g, i, m, top = Ridge.stateLow, bottom = BURNT_ORANGE)
                        else -> debtBar(g, i, m, top = Ridge.brand, bottom = DEEP_GREEN)
                    }
                }
            }
            Stats(
                listOf(
                    Triple(stringResource(R.string.sleep_ridge_debt_avg), fmt(c.average), Ridge.ink),
                    Triple(stringResource(R.string.sleep_ridge_debt_worst), fmt(c.worst), Ridge.stateLow),
                    Triple(stringResource(R.string.sleep_ridge_debt_last), fmt(c.lastNight), Ridge.brand),
                ),
            )
        }
    }
}

/** The debt bars' deep ends: the green and the orange the board fades to at the foot of a bar, from the Ridge tokens. */
private val DEEP_GREEN = lerp(Ridge.brand, Ridge.bg000, 0.62f)
private val BURNT_ORANGE = lerp(Ridge.stateLow, Ridge.bg000, 0.5f)

/** Last night's asleep hours against the one need (B2-4a-3): the numbers the board's Hours vs Needed card reads. */
internal data class HoursVsNeeded(val sleptH: Double, val neededH: Double) {
    /** The true share, uncapped (104% when you slept past your need, #691). */
    val percent: Int get() = (sleptH / neededH * 100.0).coerceAtLeast(0.0).roundToInt()
}

/**
 * The newest night's asleep total over the full history (never an in-bed window), against the newest need, the same two
 * series the classic card reads. [asleepMinFallback] stands in when no trend rows exist; the need falls back to 8 h.
 */
internal fun hoursVsNeeded(trendHours: List<Double>, trendNeedHours: List<Double>, asleepMinFallback: Double): HoursVsNeeded =
    HoursVsNeeded(
        sleptH = trendHours.lastOrNull() ?: (asleepMinFallback / 60.0),
        neededH = trendNeedHours.lastOrNull()?.takeIf { it > 0.0 } ?: 8.0,
    )

/**
 * RS-1-2 item 5 (`Sleep.dc.html`, Hours vs Needed): last night's hours against tonight's Sleep needed, with the Rest score
 * as a chip (the orb is gone), an Hours of sleep bar, the Sleep needed bar in its three parts and their table. The need
 * is [split]'s total; with both V2 parts off that is the one Base need, so the numbers read as before.
 */
@Composable
internal fun RidgeHoursVsNeededCard(
    sleptMin: Double,
    split: SleepNeedSplit,
    score: Double?,
    source: String,
    onHowRestWorks: () -> Unit,
) {
    val hoursFmt = stringResource(R.string.sleep_ridge_hvn_hours)
    val one = { x: Double -> hoursFmt.format(String.format(java.util.Locale.getDefault(), "%.1f", x)) }
    val needMin = split.totalMin
    val percent = if (needMin > 0.0) (sleptMin / needMin * 100.0).coerceAtLeast(0.0).roundToInt() else 0
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space4)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sleep_ridge_hvn_title), style = TRN_H2, color = Ridge.ink,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    stringResource(R.string.sleep_ridge_hvn_link),
                    style = TRN_LEGEND.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    color = Ridge.brand,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onHowRestWorks).wrapContentHeight(Alignment.CenterVertically),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                    // Board: "of X needed" sits beside the hours, on their baseline.
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(one(sleptMin / 60.0), style = RidgeType.numeralLg.copy(lineHeight = 48.sp), color = Ridge.ink)
                        Text(
                            stringResource(R.string.sleep_ridge_hvn_needed, one(needMin / 60.0)),
                            style = TRN_SUB.copy(fontSize = 15.sp), color = Ridge.inkMuted, maxLines = 1,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(stringResource(R.string.sleep_ridge_hvn_slept, percent), style = TRN_SUB, color = Ridge.inkMuted)
                    // Provenance stays (each value carries its source), under the board's lines.
                    if (source.isNotBlank()) Text(source, style = TRN_LEGEND, color = Ridge.inkSubtle)
                }
                if (score != null) {
                    Text(
                        stringResource(R.string.sleep_ridge_rest_chip, score.roundToInt()),
                        style = TRN_LEGEND.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = Ridge.brand, maxLines = 1,
                        modifier = Modifier
                            .border(1.dp, Ridge.brand.copy(alpha = 0.4f), RoundedCornerShape(Ridge.radiusPill))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HvnBarHeader(stringResource(R.string.sleep_ridge_hos_title), hoursMinutesPadded(sleptMin))
                HoursOfSleepBar(hoursOfSleepFill(sleptMin, needMin), stringResource(R.string.sleep_ridge_hos_a11y, hoursMinutesPadded(sleptMin), percent, hoursMinutesPadded(needMin)))
                HvnBarHeader(stringResource(R.string.sleep_ridge_need_title), hoursMinutesPadded(needMin))
                SleepNeededBar(split)
            }
            SleepNeedTable(split)
            // RS-1-2: how the figure is built, with the Effort part named as an estimate (a NOOP heuristic, not a study's number).
            Text(stringResource(R.string.sleep_ridge_need_note), style = TRN_LEGEND, color = Ridge.inkSubtle)
        }
    }
}

/** An eyebrow on the left and its figure in the serif on the right. */
@Composable
private fun HvnBarHeader(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        RidgeEyebrow(label, Modifier.weight(1f))
        Text(value, style = RidgeType.numeralMd.copy(fontSize = 20.sp, lineHeight = 24.sp), color = Ridge.ink)
    }
}

/** The board's Hours of sleep bar: a faint track and a mint gradient fill with a soft glow, [fill] 0 to 1. */
@Composable
private fun HoursOfSleepBar(fill: Float, a11y: String) {
    val track = RoundedCornerShape(8.dp)
    Box(
        Modifier.fillMaxWidth().height(16.dp)
            .semantics { contentDescription = a11y }
            .background(Ridge.glassStroke.copy(alpha = 0.10f), track)
            .border(1.dp, Ridge.glassStroke, track),
    ) {
        if (fill > 0f) {
            Box(
                Modifier.fillMaxWidth(fill).fillMaxHeight()
                    .ridgeSoftGlow(Ridge.brand, 8.dp)
                    .background(Brush.horizontalGradient(0f to Ridge.brandDeep, 0.7f to Ridge.brand, 1f to lerp(Ridge.brand, Ridge.ink, 0.35f)), track),
            )
        }
    }
}

/** The three-part Sleep needed bar: Base (grey), Recent Effort (blue), Sleep debt (orange); parts of 0 are left out. */
@Composable
private fun SleepNeededBar(split: SleepNeedSplit) {
    val parts = listOf(
        split.baseMin to Ridge.glassStroke.copy(alpha = 0.4f),
        (split.effortMin ?: 0).toDouble() to Ridge.effort,
        split.debtMin.toDouble() to Ridge.stateLow,
    ).filter { it.first > 0.0 }
    val a11y = stringResource(
        R.string.sleep_ridge_need_a11y, hoursMinutesPadded(split.totalMin), hoursMinutesPadded(split.baseMin),
        hoursMinutesPadded((split.effortMin ?: 0).toDouble()), hoursMinutesPadded(split.debtMin.toDouble()),
    )
    Row(Modifier.fillMaxWidth().height(16.dp).semantics { contentDescription = a11y }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        parts.forEachIndexed { i, (min, color) ->
            val first = i == 0
            val last = i == parts.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) 8.dp else 3.dp, bottomStart = if (first) 8.dp else 3.dp,
                topEnd = if (last) 8.dp else 3.dp, bottomEnd = if (last) 8.dp else 3.dp,
            )
            Box(Modifier.weight(min.toFloat()).fillMaxHeight().background(color, shape))
        }
    }
}

/** The breakdown table under the bars: Base sleep, Recent Effort ("—" without enough Effort history) and Sleep debt. */
@Composable
private fun SleepNeedTable(split: SleepNeedSplit) {
    val shape = RoundedCornerShape(Ridge.radiusMd)
    Column(
        Modifier.fillMaxWidth()
            .background(Ridge.bg000.copy(alpha = 0.4f), shape)
            .border(1.dp, Ridge.glassStroke.copy(alpha = 0.10f), shape)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        NeedRow(Ridge.glassStroke.copy(alpha = 0.4f), stringResource(R.string.sleep_ridge_need_base), hoursMinutesPadded(split.baseMin), Ridge.ink, divider = false)
        NeedRow(
            Ridge.effort, stringResource(R.string.sleep_ridge_need_effort),
            split.effortMin?.let { hoursMinutesPadded(it.toDouble(), signed = true) } ?: "—", Ridge.effort, divider = true,
        )
        NeedRow(Ridge.stateLow, stringResource(R.string.sleep_ridge_need_debt), hoursMinutesPadded(split.debtMin.toDouble(), signed = true), Ridge.stateLow, divider = true)
    }
}

@Composable
private fun NeedRow(swatch: Color, label: String, value: String, valueColor: Color, divider: Boolean) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).background(swatch, RoundedCornerShape(3.dp)))
            Text(label, style = RidgeType.bodySm.copy(fontSize = 14.sp), color = Ridge.ink, modifier = Modifier.weight(1f))
            Text(value, style = RidgeType.numeralMd.copy(fontSize = 16.sp, lineHeight = 20.sp), color = valueColor)
        }
    }
}

/** Tonight's smart-wake window as minutes since local midnight (B2-4a-4); the end may wrap past midnight. */
internal data class TonightWake(val startMin: Int, val endMin: Int)

/**
 * The window tonight's phone alarm may wake you in: from the next scheduled window's earliest edge to the hard
 * deadline [windowMin] later. Null when the alarm is off, so the card never promises a wake it will not make.
 */
internal fun tonightWake(enabled: Boolean, nextStartMin: Int, windowMin: Int): TonightWake? =
    if (!enabled) null else TonightWake(nextStartMin, (nextStartMin + windowMin) % (24 * 60))

/** A minutes-since-midnight clock time in the reader's chosen 12 or 24-hour form ([ClockFormat]). */
internal fun clockOfMinutes(minutes: Int, uses24Hour: Boolean, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    java.time.LocalTime.of((minutes / 60) % 24, minutes % 60)
        .format(java.time.format.DateTimeFormatter.ofPattern(com.noop.analytics.ClockFormat.hourMinutePattern(uses24Hour), locale))

/**
 * B2-4a-4: the board's Tonight card with what the app has (owner D12: no lights-out or wind-down rule). A mint
 * "Alarm ›" link opens the Alarms screen; under it the smart-wake window and whether the alarm is on.
 */
@Composable
internal fun RidgeTonightCard(
    wake: TonightWake?, alarmOn: Boolean, uses24Hour: Boolean, onOpen: () -> Unit,
    bedtime: Bedtime? = null, nights: Int = RecommendedBedtime.MIN_NIGHTS,
) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RidgeEyebrow(stringResource(R.string.sleep_ridge_tonight_eyebrow), Modifier.weight(1f).semantics { heading() })
                Text(
                    stringResource(R.string.sleep_ridge_tonight_link),
                    style = TRN_LEGEND.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Ridge.brand,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onOpen).wrapContentHeight(Alignment.CenterVertically),
                )
            }
            // BS-x2-2 item 11: recommended bedtime = wake target minus tonight's need (never a made-up time).
            if (bedtime != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space4)) {
                    TonightClock(Modifier.weight(1f), Icons.Filled.Bedtime, clockOfMinutes(bedtime.bedMin, uses24Hour), stringResource(R.string.sleep_ridge_tonight_bedtime))
                    TonightClock(
                        Modifier.weight(1f), Icons.Filled.WbTwilight, clockOfMinutes(bedtime.wakeMin, uses24Hour),
                        stringResource(if (bedtime.fromAlarm) R.string.sleep_ridge_tonight_wake_alarm else R.string.sleep_ridge_tonight_wake_typical),
                    )
                }
                if (bedtime.paybackMin > 0) {
                    Text(stringResource(R.string.sleep_ridge_tonight_payback, bedtime.paybackMin), style = TRN_SUB, color = Ridge.inkMuted)
                }
            } else {
                Text(stringResource(R.string.sleep_ridge_tonight_needs_nights, RecommendedBedtime.MIN_NIGHTS), style = TRN_SUB, color = Ridge.inkMuted)
            }
            if (!alarmOn) {
                com.noop.ui.ridge.RidgeButton(
                    text = stringResource(R.string.sleep_ridge_tonight_set_alarm), onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(), kind = com.noop.ui.ridge.RidgeButtonKind.Secondary,
                )
            }
        }
    }
}

/** One of the card's two large clock times: an icon, the time, and what it is. */
@Composable
private fun TonightClock(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, time: String, label: String) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = Ridge.inkMuted, modifier = Modifier.size(22.dp))
            Text(time, style = RidgeType.numeralMd.copy(fontSize = 28.sp, lineHeight = 32.sp), color = Ridge.ink, maxLines = 1, softWrap = false)
        }
        Text(label.uppercase(), style = TRN_STAT_LABEL, color = Ridge.inkSubtle)
    }
}
