package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.chart.Axis
import com.noop.ui.ridge.chart.PlotGeometry
import com.noop.ui.ridge.chart.RidgePlot
import com.noop.ui.ridge.chart.ScrubTip
import com.noop.ui.ridge.chart.axisLabelSlots
import com.noop.ui.ridge.chart.ridgeDots
import com.noop.ui.ridge.chart.ridgeLine
import com.noop.ui.ridge.chart.ridgeStack
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// MARK: - Ridge Trends fixed-window cards (R4b, `Trends.dc.html`)
//
// Load & recovery (R-03, C-TRN-LOAD), Intensity (R-07, C-TRN-INT) and Consistency (R-06, C-TRN-CON). Each names its
// fixed window in the eyebrow, since the range pills do not scope it (UX-TRN-1).

// MARK: - Load & recovery (R-03)

/** The load axis (§3.6): from `floor10(min(lines, 30))` to `ceil10(max(bars, lines, 80))`, ticks every 10. */
internal fun loadAxis(t: LoadTrend): Axis {
    val lines = (t.acute + t.chronic).filterNotNull()
    val lo = floor(minOf(lines.minOrNull() ?: 30.0, 30.0) / 10) * 10
    val hi = ceil(maxOf((t.effort.filterNotNull() + lines).maxOrNull() ?: 80.0, 80.0) / 10) * 10
    return Axis(lo, hi, generateSequence(lo) { it + 10 }.takeWhile { it <= hi + 1e-9 }.toList())
}

@Composable
internal fun RidgeLoadCard(
    t: LoadTrend,
    effortScale: EffortScale,
    scatter: Map<Int, ChargeEffortScatter> = emptyMap(),
    onOpen: (() -> Unit)? = null,
) {
    var showScatter by remember { mutableStateOf(false) }
    fun fmt(v: Double?) = v?.let { UnitFormatter.effortDisplay(it, effortScale) } ?: "—"
    val ratio = t.latestRatio
    // The title follows v10's acute:chronic bands (ReadinessEngine.acwrSignal), so Trends agrees with Today.
    val title = when {
        ratio == null -> stringResource(R.string.trends_ridge_load_needs, t.readings)
        ratio < 0.8 -> stringResource(R.string.trends_ridge_load_lighter)
        ratio < LOAD_RAMP_RATIO -> stringResource(R.string.trends_ridge_load_usual)
        ratio < 1.5 -> stringResource(R.string.trends_ridge_load_building)
        else -> stringResource(R.string.trends_ridge_load_spiking)
    }
    val sub = if (ratio != null) stringResource(R.string.trends_ridge_load_sub, fmt(t.latestAcute), fmt(t.latestChronic)) else null
    TrendCard(eyebrow = stringResource(R.string.trends_ridge_load_eyebrow), title = title, sub = sub, onOpen = onOpen) {
        if (ratio != null) {
            val pct = ((ratio - 1) * 100).roundToInt()
            val ramp = ratio >= LOAD_RAMP_RATIO
            val color = if (ramp) Ridge.stateModerate else Ridge.inkMuted
            Text(
                when {
                    pct > 0 -> stringResource(R.string.trends_ridge_change_up, pct)
                    pct < 0 -> stringResource(R.string.trends_ridge_change_down, -pct)
                    else -> stringResource(R.string.trends_ridge_change_flat)
                },
                style = TRN_LEGEND, color = color,
                modifier = Modifier
                    .background(color.copy(alpha = 0.10f), RoundedCornerShape(Ridge.radiusPill))
                    .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(Ridge.radiusPill))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        val axis = loadAxis(t)
        val effortFmt = stringResource(R.string.trends_ridge_effort_value)
        val tipFmt = stringResource(R.string.trends_ridge_load_tip)
        val notTracked = stringResource(R.string.trends_ridge_not_tracked)
        RidgePlot(
            n = t.days.size,
            axis = axis,
            height = 160.dp,
            bars = true,
            // Whole ticks on 0–100; the WHOOP 0–21 scale keeps its one decimal (#268).
            yLabel = { if (effortScale == EffortScale.WHOOP) fmt(it) else it.roundToInt().toString() },
            xLabels = axisLabelSlots(t.days.size).map { it to dayLabel(t.days[it]) },
            contentDescription = stringResource(R.string.trends_ridge_load_a11y, fmt(t.latestAcute), fmt(t.latestChronic)),
            accent = Ridge.effort,
            scrubY = { t.acute[it] },
            tip = { i ->
                val e = t.effort[i]
                val date = dayLabel(t.days[i])
                val a = t.acute[i]
                val c = t.chronic[i]
                ScrubTip(
                    if (e == null) notTracked else effortFmt.format(fmt(e)),
                    if (a != null && c != null) tipFmt.format(date, fmt(a), fmt(c)) else date,
                )
            },
        ) { g ->
            // Ramp days: one gold band per run, behind everything (§3.10, 10% by decision).
            val slot = g.width / t.days.size
            var i = 0
            while (i < t.ramp.size) {
                if (!t.ramp[i]) { i++; continue }
                val start = i
                while (i < t.ramp.size && t.ramp[i]) i++
                drawRect(Ridge.stateModerate.copy(alpha = 0.10f), Offset(start * slot, 0f), Size((i - start) * slot, size.height))
            }
            // Daily Effort bars; a bar under the floor is a 2 px stub at it, never hidden (§3.7).
            val floorY = g.y(axis.lo)
            t.effort.forEachIndexed { k, e ->
                if (e == null) return@forEachIndexed
                val top = minOf(g.y(e), floorY - 2f)
                drawRect(Ridge.effort.copy(alpha = 0.28f), Offset(g.x(k) - g.barWidth / 2, top), Size(g.barWidth, floorY - top))
            }
            ridgeLine(g, t.chronic, Ridge.inkMuted, width = 2.dp, dashed = true)
            ridgeLine(g, t.acute, Ridge.effort, width = 2.5.dp)
            val last = t.acute.indexOfLast { it != null }
            if (last >= 0) {
                t.chronic[last]?.let { drawCircle(Ridge.inkMuted, 4.dp.toPx(), Offset(g.x(last), g.y(it))) }
                drawCircle(Ridge.bg100, 4.5.dp.toPx(), Offset(g.x(last), g.y(t.acute[last]!!)))
                drawCircle(Ridge.effort, 4.5.dp.toPx(), Offset(g.x(last), g.y(t.acute[last]!!)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            }
        }
        Legend(
            listOf(
                Swatch.Line(Ridge.effort) to stringResource(R.string.trends_ridge_load_acute, fmt(t.latestAcute)),
                Swatch.Line(Ridge.inkMuted, dashed = true) to stringResource(R.string.trends_ridge_load_chronic, fmt(t.latestChronic)),
                Swatch.Square(Ridge.effort.copy(alpha = 0.4f)) to stringResource(R.string.trends_ridge_load_daily),
                Swatch.Square(Ridge.stateModerate.copy(alpha = 0.35f)) to stringResource(R.string.trends_ridge_load_ramp),
            ),
        )
        Text(stringResource(R.string.trends_ridge_load_note), style = TRN_LEGEND, color = Ridge.inkSubtle)
        // B2-2b-1: the secondary insight is an inline mini card (thumbnail + correlation sentence), not a bare link.
        val sc = scatter[1]
        if (sc != null && sc.r != null) {
            val rText = sc.r.let { String.format(Locale.getDefault(), "%+.2f", it).replace('-', '\u2212') }
            val sentence = stringResource(
                R.string.trends_ridge_scatter_mini,
                if (sc.r < -0.3) stringResource(R.string.trends_ridge_scatter_harder) else stringResource(R.string.trends_ridge_scatter_clear),
                rText,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Ridge.glassStroke.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .background(Ridge.glassFill, RoundedCornerShape(16.dp))
                    .clickable { showScatter = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScatterThumbnail(sc.points, sc.r < -0.3)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.trends_ridge_scatter_link),
                        style = RidgeType.bodySm.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        color = Ridge.ink,
                    )
                    Text(sentence, style = TRN_LEGEND, color = Ridge.inkMuted)
                }
                Icon(RidgeIcons.chevronRight, contentDescription = null, tint = Ridge.inkSubtle, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (showScatter) RidgeScatterSheet(scatter, effortScale) { showScatter = false }
}

/** The mini card's 96 × 66 scatter thumbnail: Charge (y) against the previous day's Effort (x), with the fit line. */
@Composable
private fun ScatterThumbnail(points: List<Pair<Double, Double>>, fit: Boolean) {
    Canvas(Modifier.size(96.dp, 66.dp)) {
        val axis = Color(190, 220, 235).copy(alpha = 0.3f)
        val left = 6.dp.toPx()
        val right = 94.dp.toPx()
        val top = 4.dp.toPx()
        val bottom = 62.dp.toPx()
        drawLine(axis, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        drawLine(axis, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
        fun x(e: Double) = left + (e / 100.0).coerceIn(0.0, 1.0).toFloat() * (right - left)
        fun y(c: Double) = bottom - (c / 100.0).coerceIn(0.0, 1.0).toFloat() * (bottom - top)
        for ((e, c) in points) drawCircle(Ridge.rest.copy(alpha = 0.7f), 2.2.dp.toPx(), Offset(x(e), y(c)))
        if (fit && points.size >= 2) {
            val mx = points.sumOf { it.first } / points.size
            val my = points.sumOf { it.second } / points.size
            val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
            val sxy = points.sumOf { (it.first - mx) * (it.second - my) }
            if (sxx > 0.0) {
                val slope = sxy / sxx
                val intercept = my - slope * mx
                drawLine(
                    Ridge.ink, Offset(x(0.0), y(intercept)), Offset(x(100.0), y(slope * 100 + intercept)),
                    1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
            }
        }
    }
}

/**
 * R-08 (05 §3, C-TRN-SCAT): each day's Charge against the Effort 1–3 days before it, over the last [SCATTER_DAYS] days,
 * with Pearson r and the number of paired days, shown only from [SCATTER_MIN_PAIRS] pairs, and the caution line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RidgeScatterSheet(scatter: Map<Int, ChargeEffortScatter>, effortScale: EffortScale, onDismiss: () -> Unit) {
    var lag by remember { mutableStateOf(1) }
    val sc = scatter[lag] ?: return
    val lagLabels = mapOf(
        1 to stringResource(R.string.trends_ridge_scatter_lag_1),
        2 to stringResource(R.string.trends_ridge_scatter_lag_2),
        3 to stringResource(R.string.trends_ridge_scatter_lag_3),
    )
    // A true minus sign, as 05 §3 writes it ("r = −0.73").
    val rText = sc.r?.let { String.format(Locale.getDefault(), "%+.2f", it).replace('-', '\u2212') }
    // Fully open: half-open, the plot's axis title and the caution line sat below the fold.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Ridge.bg100) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Ridge.space4).padding(bottom = Ridge.space6),
            verticalArrangement = Arrangement.spacedBy(Ridge.space3),
        ) {
            Text(stringResource(R.string.trends_ridge_scatter_link), style = TRN_H2, color = Ridge.ink)
            SegmentedPillControl(items = listOf(1, 2, 3), selection = lag, label = { lagLabels.getValue(it) }, onSelect = { lag = it })
            if (rText == null) {
                NeedsData(stringResource(R.string.trends_ridge_scatter_needs, SCATTER_MIN_PAIRS, sc.n), 220.dp)
            } else {
                Text(stringResource(R.string.trends_ridge_scatter_stat, sc.n, rText), style = TRN_SUB, color = Ridge.ink)
                val a11y = stringResource(R.string.trends_ridge_scatter_a11y, lagLabels.getValue(lag), sc.n, rText)
                Text(stringResource(R.string.trends_ridge_scatter_y), style = TRN_LEGEND, color = Ridge.rest)
                ScatterPlot(sc.points, effortScale, Modifier.fillMaxWidth().height(232.dp).semantics { contentDescription = a11y })
                Text(
                    stringResource(R.string.trends_ridge_scatter_x, lagLabels.getValue(lag)),
                    style = TRN_LEGEND, color = Ridge.effort, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            Text(stringResource(R.string.trends_ridge_scatter_note), style = TRN_LEGEND, color = Ridge.inkSubtle)
        }
    }
}

/** Effort (x, stored 0–100) against Charge (y, 0–100): ticks on both axes, a dashed quarter grid, one dot per day. */
@Composable
private fun ScatterPlot(points: List<Pair<Double, Double>>, effortScale: EffortScale, modifier: Modifier) {
    // Whole ticks on 0–100; the WHOOP 0–21 scale keeps its one decimal, as the load card's axis (#268).
    val labels = listOf(0.0, 50.0, 100.0).map {
        if (effortScale == EffortScale.WHOOP) UnitFormatter.effortDisplay(it, effortScale) else it.roundToInt().toString()
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
        Column(Modifier.fillMaxHeight().padding(end = Ridge.space2), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("100", "50", "0").forEach { Text(it, style = TRN_LEGEND, color = Ridge.inkSubtle) }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            for (k in 0..4) {
                val y = size.height * k / 4f
                drawLine(RidgeChart.grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            }
            for ((e, c) in points) {
                val x = (e / 100.0).coerceIn(0.0, 1.0).toFloat() * size.width
                val y = (1f - (c / 100.0).coerceIn(0.0, 1.0).toFloat()) * size.height
                drawCircle(Ridge.rest.copy(alpha = 0.75f), 4.dp.toPx(), Offset(x, y))
            }
        }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp)) {
            Text(labels[0], style = TRN_LEGEND, color = Ridge.inkSubtle, modifier = Modifier.weight(1f))
            Text(labels[1], style = TRN_LEGEND, color = Ridge.inkSubtle, modifier = Modifier.weight(1f))
            Text(labels[2], style = TRN_LEGEND, color = Ridge.inkSubtle)
        }
    }
}

// MARK: - Intensity (R-07)

/** Minutes axis: 0 to three equal steps of a whole multiple of 30 min. */
internal fun minutesAxis(maxMinutes: Double): Axis {
    val step = maxOf(30.0, ceil(maxMinutes / 3 / 30) * 30)
    return Axis(0.0, step * 3, listOf(0.0, step, step * 2, step * 3))
}

/** The Intensity Y-axis tick label: whole minutes with a short unit, like Calories' "1k" ("30m", "60m"). */
internal fun minutesTickLabel(minutes: Double): String = "${minutes.roundToInt()}m"

/** BS-x2-1 item 6: the Intensity plot is 1.5 times its old 128 dp so a low and a high week read apart. */
private val INTENSITY_PLOT_HEIGHT = 192.dp

@Composable
internal fun RidgeIntensityCard(t: IntensityTrend, onOpen: (() -> Unit)? = null) {
    val total = t.total
    // B2-2b-2: the board's title is a distribution summary, not the minute count.
    val title = when {
        total <= 0 -> stringResource(R.string.trends_ridge_int_none)
        t.high / total >= 0.15 -> stringResource(R.string.trends_ridge_int_more_hard)
        else -> stringResource(R.string.trends_ridge_int_mostly_easy)
    }
    TrendCard(
        eyebrow = stringResource(R.string.trends_ridge_int_eyebrow),
        title = title,
        onOpen = onOpen,
    ) {
        if (total <= 0) {
            NeedsData(stringResource(R.string.trends_ridge_int_coverage, t.sessionsWithZones, t.sessions), 128.dp)
            return@TrendCard
        }
        fun pct(v: Double) = (v / total * 100).roundToInt()
        val minFmt = stringResource(R.string.trends_ridge_minutes)
        val tipFmt = stringResource(R.string.trends_ridge_int_tip)
        val thisWeek = stringResource(R.string.trends_ridge_this_week_short)
        val axis = minutesAxis(t.weeks.maxOf { it.total })
        RidgePlot(
            n = t.weeks.size,
            axis = axis,
            height = INTENSITY_PLOT_HEIGHT,
            bars = true,
            yLabel = ::minutesTickLabel,
            baseline = true,
            xLabels = t.weeks.indices.map { it to if (it == t.weeks.lastIndex) thisWeek else dayLabel(t.weeks[it].start) },
            contentDescription = stringResource(R.string.trends_ridge_int_a11y, pct(t.low), pct(t.moderate), pct(t.high)),
            accent = Ridge.stateModerate,
            scrubY = { t.weeks[it].total },
            tip = { i ->
                val w = t.weeks[i]
                ScrubTip(minFmt.format(w.total.roundToInt().toString()), tipFmt.format(dayLabel(w.start), w.high.roundToInt().toString()))
            },
        ) { g ->
            t.weeks.forEachIndexed { i, w ->
                ridgeStack(g, i, listOf(w.low to Ridge.effort, w.moderate to Ridge.stateModerate, w.high to Ridge.stateDepleted))
            }
        }
        Legend(
            listOf(
                Swatch.Square(Ridge.effort) to stringResource(R.string.trends_ridge_int_low, pct(t.low)),
                Swatch.Square(Ridge.stateModerate) to stringResource(R.string.trends_ridge_int_mod, pct(t.moderate)),
                Swatch.Square(Ridge.stateDepleted) to stringResource(R.string.trends_ridge_int_high, pct(t.high)),
            ),
        )
        Text(
            stringResource(R.string.trends_ridge_int_coverage, t.sessionsWithZones, t.sessions),
            style = TRN_LEGEND, color = Ridge.inkSubtle,
        )
    }
}
// MARK: - Synthesis calendar (B2-2a-6, C-TRN-SYN)

/** The Synthesis heat-grid cell size (the board's 13 px cell, 3 px gap). */
private val SYN_CELL = 13.dp
private val SYN_GAP = 3.dp

@Composable
internal fun RidgeSynthesisCard(s: SynthesisTrend, onOpen: (() -> Unit)? = null) {
    TrendCard(
        eyebrow = stringResource(R.string.trends_ridge_syn_eyebrow),
        title = stringResource(R.string.trends_ridge_syn_title),
        sub = stringResource(R.string.trends_ridge_syn_sub, SYNTHESIS_WEEKS),
        onOpen = onOpen,
        trailing = stringResource(R.string.trends_ridge_syn_days, s.tracked),
    ) {
        // Monday-first weekday labels on the left (M/W/F/S), then the month row and the 20-column heat grid.
        DayTipSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(SYN_GAP), modifier = Modifier.padding(top = 18.dp)) {
                // Rows 0/2/4/6 of a Monday-first column are M / W / F / S (the board leaves the rest blank).
                listOf(DayOfWeek.MONDAY, null, DayOfWeek.WEDNESDAY, null, DayOfWeek.FRIDAY, null, DayOfWeek.SUNDAY)
                    .forEach { dow ->
                        Box(Modifier.width(12.dp).height(SYN_CELL), contentAlignment = Alignment.Center) {
                            if (dow != null) {
                                Text(
                                    dow.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()),
                                    style = TRN_STAT_LABEL, color = Ridge.inkSubtle,
                                )
                            }
                        }
                    }
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SYN_GAP), modifier = Modifier.height(14.dp)) {
                    s.monthLabels.forEach { m ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (m.isNotEmpty()) Text(m, style = TRN_STAT_LABEL, color = Ridge.inkSubtle)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SYN_GAP)) {
                    for (w in 0 until SYNTHESIS_WEEKS) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SYN_GAP)) {
                            for (dow in 0 until 7) {
                                SynthesisCellView(s.cells[w * 7 + dow])
                            }
                        }
                    }
                }
            }
        }
        }
        // The Depleted → Peak gradient key.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.today_synthesis_depleted), style = TRN_LEGEND, color = Ridge.inkMuted)
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(SYNTHESIS_STATE_COLOURS),
                        RoundedCornerShape(3.dp),
                    ),
            )
            Text(stringResource(R.string.today_synthesis_peak), style = TRN_LEGEND, color = Ridge.inkMuted)
        }
        SynthesisShareStats(s)
    }
}

/** The five Synthesis stage names, Depleted → Peak, as the Today card draws them. */
private val SYNTHESIS_WORD_RES = listOf(
    R.string.today_synthesis_depleted,
    R.string.today_synthesis_low,
    R.string.today_synthesis_steady,
    R.string.today_synthesis_strong,
    R.string.today_synthesis_peak,
)

@Composable
private fun SynthesisCellView(c: SynthesisCell) {
    val shape = RoundedCornerShape(4.dp)
    val fill = c.band?.let { SYNTHESIS_STATE_COLOURS[it] }
    var m = Modifier.fillMaxWidth().height(SYN_CELL)
    if (!c.future) {
        m = if (fill != null) m.background(fill, shape)
        else m.background(Ridge.glassFill, shape).border(1.dp, Ridge.glassStroke.copy(alpha = 0.7f), shape)
    }
    val cd = if (c.band != null && !c.future) {
        val name = stringResource(SYNTHESIS_WORD_RES[c.band])
        stringResource(R.string.trends_ridge_syn_cell, dayLabel(c.date), name)
    } else null
    if (cd != null) m = m.semantics { contentDescription = cd }
    DayTipBox(c.date, !c.future, m) {}
}

@Composable
private fun SynthesisShareStats(s: SynthesisTrend) {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
        HorizontalDivider(color = Ridge.glassStroke)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            SynthesisShareStat(s.strongOrPeak, stringResource(R.string.trends_ridge_syn_strong), Ridge.brand)
            SynthesisShareStat(s.steady, stringResource(R.string.today_synthesis_steady), Ridge.stateModerate)
            SynthesisShareStat(s.lowOrDepleted, stringResource(R.string.trends_ridge_syn_low), Ridge.stateLow)
        }
    }
}

@Composable
private fun RowScope.SynthesisShareStat(percent: Int, label: String, color: Color) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        Text("$percent%", style = TRN_STAT_VALUE, color = color)
        // minLines reserves a second line so a one-word label ("Steady") aligns its bar with the two-line labels.
        Text(label, style = TRN_STAT_LABEL, color = Ridge.inkSubtle, maxLines = 2, minLines = 2)
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Ridge.glassStroke, RoundedCornerShape(2.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}



// MARK: - Other daily signals (B2-2b-4, C-TRN-SIG)

/** The metric key each Other-daily-signals tile opens (BS-x2-2 item 7: Stress passed "insights", the Journal). */
internal object SIGNAL_TILE_KEYS {
    const val stress = "stress"
    const val breath = "resp"
    const val skin = "skin"
    const val spo2 = "spo2"
    val all = listOf(stress, breath, skin, spo2)
}

@Composable
internal fun RidgeSignalsCard(s: SignalsTrend, onOpenMetric: (String) -> Unit = {}) {
    TrendCard(
        eyebrow = stringResource(R.string.trends_ridge_sig_eyebrow),
        title = stringResource(R.string.trends_ridge_sig_title),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalTileView(
                    label = stringResource(R.string.trends_ridge_sig_stress), color = Ridge.stateModerate,
                    value = sigFmt(s.stress.latest, 1),
                    unit = stringResource(R.string.l10n_stress_screen_of_3_46203495),
                    state = signalState(
                        StressBand.forScore(s.stress.latest ?: 0.0).title,
                        s.stress.inNormal, stringResource(R.string.trends_ridge_sig_in_normal),
                    ),
                    values = s.stress.values, Modifier.weight(1f), onClick = { onOpenMetric(SIGNAL_TILE_KEYS.stress) },
                )
                SignalTileView(
                    label = stringResource(R.string.trends_ridge_sig_breath), color = Ridge.dataBreath,
                    value = sigFmt(s.resp.latest, 1),
                    unit = stringResource(R.string.today_unit_rpm),
                    state = signalState(
                        stringResource(R.string.trends_ridge_sig_overnight),
                        s.resp.inNormal, stringResource(R.string.trends_ridge_sig_in_normal),
                    ),
                    values = s.resp.values, Modifier.weight(1f), onClick = { onOpenMetric(SIGNAL_TILE_KEYS.breath) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalTileView(
                    label = stringResource(R.string.trends_ridge_sig_skin), color = Ridge.dataEnergy,
                    value = signedFmt(s.skinTemp.latest),
                    unit = stringResource(R.string.trends_ridge_sig_unit_c),
                    state = signalState(
                        stringResource(R.string.skin_temp_vs_baseline),
                        true, skinTrendWord(s.skinTemp.trend),
                    ),
                    values = s.skinTemp.values, Modifier.weight(1f), onClick = { onOpenMetric(SIGNAL_TILE_KEYS.skin) },
                )
                SignalTileView(
                    label = stringResource(R.string.trends_ridge_sig_spo2), color = Ridge.effort,
                    value = sigFmt(s.spo2.latest, 0),
                    unit = stringResource(R.string.trends_ridge_sig_unit_pct),
                    state = (if (s.spo2.isEstimate) {
                        stringResource(R.string.trends_ridge_sig_strap_estimate)
                    } else {
                        signalState(
                            stringResource(R.string.trends_ridge_sig_overnight),
                            s.spo2.inNormal, stringResource(R.string.trends_ridge_sig_in_normal),
                        )
                    }) + (s.spo2.asOf?.let { " · " + it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())) } ?: ""),
                    values = s.spo2.values, Modifier.weight(1f), onClick = { onOpenMetric(SIGNAL_TILE_KEYS.spo2) },
                )
            }
        }
    }
}

/** "Settled" → "Settled · in normal" when the signal is inside its normal; "overnight" and "vs baseline" join alike. */
private fun signalState(first: String, normal: Boolean, second: String): String =
    if (normal) "$first · $second" else first

@Composable
private fun skinTrendWord(trend: SignalTrend): String = stringResource(
    when (trend) {
        SignalTrend.RISING -> R.string.trends_ridge_sig_rising
        SignalTrend.FALLING -> R.string.trends_ridge_sig_falling
        SignalTrend.STEADY -> R.string.trends_ridge_sig_steady
    },
)

@Composable
private fun SignalTileView(
    label: String,
    color: Color,
    value: String,
    unit: String,
    state: String,
    values: List<Double?>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Ridge.radiusSm))
            .clickable(onClick = onClick)
            .background(Ridge.glassFill, RoundedCornerShape(Ridge.radiusSm))
            .padding(Ridge.space3),
        verticalArrangement = Arrangement.spacedBy(Ridge.space1),
    ) {
        Text(label.uppercase(), style = TRN_STAT_LABEL, color = color, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = TRN_STAT_VALUE, color = Ridge.ink)
            Text(unit, style = TRN_SUB, color = Ridge.inkMuted, modifier = Modifier.padding(bottom = 2.dp))
        }
        SignalSparkline(values, color)
        Text(state, style = TRN_LEGEND, color = Ridge.inkSubtle, maxLines = 1)
    }
}

/**
 * The tile's sparkline drawn with the Heart health line style (B2-2b-4 refinement): the same [ridgeLine] / [ridgeDots]
 * helpers the HRV/RHR plots use — round caps, a dot per day, and the line breaks across gaps instead of skipping them.
 */
@Composable
private fun SignalSparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
    val nn = values.filterNotNull()
    val lo = nn.minOrNull() ?: 0.0
    val hi = nn.maxOrNull() ?: 1.0
    val pad = ((hi - lo) * 0.15).coerceAtLeast(0.1)
    val axis = Axis(lo - pad, hi + pad, emptyList())
    Canvas(modifier.fillMaxWidth().height(24.dp)) {
        val g = PlotGeometry(size.width, 1.dp.toPx(), size.height - 1.dp.toPx(), values.size, bars = false, axis = axis)
        ridgeLine(g, values, color, width = 2.dp)
        ridgeDots(g, values, color, radius = 1.5.dp)
    }
}

/** A one-decimal (or whole) signal value; an empty read-out is an en dash. */
private fun sigFmt(v: Double?, decimals: Int): String = v?.let {
    if (decimals == 0) it.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", it)
} ?: "—"

/** Skin temp is signed: "+0.6" / "−0.3"; a value that rounds to zero drops the sign. */
private fun signedFmt(v: Double?): String = v?.let {
    val s = String.format(Locale.getDefault(), "%.1f", it)
    when {
        s == "-0.0" -> "0.0"
        it > 0.0 && !s.startsWith("+") -> "+$s"
        else -> s
    }
} ?: "—"

// MARK: - Consistency (R-06)

// MARK: - Consistency (R-06)

private val CELL = 22.dp
private val CELL_GAP = 4.dp

@Composable
internal fun RidgeConsistencyCard(c: ConsistencyTrend) {
    val perWeek = c.trained * 7.0 / c.elapsed.coerceAtLeast(1)
    TrendCard(
        eyebrow = stringResource(R.string.trends_ridge_con_eyebrow),
        title = stringResource(
            R.string.trends_ridge_con_title,
            String.format(Locale.getDefault(), "%.1f", perWeek), CONSISTENCY_WEEKS,
        ),
    ) {
        // B2-2b-5: the board's three intensities — the app's four Effort bands collapse into them.
        val intensity = mapOf(
            EffortBand.REST to stringResource(R.string.trends_ridge_con_rest_day),
            EffortBand.MODERATE to stringResource(R.string.trends_ridge_con_light_mod),
            EffortBand.HIGH to stringResource(R.string.trends_ridge_con_high_plus),
            EffortBand.ALL_OUT to stringResource(R.string.trends_ridge_con_high_plus),
        )
        val cellFmt = stringResource(R.string.trends_ridge_con_cell)
        val untrackedFmt = stringResource(R.string.trends_ridge_con_cell_untracked)
        val sleptTarget = stringResource(R.string.trends_ridge_con_slept_target)
        val sleptBelow = stringResource(R.string.trends_ridge_con_slept_below)
        val sleptNone = stringResource(R.string.trends_ridge_con_slept_untracked)
        DayTipSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // M–S, Monday first, in the reader's language.
            Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                c.cells.take(7).forEach { d ->
                    Box(Modifier.width(14.dp).height(CELL), contentAlignment = Alignment.Center) {
                        Text(
                            d.date.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()),
                            style = TRN_STAT_LABEL, color = Ridge.inkSubtle,
                        )
                    }
                }
            }
            for (w in 0 until CONSISTENCY_WEEKS) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    for (dow in 0 until 7) {
                        val d = c.cells[w * 7 + dow]
                        val label = when {
                            d.future -> null
                            d.band == null -> untrackedFmt.format(dayLabel(d.date))
                            else -> cellFmt.format(
                                dayLabel(d.date), intensity.getValue(d.band), whole(d.effort),
                                when (d.onTarget) { true -> sleptTarget; false -> sleptBelow; null -> sleptNone },
                            )
                        }
                        ConsistencyCell(d, label)
                    }
                }
            }
        }
        }
        Stats(
            listOf(
                Triple(stringResource(R.string.trends_ridge_con_trained), stringResource(R.string.trends_ridge_n_of, c.trained, c.elapsed), Ridge.effort),
                Triple(stringResource(R.string.trends_ridge_con_target), stringResource(R.string.trends_ridge_n_of, c.onTarget, c.trackedNights), Ridge.rest),
            ),
        )
        Legend(
            listOf(
                Swatch.Square(Ridge.inkSubtle, outlined = true) to intensity.getValue(EffortBand.REST),
                Swatch.Square(Ridge.dataConsistency) to intensity.getValue(EffortBand.MODERATE),
                Swatch.Square(Ridge.effort) to intensity.getValue(EffortBand.HIGH),
                Swatch.Dot(Ridge.ink) to stringResource(
                    // R9a V2-3: the legend names the rule the moons follow.
                    if (com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.SLEPT_ON_TARGET)) R.string.trends_ridge_con_target_legend_need
                    else R.string.trends_ridge_con_target_legend,
                ),
            ),
        )
    }
}

/** One day: three intensities (B2-2b-5) — rest outline, light–moderate indigo, high blue — and a moon when slept
 *  on target; future days empty with no outline. */
@Composable
private fun ConsistencyCell(d: ConsistencyDay, label: String?) {
    val shape = RoundedCornerShape(4.dp)
    val fill = when (d.band) {
        EffortBand.MODERATE -> Ridge.dataConsistency
        EffortBand.HIGH, EffortBand.ALL_OUT -> Ridge.effort
        EffortBand.REST, null -> Ridge.glassFill
    }
    var m = Modifier.fillMaxWidth().height(CELL)
    if (!d.future) {
        m = m.background(fill, shape)
        if (d.band == null || d.band == EffortBand.REST) m = m.border(1.dp, Ridge.glassStroke, shape)
    }
    if (label != null) m = m.semantics { contentDescription = label }
    DayTipBox(d.date, !d.future, m) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            if (d.onTarget == true) {
                Icon(
                    RidgeIcons.moon, contentDescription = null, modifier = Modifier.size(12.dp),
                    tint = if (d.band == null || d.band == EffortBand.REST) Ridge.ink else Ridge.inkOnLiquid,
                )
            }
        }
    }
}


