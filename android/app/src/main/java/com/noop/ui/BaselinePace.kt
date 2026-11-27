package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeSheet
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/*
 * RS-1-3 item 8 (Baseline.dc.html): the Pace of aging card on the page, with its Trend View row, and the Trend View sheet
 * ("What's moving your age": the factor rows, the six-month pace and the body age vs calendar age chart). The numbers come
 * from BodyAgeTrendModel; these composables only draw them, and never draw a number that is not there.
 */

/** "0.8×", with a true minus sign below zero ("−0.4×"). */
private fun paceText(p: Double): String = (if (p < -0.049) "−" else "") + String.format(Locale.getDefault(), "%.1f×", abs(p))

@Composable
private fun PaceChip(trend: PaceTrend?) {
    trend ?: return
    val (label, color) = when (trend) {
        PaceTrend.SLOWER -> stringResource(R.string.pace_slower) to Ridge.brand
        PaceTrend.STEADY -> stringResource(R.string.pace_steady) to Ridge.inkMuted
        PaceTrend.FASTER -> stringResource(R.string.pace_faster) to Ridge.stateModerate
    }
    Row(
        Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(Ridge.radiusPill)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
    }
}

/**
 * The page's Pace of aging card: today's pace in the serif, the 40-bar scale with a marker (the five bars nearest the
 * pace lit mint, three either side a step dimmer), the scale labels, the board's sentence, and the Trend View row.
 */
@Composable
internal fun PaceOfAgingCard(t: BodyAgeTrend, onOpenTrend: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RidgeEyebrow(stringResource(R.string.pace_title), Modifier.weight(1f).semantics { heading() })
                PaceChip(BodyAgeTrendModel.versusLastMonth(t))
            }
            val pace = t.pace
            if (pace == null) {
                Text(stringResource(R.string.pace_needs_month), style = TRN_SUB, color = Ridge.inkMuted)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(paceText(pace), style = RidgeType.numeralLg.copy(lineHeight = 48.sp), color = Ridge.ink)
                    Text(stringResource(R.string.pace_unit), style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                PaceBars(pace, count = 40, height = 40.dp, a11y = stringResource(R.string.pace_a11y, paceText(pace)))
                PaceScaleLabels(stringResource(R.string.pace_real_time))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
                val months = BodyAgeTrendModel.monthsPerYear(pace)
                Text(
                    when {
                        months < 0 -> stringResource(R.string.pace_sentence_falling, -months)
                        months < 12 -> stringResource(R.string.pace_sentence_slower, months)
                        months == 12 -> stringResource(R.string.pace_sentence_same)
                        else -> stringResource(R.string.pace_sentence_faster, months)
                    },
                    style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
            val a11y = stringResource(R.string.pace_trend_a11y)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onOpenTrend)
                    .semantics(mergeDescendants = true) { contentDescription = a11y },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RidgeEyebrow(stringResource(R.string.trend_view_title), Modifier.weight(1f), color = Ridge.ink)
                Text(stringResource(R.string.trend_view_six_months), style = RidgeType.bodySm, color = Ridge.inkMuted)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ridge.inkSubtle, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The pace scale: [count] bars across -1x..3x, lit around [pace], with a light marker at it. */
@Composable
private fun PaceBars(pace: Double, count: Int, height: androidx.compose.ui.unit.Dp, a11y: String) {
    val at = BodyAgeTrendModel.scaleFraction(pace)
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = a11y }) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (count - 1)) / count
        val hot = (at * (count - 1)).let { Math.round(it) }
        for (i in 0 until count) {
            val d = abs(i - hot)
            val (h, color) = when {
                d <= 2 -> size.height to Ridge.brand
                d <= 5 -> size.height * 0.8f to Ridge.inkMuted.copy(alpha = 0.32f)
                else -> size.height * 0.6f to Ridge.inkMuted.copy(alpha = 0.14f)
            }
            drawRoundRect(color, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
        }
        val x = size.width * at
        drawLine(Ridge.ink.copy(alpha = 0.35f), Offset(x, -4.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Ridge.ink, Offset(x, -4.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun PaceScaleLabels(middle: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(paceText(BodyAgeTrendModel.PACE_MIN), style = TRN_LEGEND, color = Ridge.inkSubtle)
        Text(middle, style = TRN_LEGEND, color = Ridge.inkSubtle, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(paceText(BodyAgeTrendModel.PACE_MAX), style = TRN_LEGEND, color = Ridge.inkSubtle)
    }
}

/** The Trend View sheet: a dark sheet washed mint at the top, as on the board. */
@Composable
internal fun TrendViewSheet(t: BodyAgeTrend, onDismiss: () -> Unit) {
    RidgeSheet(onDismiss = onDismiss, top = lerp(Ridge.bg000, Ridge.brand, 0.14f)) {
        TrendViewContent(t, onDone = onDismiss)
    }
}

/** The sheet's content (stateless, so it has a snapshot). */
@Composable
internal fun TrendViewContent(t: BodyAgeTrend, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RidgeEyebrow(stringResource(R.string.trend_view_title))
            Text(stringResource(R.string.trend_view_heading), style = RidgeType.numeralMd.copy(fontSize = 26.sp, lineHeight = 30.sp), color = Ridge.ink, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.trend_view_sub), style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = Ridge.inkMuted)
        }
        if (t.rows.isNotEmpty()) FactorRows(t.rows)
        SheetPace(t)
        AgeChartCard(t.weekly)
        RidgeButton(stringResource(R.string.trend_view_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Triangle(down: Boolean, color: Color) {
    Canvas(Modifier.size(width = 10.dp, height = 7.dp)) {
        val p = Path().apply {
            if (down) { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height) }
            else { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width / 2, 0f) }
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun FactorRows(rows: List<TrendRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.trend_view_fitness), style = RidgeType.numeralMd.copy(fontSize = 20.sp, lineHeight = 24.sp), color = Ridge.ink, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Triangle(down = true, color = Ridge.ink)
                Text(stringResource(R.string.trend_view_six_month), style = TRN_LEGEND, color = Ridge.inkMuted)
                Box(Modifier.size(6.dp))
                Triangle(down = false, color = Ridge.inkSubtle)
                Text(stringResource(R.string.trend_view_thirty_day), style = TRN_LEGEND, color = Ridge.inkMuted)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Ridge.glassFillStrong, RoundedCornerShape(Ridge.radiusMd))
                .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusMd)).padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
                FactorRow(r)
            }
        }
    }
}

/** A factor's display: its name, unit, how a value prints, and the bar-end labels. */
@Composable
private fun factorName(f: TrendFactor): String = stringResource(
    when (f) {
        TrendFactor.VO2_MAX -> R.string.trend_f_vo2
        TrendFactor.RESTING_HR -> R.string.trend_f_rhr
        TrendFactor.HRV -> R.string.trend_f_hrv
        TrendFactor.STEPS -> R.string.trend_f_steps
        TrendFactor.SLEEP_REGULARITY -> R.string.trend_f_regularity
        TrendFactor.SLEEP_DURATION -> R.string.trend_f_sleep
    },
)

@Composable
private fun factorUnit(f: TrendFactor): String = stringResource(
    when (f) {
        TrendFactor.VO2_MAX -> R.string.trend_u_vo2
        TrendFactor.RESTING_HR -> R.string.trend_u_bpm
        TrendFactor.HRV -> R.string.trend_u_ms
        TrendFactor.STEPS -> R.string.trend_u_steps
        TrendFactor.SLEEP_REGULARITY -> R.string.trend_u_regularity
        TrendFactor.SLEEP_DURATION -> R.string.trend_u_sleep
    },
)

internal fun factorValue(f: TrendFactor, v: Double): String = when (f) {
    TrendFactor.VO2_MAX, TrendFactor.SLEEP_DURATION -> String.format(Locale.getDefault(), "%.1f", v)
    TrendFactor.STEPS -> String.format(Locale.getDefault(), "%,d", Math.round(v))
    else -> Math.round(v).toString()
}

/** The bar's 9-step ramp from a poor value (amber) to a good one (light mint), by the factor's own [TrendFactor.goodness]. */
private fun rampColor(g: Double): Color {
    val stops = listOf(Ridge.stateLow, Ridge.stateModerate, Ridge.brandDeep, Ridge.brand, lerp(Ridge.brand, Ridge.ink, 0.35f))
    val x = g.coerceIn(0.0, 1.0) * (stops.size - 1)
    val i = floor(x).toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[i], stops[i + 1], (x - i).toFloat())
}

@Composable
private fun FactorRow(r: TrendRow) {
    val f = r.factor
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RidgeEyebrow(factorName(f), Modifier.weight(1f), color = Ridge.ink)
            val y = r.years
            Text(
                if (y == null) stringResource(R.string.trend_not_in_age)
                else stringResource(R.string.trend_years, (if (abs(y) < 0.05) "" else if (y > 0) "+" else "−") + String.format(Locale.getDefault(), "%.1f", abs(y))),
                style = if (y == null) TRN_LEGEND else RidgeType.numeralMd.copy(fontSize = 18.sp, lineHeight = 22.sp),
                color = when { y == null -> Ridge.inkSubtle; y <= 0 -> Ridge.brand; else -> Ridge.stateModerate },
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(factorValue(f, r.now), style = RidgeType.numeralMd.copy(fontSize = 22.sp, lineHeight = 26.sp), color = Ridge.ink)
            Text(factorUnit(f), style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
        }
        val a11y = stringResource(R.string.trend_row_a11y, factorName(f), factorValue(f, r.avg6), factorValue(f, r.avg30))
        Canvas(Modifier.fillMaxWidth().height(30.dp).padding(vertical = 2.dp).semantics { contentDescription = a11y }) {
            val barTop = size.height / 2 - 3.dp.toPx()
            val gap = 2.dp.toPx()
            val segW = (size.width - gap * 8) / 9
            for (i in 0 until 9) {
                val mid = f.lo + (i + 0.5) / 9.0 * (f.hi - f.lo)
                drawRoundRect(rampColor(f.goodness(mid)), Offset(i * (segW + gap), barTop), Size(segW, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
            fun tri(x: Float, down: Boolean, c: Color) {
                val w = 5.dp.toPx(); val h = 7.dp.toPx()
                val p = Path().apply {
                    if (down) { moveTo(x - w, barTop - h - 3.dp.toPx()); lineTo(x + w, barTop - h - 3.dp.toPx()); lineTo(x, barTop - 3.dp.toPx()) }
                    else { val b = barTop + 9.dp.toPx(); moveTo(x - w, b + h); lineTo(x + w, b + h); lineTo(x, b) }
                    close()
                }
                drawPath(p, c)
            }
            tri(size.width * f.fraction(r.avg6), down = true, c = Ridge.ink)
            tri(size.width * f.fraction(r.avg30), down = false, c = Ridge.inkSubtle)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(factorValue(f, f.lo), style = TRN_LEGEND, color = Ridge.inkSubtle)
            Text(
                stringResource(R.string.trend_avg6, factorValue(f, r.avg6)), style = TRN_LEGEND, color = Ridge.inkSubtle,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(factorValue(f, f.hi), style = TRN_LEGEND, color = Ridge.inkSubtle)
        }
    }
}

/** The sheet's pace block: the six-month average pace on the 60-tick scale. */
@Composable
private fun SheetPace(t: BodyAgeTrend) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RidgeEyebrow(stringResource(R.string.pace_title), Modifier.weight(1f))
                PaceChip(BodyAgeTrendModel.versusLastMonth(t))
            }
            val avg = t.pace6mAvg
            if (avg == null) {
                Text(stringResource(R.string.pace_needs_month), style = TRN_SUB, color = Ridge.inkMuted)
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pace_slow), style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.pace_fast), style = RidgeType.bodySm, color = Ridge.inkMuted)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(paceText(avg), style = RidgeType.numeralMd.copy(fontSize = 36.sp, lineHeight = 40.sp), color = Ridge.ink)
                Text(stringResource(R.string.pace_six_month_avg), style = TRN_LEGEND, color = Ridge.inkMuted)
            }
            PaceBars(avg, count = 60, height = 36.dp, a11y = stringResource(R.string.pace_a11y_avg, paceText(avg)))
            PaceScaleLabels(paceText(1.0))
        }
    }
}

/**
 * Body age (a weekly step line, amber to mint) against calendar age (a light line) over the six months, with each line's
 * last value in a tag and one sentence on how far each moved. Under [BodyAgeTrendModel.MIN_CHART_WEEKS] points it says
 * so instead.
 */
@Composable
private fun AgeChartCard(points: List<AgePoint>) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RidgeEyebrow(stringResource(R.string.age_chart_title))
            if (points.size < BodyAgeTrendModel.MIN_CHART_WEEKS) {
                Text(stringResource(R.string.age_chart_needs), style = TRN_SUB, color = Ridge.inkMuted)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LegendSwatch(Brush.linearGradient(listOf(Ridge.stateModerate, Ridge.brand)), stringResource(R.string.age_chart_body))
                LegendSwatch(Brush.linearGradient(listOf(Ridge.ink, Ridge.ink)), stringResource(R.string.age_chart_calendar))
            }
            AgeChart(points)
            val first = points.first(); val last = points.last()
            val dBody = last.bodyAge - first.bodyAge
            val dCal = last.calendarAge - first.calendarAge
            val fmt = { x: Double -> String.format(Locale.getDefault(), "%.1f", abs(x)) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
            Text(
                stringResource(if (dBody <= 0) R.string.age_chart_sentence_down else R.string.age_chart_sentence_up, fmt(dBody), fmt(dCal)),
                style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted,
            )
        }
    }
}

@Composable
private fun LegendSwatch(brush: Brush, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(14.dp).background(brush, RoundedCornerShape(3.dp)))
        Text(label.uppercase(Locale.getDefault()), style = RidgeType.eyebrow.copy(fontSize = 12.sp), color = Ridge.inkMuted)
    }
}

@Composable
private fun AgeChart(points: List<AgePoint>) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = RidgeType.sans, fontSize = 11.sp, lineHeight = 14.sp, color = Ridge.inkSubtle)
    val tagStyle = TextStyle(fontFamily = RidgeType.sans, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val all = points.flatMap { listOf(it.bodyAge, it.calendarAge) }
    val step = if ((all.max() - all.min()) > 15) 10.0 else 5.0
    val lo = floor((all.min() - 1) / step) * step
    val hi = ceil((all.max() + 1) / step) * step
    val grid = generateSequence(lo) { it + step }.takeWhile { it <= hi + 1e-9 }.toList()
    val months = points.map { it.day.withDayOfMonth(1) }.distinct()
    val a11y = stringResource(
        R.string.age_chart_a11y,
        String.format(Locale.getDefault(), "%.1f", points.first().bodyAge), String.format(Locale.getDefault(), "%.1f", points.last().bodyAge),
        String.format(Locale.getDefault(), "%.1f", points.last().calendarAge),
    )
    Canvas(Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = a11y }) {
        val left = 38.dp.toPx(); val right = size.width - 6.dp.toPx()
        val top = 10.dp.toPx(); val bottom = size.height - 22.dp.toPx()
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        val first = points.first().day; val span = (points.last().day.toEpochDay() - first.toEpochDay()).coerceAtLeast(1)
        fun x(d: java.time.LocalDate) = left + (d.toEpochDay() - first.toEpochDay()).toFloat() / span * (right - left)
        grid.forEach { g ->
            drawLine(Ridge.glassStroke, Offset(left, y(g)), Offset(right, y(g)), 1f)
            val t = measurer.measure(String.format(Locale.getDefault(), "%.1f", g), label)
            drawText(t, topLeft = Offset(0f, y(g) - t.size.height / 2f))
        }
        months.forEach { m ->
            val mx = x(m.plusDays(14)).coerceIn(left, right)
            if (!m.plusDays(14).isBefore(first) && !m.plusDays(14).isAfter(points.last().day)) {
                val t = measurer.measure(m.month.getDisplayName(DayTextStyle.SHORT, Locale.getDefault()), label)
                drawText(t, topLeft = Offset(mx - t.size.width / 2f, bottom + 6.dp.toPx()))
            }
        }
        val cal = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.day), y(p.calendarAge)) else lineTo(x(p.day), y(p.calendarAge)) } }
        drawPath(cal, Ridge.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val body = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(x(p.day), y(p.bodyAge))
                else { lineTo(x(p.day), y(points[i - 1].bodyAge)); lineTo(x(p.day), y(p.bodyAge)) }
            }
        }
        drawPath(
            body, Brush.horizontalGradient(listOf(Ridge.stateModerate, Ridge.brand), startX = left, endX = right),
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val last = points.last()
        val ex = x(last.day)
        drawCircle(Ridge.bg100, 4.5.dp.toPx(), Offset(ex, y(last.calendarAge)))
        drawCircle(Ridge.ink, 4.5.dp.toPx(), Offset(ex, y(last.calendarAge)), style = Stroke(2.dp.toPx()))
        drawCircle(Ridge.brand, 4.dp.toPx(), Offset(ex, y(last.bodyAge)))
        fun tag(v: Double, color: Color, above: Boolean) {
            val t = measurer.measure(String.format(Locale.getDefault(), "%.1f", v), tagStyle.copy(color = color))
            val w = t.size.width + 12.dp.toPx(); val h = 20.dp.toPx()
            val tx = (ex - w - 6.dp.toPx()).coerceAtLeast(left)
            val ty = if (above) y(v) - h - 6.dp.toPx() else y(v) + 6.dp.toPx()
            drawRoundRect(Ridge.bg000.copy(alpha = 0.94f), Offset(tx, ty), Size(w, h), CornerRadius(6.dp.toPx()))
            drawText(t, topLeft = Offset(tx + 6.dp.toPx(), ty + (h - t.size.height) / 2f))
        }
        val bodyBelow = last.bodyAge <= last.calendarAge
        tag(last.calendarAge, Ridge.ink, above = bodyBelow)
        tag(last.bodyAge, Ridge.brand, above = !bodyBelow)
    }
}
