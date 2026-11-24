package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeCardChevron
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgeSegmentedPills
import com.noop.ui.ridge.chart.RidgePlot
import com.noop.ui.ridge.chart.ScrubTip
import com.noop.ui.ridge.chart.SleepNight
import com.noop.ui.ridge.chart.axisLabelSlots
import com.noop.ui.ridge.chart.ridgeBand
import com.noop.ui.ridge.chart.ridgeDots
import com.noop.ui.ridge.chart.ridgeEndRing
import com.noop.ui.ridge.chart.ridgeGapBar
import com.noop.ui.ridge.chart.ridgeLine
import com.noop.ui.ridge.chart.ridgeStack
import com.noop.ui.ridge.chart.scoreAxis
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Ridge Trends (R4a, `Trends.dc.html`)
//
// The Ridge page header, the 7/14/30 range pills (R-02) and the daily cards built on the R-01 primitives: Charge
// (C-TRN-CHG), Heart health (R-04, C-TRN-HH) and Sleep by stage (R-05, C-TRN-SLP). Every value comes from
// [TrendsRidgeModel], which reads the registry. Missing days are gaps, and a card with too little data says how much
// it needs (R5) instead of widening the window as v10 did (UX-TRN-6).

internal val TRN_H2 = RidgeType.numeralMd.copy(fontSize = 21.sp, lineHeight = 26.sp)
internal val TRN_SUB = RidgeType.bodySm.copy(fontWeight = FontWeight.Normal)
internal val TRN_LEGEND = RidgeType.bodySm.copy(fontSize = 12.sp, lineHeight = 16.sp)
internal val TRN_STAT_LABEL = RidgeType.eyebrow.copy(fontSize = 11.sp)
internal val TRN_STAT_VALUE = RidgeType.numeralMd.copy(fontSize = 20.sp, lineHeight = 24.sp)

internal fun whole(v: Double?): String = v?.roundToInt()?.toString() ?: "—"

/** "2,360" — a grouped whole kcal count (B2-2a-3), locale-aware like the rest of the app's grouped numbers. */
internal fun kcal(v: Double?): String =
    v?.let { java.text.NumberFormat.getIntegerInstance(Locale.getDefault()).format(it.roundToInt()) } ?: "—"

internal fun dayLabel(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

/** The board's Sleep header date form, weekday first ("Sat 19 Sep"); the eyebrow draws it in capitals. */
internal fun dayLabelWithWeekday(d: LocalDate, locale: Locale = Locale.getDefault()): String =
    d.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

internal fun xLabels(days: List<LocalDate>): List<Pair<Int, String>> =
    axisLabelSlots(days.size).map { it to dayLabel(days[it]) }

/** The page title block: "Day to day" eyebrow over the Playfair "Trends". */
@Composable
internal fun RidgeTrendsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        RidgeEyebrow(stringResource(R.string.trends_ridge_eyebrow))
        Text(
            stringResource(R.string.nav_trends), style = RidgeType.titleXl, color = Ridge.ink,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The range pills (R-02): 7 / 14 / 30 days, scoping the daily charts only. */
@Composable
internal fun RidgeTrendsPills(range: RidgeTrendsRange, onSelect: (RidgeTrendsRange) -> Unit) {
    val labels = RidgeTrendsRange.entries.associateWith { pluralStringResource(R.plurals.trends_ridge_range_days, it.days, it.days) }
    RidgeSegmentedPills(
        items = RidgeTrendsRange.entries.toList(),
        selected = range,
        onSelect = onSelect,
        label = { labels.getValue(it) },
        fill = true,
    )
}

/**
 * A Trends glass card: eyebrow, Playfair title, optional subline, then the body. [onOpen], when given, adds the
 * board's chevron on the eyebrow row (B2-2a-1) and opens that metric in Insights; the label is [R.string.nav_insights].
 */
@Composable
internal fun TrendCard(
    eyebrow: String,
    title: String,
    sub: String? = null,
    onOpen: (() -> Unit)? = null,
    trailing: String? = null,
    body: @Composable () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                    RidgeEyebrow(eyebrow)
                    Text(title, style = TRN_H2, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                    if (sub != null) Text(sub, style = TRN_SUB, color = Ridge.inkMuted)
                }
                // The right-hand header read-out (e.g. the Synthesis card's "126 days"), before the chevron.
                if (trailing != null) {
                    Text(
                        trailing, style = TRN_SUB, color = Ridge.inkMuted,
                        modifier = Modifier.align(Alignment.Top).padding(top = 2.dp),
                    )
                }
                // Top-aligned so the ring sits on the eyebrow line, as on the board, whatever the title wraps to.
                if (onOpen != null) {
                    RidgeCardChevron(
                        stringResource(R.string.nav_insights), onOpen,
                        Modifier.align(Alignment.Top),
                    )
                }
            }
            body()
        }
    }
}

/** The "needs more data" well (R5), sized like the chart it stands in for. */
@Composable
internal fun NeedsData(text: String, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Ridge.glassFill, RoundedCornerShape(Ridge.radiusSm)),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = TRN_SUB, color = Ridge.inkSubtle) }
}

internal sealed interface Swatch {
    data class Dot(val color: Color) : Swatch
    data class Line(val color: Color, val dashed: Boolean = false) : Swatch
    data class Square(val color: Color, val outlined: Boolean = false) : Swatch
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Legend(items: List<Pair<Swatch, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (sw, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (sw) {
                    is Swatch.Dot -> Box(Modifier.size(7.dp).background(sw.color, RoundedCornerShape(50)))
                    is Swatch.Line -> androidx.compose.foundation.Canvas(Modifier.width(16.dp).height(3.dp)) {
                        drawLine(
                            sw.color, androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                            androidx.compose.ui.geometry.Offset(size.width, size.height / 2), size.height,
                            pathEffect = if (sw.dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null,
                        )
                    }
                    is Swatch.Square -> Box(
                        Modifier.size(10.dp).then(
                            if (sw.outlined) Modifier.border(1.dp, sw.color, RoundedCornerShape(2.dp))
                            else Modifier.background(sw.color, RoundedCornerShape(2.dp)),
                        ),
                    )
                }
                Text(text, style = TRN_LEGEND, color = Ridge.inkMuted, maxLines = 1)
            }
        }
    }
}

/** The card's footer stats (`np-stats`): a hairline, then up to three label / Playfair value columns. */
@Composable
internal fun Stats(items: List<Triple<String, String, Color>>) {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
        HorizontalDivider(color = Ridge.glassStroke)
        // R10c: a label may take two lines ("Śr. odpoczynek", "Нагрузка, ср." were clipped to their first word on one);
        // the values stay level across the columns.
        Row(
            Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Ridge.space2),
        ) {
            items.forEach { (label, value, color) ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        label.uppercase(), style = TRN_STAT_LABEL, color = Ridge.inkSubtle, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(value, style = TRN_STAT_VALUE, color = color, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// MARK: - Effort · daily (B2-2a-2)

/** "This week 55 · down 3 on last week" from the two trailing 7-day means; [fmt] applies the Effort scale. */
@Composable
private fun weekPhrase(thisWeek: Double?, lastWeek: Double?, fmt: (Double) -> String = { whole(it) }): String? =
    thisWeek?.let { tw ->
        val delta = lastWeek?.let { (tw - it).roundToInt() }
        when {
            delta == null -> stringResource(R.string.trends_ridge_this_week, fmt(tw))
            delta > 0 -> stringResource(R.string.trends_ridge_week_up, fmt(tw), delta.toString())
            delta < 0 -> stringResource(R.string.trends_ridge_week_down, fmt(tw), abs(delta).toString())
            else -> stringResource(R.string.trends_ridge_week_same, fmt(tw))
        }
    }

/**
 * Effort over the range (B2-2a-2): daily dots, the bright 7-day mean and today ringed. The frame is the board's
 * axis-less one — no y labels, gridlines or date labels (Q6); the "Today" caption keeps the last slot legible and the
 * scrub tooltip keeps the date.
 */
@Composable
internal fun RidgeEffortCard(m: TrendsRidgeModel, effortScale: EffortScale, onOpen: (() -> Unit)? = null) {
    val e = m.effort
    fun fmt(v: Double?) = v?.let { UnitFormatter.effortDisplay(it, effortScale) } ?: "—"
    // #268: Effort reads on the user's chosen scale, here and in the week's move.
    val week = weekPhrase(e.thisWeek, e.lastWeek) { UnitFormatter.effortDisplay(it, effortScale) }
    val sub = week?.let { stringResource(R.string.trends_ridge_join, it, stringResource(R.string.trends_ridge_ringed)) }
    TrendCard(
        eyebrow = stringResource(R.string.trends_ridge_effort_eyebrow),
        title = stringResource(R.string.trends_ridge_averaging, fmt(e.average)),
        sub = sub,
        onOpen = onOpen,
    ) {
        if (e.tracked < 2) {
            NeedsData(stringResource(R.string.trends_ridge_needs_days, e.tracked), 132.dp)
        } else {
            val notTracked = stringResource(R.string.trends_ridge_not_tracked)
            val valueFmt = stringResource(R.string.trends_ridge_effort_value)
            val rollingFmt = stringResource(R.string.trends_ridge_rolling)
            val lastIdx = e.values.indexOfLast { it != null }
            val a11y = stringResource(R.string.trends_ridge_effort_a11y, m.days.size, fmt(e.average), fmt(e.today))
            RidgePlot(
                n = m.days.size,
                axis = scoreAxis,
                height = 132.dp,
                yLabel = { it.roundToInt().toString() },
                yLabels = false,
                gridlines = false,
                baseline = true,
                caption = stringResource(R.string.trends_ridge_stat_today),
                xLabels = emptyList(),
                contentDescription = a11y,
                accent = Ridge.effort,
                scrubY = { e.values[it] },
                tip = { i ->
                    val v = e.values[i]
                    val date = dayLabel(m.days[i])
                    val detail = e.mean7[i]?.let { rollingFmt.format(date, fmt(it)) } ?: date
                    ScrubTip(if (v == null) notTracked else valueFmt.format(fmt(v)), detail)
                },
            ) { g ->
                ridgeDots(g, e.values, Ridge.effort, radius = if (m.days.size > 14) 2.6.dp else 3.dp)
                ridgeLine(g, e.mean7, Ridge.effort, width = 2.5.dp)
                if (lastIdx >= 0) ridgeEndRing(g, lastIdx, e.values[lastIdx]!!, Ridge.effort)
            }
            Legend(
                listOf(
                    Swatch.Dot(Ridge.effort.copy(alpha = 0.6f)) to stringResource(R.string.trends_ridge_legend_daily),
                    Swatch.Line(Ridge.effort) to stringResource(R.string.trends_ridge_legend_mean),
                ),
            )
        }
        Stats(
            listOf(
                Triple(stringResource(R.string.trends_ridge_stat_today), fmt(e.today), Ridge.effort),
                Triple(stringResource(R.string.trends_ridge_stat_average), fmt(e.average), Ridge.effort.copy(alpha = 0.8f)),
            ),
        )
    }
}

// MARK: - Calories · daily (B2-2a-3)

/**
 * Calories over the range (B2-2a-3): stacked bars — a slate base under an amber active cap — with the board's 1k/2k/3k
 * gridlines but no date labels, and a "Today" caption. Imported days split base/active; a strap-only day draws its
 * whole-day estimate as base with no cap, and the subline says the split needs an import (checkpoint Q2).
 */
@Composable
internal fun RidgeCaloriesCard(m: TrendsRidgeModel, onOpen: (() -> Unit)? = null) {
    val c = m.calories
    val baseFill = Ridge.dataBase
    val activeFill = Ridge.dataEnergy
    val title = stringResource(R.string.trends_ridge_cal_title, kcal(c.totalAvg))
    val sub = when {
        c.activeShare != null -> stringResource(R.string.trends_ridge_cal_sub, c.activeShare)
        else -> stringResource(R.string.trends_ridge_cal_no_split)
    }
    TrendCard(
        eyebrow = stringResource(R.string.trends_ridge_cal_eyebrow),
        title = title,
        sub = sub,
        onOpen = onOpen,
    ) {
        if (c.tracked < 2) {
            NeedsData(stringResource(R.string.trends_ridge_needs_days, c.tracked), 132.dp)
        } else {
            val notTracked = stringResource(R.string.trends_ridge_not_tracked)
            val tipFmt = stringResource(R.string.trends_ridge_cal_tip)
            val todayFmt = stringResource(R.string.trends_ridge_cal_today)
            val a11y = stringResource(R.string.trends_ridge_cal_a11y, m.days.size, kcal(c.totalAvg), kcal(c.activeAvg))
            RidgePlot(
                n = m.days.size,
                axis = c.axis,
                height = 132.dp,
                bars = true,
                yLabel = { "${(it / 1000).roundToInt()}k" },
                xLabels = emptyList(),
                baseline = true,
                caption = stringResource(R.string.trends_ridge_stat_today),
                contentDescription = a11y,
                accent = activeFill,
                scrubY = { c.days[it].total },
                tip = { i ->
                    val d = c.days[i]
                    val date = if (d.isToday) todayFmt else dayLabel(d.date)
                    when {
                        d.base == null -> ScrubTip(notTracked, date)
                        else -> ScrubTip(tipFmt.format(kcal(d.total), kcal(d.base), kcal(d.active ?: 0.0)), date)
                    }
                },
            ) { g ->
                c.days.forEachIndexed { i, d ->
                    when {
                        d.base == null -> ridgeGapBar(g, i, c.axis.hi * 0.9)
                        else -> {
                            val dim = if (d.isToday) 0.3f else 0.55f
                            val dimActive = if (d.isToday) 0.55f else 0.95f
                            val segments = buildList {
                                add(d.base to baseFill.copy(alpha = dim))
                                if (d.active != null && d.active > 0.0) add(d.active to activeFill.copy(alpha = dimActive))
                            }
                            ridgeStack(g, i, segments)
                        }
                    }
                }
            }
            Legend(
                listOf(
                    Swatch.Square(baseFill.copy(alpha = 0.55f)) to stringResource(R.string.trends_ridge_cal_legend_base),
                    Swatch.Square(activeFill.copy(alpha = 0.95f)) to stringResource(R.string.trends_ridge_cal_legend_active),
                ),
            )
        }
        Stats(
            listOf(
                Triple(stringResource(R.string.trends_ridge_cal_stat_avg_day), kcal(c.totalAvg), Ridge.ink),
                Triple(stringResource(R.string.trends_ridge_cal_stat_active_avg), kcal(c.activeAvg), activeFill),
                Triple(stringResource(R.string.trends_ridge_cal_today), kcal(c.todayTotal), Ridge.ink),
            ),
        )
    }
}

// MARK: - Charge · daily (C-TRN-CHG)

@Composable
internal fun RidgeChargeCard(m: TrendsRidgeModel, onOpen: (() -> Unit)? = null) {
    val c = m.charge
    val sub = weekPhrase(c.thisWeek, c.lastWeek)
        ?.let { stringResource(R.string.trends_ridge_join, it, stringResource(R.string.trends_ridge_ringed)) }
    TrendCard(
        eyebrow = stringResource(R.string.trends_ridge_charge_eyebrow),
        title = stringResource(R.string.trends_ridge_averaging, whole(c.average)),
        sub = sub,
        onOpen = onOpen,
    ) {
        if (c.tracked < 2) {
            NeedsData(stringResource(R.string.trends_ridge_needs_days, c.tracked), 132.dp)
        } else {
            val notTracked = stringResource(R.string.trends_ridge_not_tracked)
            val valueFmt = stringResource(R.string.trends_ridge_charge_value)
            val lastIdx = c.values.indexOfLast { it != null }
            RidgePlot(
                n = m.days.size,
                axis = scoreAxis,
                height = 132.dp,
                yLabel = { it.roundToInt().toString() },
                yLabels = false,
                gridlines = false,
                baseline = true,
                caption = stringResource(R.string.trends_ridge_stat_today),
                xLabels = emptyList(),
                contentDescription = stringResource(
                    R.string.trends_ridge_charge_a11y, m.days.size, whole(c.average), whole(c.values.getOrNull(lastIdx)),
                ),
                accent = Ridge.charge,
                scrubY = { c.values[it] },
                tip = { i ->
                    val v = c.values[i]
                    ScrubTip(if (v == null) notTracked else valueFmt.format(whole(v)), dayLabel(m.days[i]))
                },
            ) { g ->
                ridgeDots(g, c.values, Ridge.charge, radius = if (m.days.size > 14) 2.6.dp else 3.dp)
                ridgeLine(g, c.mean7, Ridge.charge, width = 2.5.dp)
                if (lastIdx >= 0) ridgeEndRing(g, lastIdx, c.values[lastIdx]!!, Ridge.charge)
            }
            Legend(
                listOf(
                    Swatch.Dot(Ridge.charge.copy(alpha = 0.6f)) to stringResource(R.string.trends_ridge_legend_daily),
                    Swatch.Line(Ridge.charge) to stringResource(R.string.trends_ridge_legend_mean),
                ),
            )
        }
        Stats(
            listOf(
                Triple(stringResource(R.string.trends_ridge_stat_today), whole(c.today), Ridge.charge),
                Triple(stringResource(R.string.trends_ridge_stat_average), whole(c.average), Ridge.charge.copy(alpha = 0.8f)),
            ),
        )
    }
}

// MARK: - Open Insights (B2-2b-6)

/** The board's closing link card: a mint-bordered row that opens the Insights hub. */
@Composable
internal fun RidgeOpenInsightsCard(onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Ridge.brand.copy(alpha = 0.35f), RoundedCornerShape(Ridge.radiusMd))
            .background(Ridge.glassFill, RoundedCornerShape(Ridge.radiusMd))
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.trends_ridge_open_insights),
                style = RidgeType.bodySm.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = Ridge.ink,
            )
            Text(stringResource(R.string.trends_ridge_open_insights_sub), style = TRN_SUB, color = Ridge.inkMuted)
        }
        Icon(RidgeIcons.chevronRight, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(18.dp))
    }
}

// MARK: - Heart health (R-04, C-TRN-HH)

// MARK: - Heart health (R-04, C-TRN-HH)

/** The card title: what sits outside the normal range, or that both are within it. */
@Composable
private fun heartTitle(h: HeartTrend): String {
    val rhr = when (h.rhr.position) {
        NormalPosition.ABOVE -> stringResource(R.string.trends_ridge_rhr_above)
        NormalPosition.BELOW -> stringResource(R.string.trends_ridge_rhr_below)
        else -> null
    }
    val hrv = when (h.hrv.position) {
        NormalPosition.ABOVE -> stringResource(R.string.trends_ridge_hrv_above)
        NormalPosition.BELOW -> stringResource(R.string.trends_ridge_hrv_below)
        else -> null
    }
    return when {
        rhr != null && hrv != null -> stringResource(R.string.trends_ridge_join, rhr, hrv)
        rhr != null -> rhr
        hrv != null -> hrv
        h.rhr.position == NormalPosition.NO_BAND && h.hrv.position == NormalPosition.NO_BAND ->
            stringResource(R.string.trends_ridge_heart_building)
        else -> stringResource(R.string.trends_ridge_heart_within)
    }
}

@Composable
internal fun RidgeHeartCard(m: TrendsRidgeModel, onOpen: (() -> Unit)? = null) {
    val h = m.heart
    TrendCard(eyebrow = stringResource(R.string.trends_ridge_heart_eyebrow), title = heartTitle(h), onOpen = onOpen) {
        HeartPlot(
            m.days, h.rhr, Ridge.dataHeart, stringResource(R.string.trends_ridge_rhr_label),
            R.string.trends_ridge_bpm, showDates = false,
        )
        HeartPlot(
            m.days, h.hrv, Ridge.dataHrv, stringResource(R.string.trends_ridge_hrv_label),
            R.string.trends_ridge_ms, showDates = true,
        )
        HorizontalDivider(color = Ridge.glassStroke)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Ridge.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Before 14 nights there is no band to explain, so the key says when it arrives.
            val band = h.hrv.band ?: h.rhr.band
            val key = if (band != null) {
                stringResource(R.string.trends_ridge_heart_key)
            } else {
                stringResource(R.string.trends_ridge_band_after, maxOf(h.hrv.bandNights, h.rhr.bandNights))
            }
            Text(key, style = TRN_LEGEND, color = Ridge.inkSubtle, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.trends_ridge_nights_of, h.tracked, m.days.size),
                style = TRN_LEGEND, color = Ridge.inkMuted, maxLines = 1,
            )
        }
    }
}

/** One heart signal: the label, the latest 7-day mean with its change pill, and the nightly plot. */
@Composable
private fun HeartPlot(
    days: List<LocalDate>,
    s: HeartSeries,
    color: Color,
    label: String,
    unitRes: Int,
    showDates: Boolean,
) {
    val latest = s.latest7 ?: s.values.lastOrNull { it != null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, style = RidgeType.bodySm.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = color, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(unitRes, whole(latest)),
                style = RidgeType.numeralMd.copy(fontSize = 26.sp, lineHeight = 30.sp), color = Ridge.ink, maxLines = 1,
            )
            s.changePct?.let { pct ->
                Spacer(Modifier.width(Ridge.space2))
                val text = when {
                    pct > 0 -> stringResource(R.string.trends_ridge_change_up, pct)
                    pct < 0 -> stringResource(R.string.trends_ridge_change_down, -pct)
                    else -> stringResource(R.string.trends_ridge_change_flat)
                }
                // Colour carries the metric, never "good" or "bad"; the title's words say above or below normal.
                Text(
                    text,
                    style = RidgeType.bodySm.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
                    color = color,
                    modifier = Modifier
                        .background(color.copy(alpha = 0.13f), RoundedCornerShape(Ridge.radiusPill))
                        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(Ridge.radiusPill))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    maxLines = 1,
                )
            }
        }
        if (s.values.count { it != null } < 2) {
            NeedsData(stringResource(R.string.trends_ridge_needs_days, s.values.count { it != null }), 118.dp)
            return@Column
        }
        val notTracked = stringResource(R.string.trends_ridge_not_tracked)
        val unitFmt = stringResource(unitRes)
        val rollingFmt = stringResource(R.string.trends_ridge_rolling)
        val lastIdx = s.values.indexOfLast { it != null }
        RidgePlot(
            n = days.size,
            axis = s.axis,
            height = 118.dp,
            yLabel = { it.roundToInt().toString() },
            xLabels = if (showDates) xLabels(days) else emptyList(),
            contentDescription = stringResource(R.string.trends_ridge_heart_a11y, label, days.size, whole(s.latest7)),
            accent = color,
            scrubY = { s.values[it] ?: s.mean7[it] },
            tip = { i ->
                val v = s.values[i]
                val date = dayLabel(days[i])
                val detail = s.mean7[i]?.let { rollingFmt.format(date, whole(it)) } ?: date
                ScrubTip(if (v == null) notTracked else unitFmt.format(whole(v)), detail)
            },
        ) { g ->
            s.band?.let { ridgeBand(g, it.start, it.endInclusive, color.copy(alpha = 0.08f)) }
            // The board's mean line is a faint guide (0.45 opacity, 1.5 dp); the nightly dots carry the data.
            ridgeLine(g, s.mean7, color.copy(alpha = 0.45f), width = 1.5.dp)
            ridgeDots(
                g, s.values, color, radius = 4.dp,
                // §6.3: subtle dots, full strength outside the normal band; outliers hollow (§6.8).
                alpha = { _, v -> if (s.band != null && v !in s.band) 1f else 0.45f },
                hollow = { i, _ -> s.outlier[i] },
            )
            if (lastIdx >= 0) ridgeEndRing(g, lastIdx, s.values[lastIdx]!!, color)
        }
    }
}

// MARK: - Sleep by stage (R-05, C-TRN-SLP)

/**
 * One bar per night (§7): Deep, Light and REM stacked to time asleep (Awake is not in the bar, no "Core"); a night
 * with a duration but no stages is one neutral bar; an untracked night is a dashed outline at 7 h. Since R9b the one
 * sleep need is a dashed line per night (§7.6) and the tooltip names it (§7.8).
 */
@Composable
internal fun RidgeSleepCard(m: TrendsRidgeModel, restAvg: Double?) {
    val s = m.sleep
    // B2-2b-3: the headline names the needed hours when the one-need line is on ("Averaging 6h 26m of 7h 38m needed").
    val avgNeedHours = s.needHours.filterNotNull().takeIf { it.isNotEmpty() }?.average()
    val title = s.avgAsleepMin?.let { asleep ->
        avgNeedHours?.let { need -> stringResource(R.string.trends_ridge_sleep_title_need, hoursMinutes(asleep), hoursMinutes(need * 60)) }
            ?: stringResource(R.string.trends_ridge_sleep_title, hoursMinutes(asleep))
    } ?: stringResource(R.string.trends_ridge_not_tracked)
    TrendCard(eyebrow = stringResource(R.string.trends_ridge_sleep_eyebrow), title = title) {
        if (s.tracked < 2) {
            NeedsData(stringResource(R.string.trends_ridge_needs_days, s.tracked), 176.dp)
        } else {
            val notTracked = stringResource(R.string.trends_ridge_not_tracked)
            val noStages = stringResource(R.string.trends_ridge_no_stages)
            val asleepFmt = stringResource(R.string.trends_ridge_asleep)
            val stagesFmt = stringResource(R.string.trends_ridge_stages)
            val hoursFmt = stringResource(R.string.trends_ridge_hours)
            val dateNeedFmt = stringResource(R.string.trends_ridge_date_need)
            RidgePlot(
                n = m.days.size,
                axis = s.axis,
                height = 176.dp,
                bars = true,
                yLabel = { hoursFmt.format(it.roundToInt()) },
                xLabels = xLabels(m.days),
                contentDescription = stringResource(
                    R.string.trends_ridge_sleep_a11y, m.days.size, hoursMinutes(s.avgAsleepMin ?: 0.0),
                ),
                accent = Ridge.ink,
                tip = { i ->
                    val date = s.needHours.getOrNull(i)?.let { dateNeedFmt.format(dayLabel(m.days[i]), hoursMinutes(it * 60)) }
                        ?: dayLabel(m.days[i])
                    when (val n = s.nights[i]) {
                        SleepNight.Untracked -> ScrubTip(notTracked, date)
                        is SleepNight.Unstaged -> ScrubTip(asleepFmt.format(hoursMinutes(n.asleepMin)), date, noStages)
                        is SleepNight.Staged -> ScrubTip(
                            asleepFmt.format(hoursMinutes(n.asleepMin)), date,
                            stagesFmt.format(hoursMinutes(n.lightMin), hoursMinutes(n.deepMin), hoursMinutes(n.remMin)),
                        )
                    }
                },
            ) { g ->
                s.nights.forEachIndexed { i, n ->
                    when (n) {
                        SleepNight.Untracked -> ridgeGapBar(g, i, 7.0)
                        is SleepNight.Unstaged -> ridgeStack(g, i, listOf(n.asleepMin / 60 to RidgeChart.neutral.copy(alpha = 0.5f)))
                        is SleepNight.Staged -> ridgeStack(
                            g, i,
                            listOf(
                                n.deepMin / 60 to Ridge.sleepDeep,
                                n.lightMin / 60 to Ridge.sleepLight,
                                n.remMin / 60 to Ridge.sleepRem,
                            ),
                        )
                    }
                }
                if (s.needHours.isNotEmpty()) ridgeLine(g, s.needHours, Ridge.ink.copy(alpha = 0.7f), width = 1.5.dp, dashed = true)
            }
            Legend(
                listOfNotNull(
                    Swatch.Square(Ridge.sleepDeep) to stringResource(R.string.trends_ridge_stage_deep),
                    Swatch.Square(Ridge.sleepLight) to stringResource(R.string.trends_ridge_stage_light),
                    Swatch.Square(Ridge.sleepRem) to stringResource(R.string.trends_ridge_stage_rem),
                    Swatch.Square(RidgeChart.neutral, outlined = true) to notTracked,
                    if (s.needHours.isNotEmpty()) {
                        Swatch.Line(Ridge.ink.copy(alpha = 0.7f), dashed = true) to stringResource(R.string.trends_ridge_need_legend)
                    } else {
                        null
                    },
                ),
            )
        }
        Stats(
            listOf(
                Triple(stringResource(R.string.trends_ridge_stat_avg_asleep), s.avgAsleepMin?.let(::hoursMinutes) ?: "—", Ridge.ink),
                Triple(stringResource(R.string.trends_ridge_stat_regularity), whole(s.regularity), Ridge.ink),
                Triple(stringResource(R.string.trends_ridge_stat_rest_avg), whole(restAvg), Ridge.rest),
            ),
        )
    }
}
