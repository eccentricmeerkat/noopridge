package com.noop.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgeSheet
import com.noop.ui.ridge.chart.Axis
import com.noop.ui.ridge.chart.RidgePlot
import com.noop.ui.ridge.chart.ScrubTip
import com.noop.ui.ridge.chart.axisLabelSlots
import com.noop.ui.ridge.chart.ridgeBand
import com.noop.ui.ridge.chart.ridgeEndRing
import com.noop.ui.ridge.chart.ridgeLine

// B2 Hub2a: the Insights board's Explore cards (`Insights.dc.html`). They take plain values, so the Explore screen keeps its own
// metric model, and the cards stay free of it.

/** One row of the metric picker sheet. */
internal data class PickerEntry(
    val key: String, val title: String, val category: String, val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** BS-x2-3 item 16: the metric has no reading in the last 90 days; its row says so. */
    val noData: Boolean = false,
)

/** The board's Deep Timeline entry: a glass card with a heart-pink border, a heart-line icon and one line. */
@Composable
internal fun RidgeDeepEntry(title: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Ridge.radiusLg)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ridge.glassFill)
            .border(1.dp, Ridge.dataHeart.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("∿", style = RidgeType.titleSm, color = Ridge.dataHeart)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold), color = Ridge.ink)
            Text(stringResource(R.string.explore_ridge_deep_sub), style = TRN_LEGEND, color = Ridge.inkMuted)
        }
        Icon(RidgeIcons.chevronRight, contentDescription = null, tint = Ridge.inkSubtle, modifier = Modifier.size(18.dp))
    }
}

/** The metric picker: the category over the name, opening a bottom sheet of every metric grouped by category. */
@Composable
internal fun RidgeMetricPicker(entries: List<PickerEntry>, selected: PickerEntry, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Ridge.radiusMd)
    val pickLabel = stringResource(R.string.explore_pick_metric)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ridge.glassFillStrong)
            .border(1.dp, Ridge.brand.copy(alpha = 0.45f), shape)
            .clickable { open = true }
            .semantics { contentDescription = pickLabel; stateDescription = selected.title }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        selected.icon?.let { Icon(it, contentDescription = null, tint = selected.accent, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(selected.category.uppercase(), style = RidgeType.eyebrow, color = Ridge.brand)
            Text(selected.title, style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = Ridge.ink)
        }
        Icon(RidgeIcons.chevronDown, contentDescription = null, tint = Ridge.ink, modifier = Modifier.size(18.dp))
    }
    if (open) {
        RidgeSheet(onDismiss = { open = false }) {
            // RidgeSheet already scrolls; a second scroller under it measures with no height limit and crashes.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.explore_ridge_pick_title), style = RidgeType.titleSm, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                entries.groupBy { it.category }.forEach { (category, items) ->
                    Text(category.uppercase(), style = RidgeType.eyebrow, color = Ridge.brand, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    items.forEach { e ->
                        val on = e.key == selected.key
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Ridge.brand.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { onSelect(e.key); open = false }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (e.icon != null) Icon(e.icon, contentDescription = null, tint = e.accent, modifier = Modifier.size(20.dp))
                            else Box(Modifier.size(8.dp).clip(CircleShape).background(e.accent))
                            Text(e.title, style = RidgeType.body, color = if (on) Ridge.brand else Ridge.ink, modifier = Modifier.weight(1f))
                            // Right-aligned on the same line, small muted text like the card taglines. The row stays tappable.
                            if (e.noData) Text(stringResource(R.string.explore_ridge_no_data), style = TRN_LEGEND, color = Ridge.inkSubtle)
                        }
                    }
                }
            }
        }
    }
}

/** What the chart card needs, already formatted. */
internal class ExploreChartModel(
    val name: String,
    val description: String?,
    val accent: Color,
    val latest: String,
    val values: List<Double>,
    val dates: List<String>,
    val format: (Double) -> String,
    val normal: ClosedFloatingPointRange<Double>?,
    val stats: List<Pair<String, String>>,
    val a11y: String,
    /** BS-x2-4 item 20: draw daily bars that start at zero instead of a line (Sleep debt). */
    val bars: Boolean = false,
)

/** The board's chart card: the name in the serif, a normal-range line, the latest value, the chart, four stats. */
@Composable
internal fun RidgeExploreChartCard(m: ExploreChartModel) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m.name, style = RidgeType.titleSm, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                    m.description?.let { Text(it, style = TRN_SUB, color = Ridge.inkMuted) }
                    val last = m.values.lastOrNull()
                    if (m.normal != null && last != null) {
                        val inside = last in m.normal
                        Text(
                            stringResource(if (inside) R.string.explore_ridge_inside else R.string.explore_ridge_outside),
                            style = TRN_SUB.copy(fontWeight = FontWeight.SemiBold),
                            color = if (inside) Ridge.brand else Ridge.stateLow,
                        )
                    }
                }
                Text(m.latest, style = RidgeType.titleSm, color = m.accent)
            }
            if (m.values.size >= 2) {
                val n = m.values.size
                val lo0 = minOf(m.values.min(), m.normal?.start ?: Double.MAX_VALUE)
                val hi0 = maxOf(m.values.max(), m.normal?.endInclusive ?: -Double.MAX_VALUE)
                val pad = ((hi0 - lo0) * 0.10).takeIf { it > 0.0 } ?: 1.0
                val (axLo, axHi) = if (m.bars) exploreBarBounds(m.values, m.normal) else (lo0 - pad) to (hi0 + pad)
                val axis = Axis(axLo, axHi, listOf(axLo, (axLo + axHi) / 2, axHi))
                val slots = axisLabelSlots(n).map { it to m.dates.getOrElse(it) { "" } }
                RidgePlot(
                    n = n, axis = axis, height = 150.dp, yLabel = { "" }, xLabels = slots, bars = m.bars,
                    contentDescription = m.a11y, accent = m.accent, yLabels = false, gridlines = false, baseline = true,
                    scrubY = { i -> m.values.getOrNull(i) },
                    tip = { i -> m.values.getOrNull(i)?.let { ScrubTip(m.format(it), m.dates.getOrElse(i) { "" }) } },
                ) { g ->
                    m.normal?.let { ridgeBand(g, it.start, it.endInclusive, Ridge.ink.copy(alpha = 0.07f)) }
                    if (m.bars) {
                        // Daily bars from zero, in the metric's own colour; the latest day is full strength, the rest a little softer.
                        val bw = g.barWidth
                        m.values.forEachIndexed { i, v ->
                            val top = g.y(v)
                            drawRoundRect(
                                m.accent.copy(alpha = if (i == n - 1) 1f else 0.82f), Offset(g.x(i) - bw / 2, top),
                                androidx.compose.ui.geometry.Size(bw, (g.y(0.0) - top).coerceAtLeast(1f)),
                                androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                            )
                        }
                        return@RidgePlot
                    }
                    val area = Path().apply {
                        moveTo(g.x(0), g.bottom)
                        m.values.forEachIndexed { i, v -> lineTo(g.x(i), g.y(v)) }
                        lineTo(g.x(n - 1), g.bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(m.accent.copy(alpha = 0.18f), m.accent.copy(alpha = 0f)), startY = g.top, endY = g.bottom))
                    ridgeLine(g, m.values, m.accent, width = 2.25.dp)
                    ridgeEndRing(g, n - 1, m.values.last(), m.accent)
                }
            } else {
                Text(stringResource(R.string.explore_ridge_none), style = TRN_SUB, color = Ridge.inkSubtle, modifier = Modifier.padding(vertical = 28.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                m.stats.forEach { (k, v) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(k.uppercase(), style = RidgeType.eyebrow.copy(fontSize = 9.sp), color = Ridge.inkSubtle, maxLines = 1)
                        Text(v, style = RidgeType.titleSm.copy(fontSize = 17.sp, lineHeight = 20.sp), color = Ridge.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** "Over the visible window / Δ vs previous month" with the change at the right, green when it moved the good way. */
@Composable
internal fun RidgeExploreDeltaCard(window: String, delta: String, good: Boolean?) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RidgeEyebrow(stringResource(R.string.explore_ridge_delta_eyebrow))
                Text(stringResource(R.string.explore_ridge_delta_title, window), style = RidgeType.body.copy(fontSize = 14.sp), color = Ridge.ink)
            }
            Text(
                delta, style = RidgeType.titleSm.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = when (good) { true -> Ridge.brand; false -> Ridge.stateLow; null -> Ridge.inkMuted },
            )
        }
    }
}

/** A plain titled paragraph card ("What it means"). */
@Composable
internal fun RidgeAboutCard(eyebrow: String, body: String) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RidgeEyebrow(eyebrow)
            Text(body, style = RidgeType.bodySm.copy(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted)
        }
    }
}

/** The text of the "What it means" card for an Explore metric key; null when the metric has none. */
internal fun exploreAboutRes(key: String): Int? = when (key) {
    "recovery" -> R.string.explore_ridge_about_recovery
    "strain" -> R.string.explore_ridge_about_strain
    "hrv" -> R.string.explore_ridge_about_hrv
    "rhr" -> R.string.explore_ridge_about_rhr
    "sleep" -> R.string.explore_ridge_about_sleep
    "efficiency" -> R.string.explore_ridge_about_efficiency
    "spo2" -> R.string.explore_ridge_about_spo2
    "resp" -> R.string.explore_ridge_about_resp
    // BS-x2-4 item 21.
    "active_kcal" -> R.string.explore_ridge_about_calories
    "workout_kcal" -> R.string.explore_ridge_about_workout_kcal
    "hc_active_kcal" -> R.string.explore_ridge_about_hc_active
    "acwr" -> R.string.explore_ridge_about_acwr
    "ctl", "atl" -> R.string.explore_ridge_about_load
    "form" -> R.string.explore_ridge_about_form
    "sdnn" -> R.string.explore_ridge_about_sdnn
    "skin" -> R.string.explore_ridge_about_skin
    else -> null
}

/** What the Summary block needs, already formatted. [value] strings carry their unit ("42 %"), split here as the board does. */
internal class ExploreSummaryModel(
    val points: String,
    val average: String,
    val averageCaption: String,
    val min: String,
    val max: String,
    val delta: String,
    val deltaPill: String?,
    val deltaGood: Boolean?,
    val deltaCaption: String,
    val latest: String,
    val latestCaption: String?,
    val accent: Color,
)

private fun splitUnit(v: String): Pair<String, String> {
    val i = v.indexOf(' ')
    return if (i < 0) v to "" else v.substring(0, i) to v.substring(i + 1)
}

/** The board's Summary block: a wide Average card, then Min, Max, Δ vs prev and Latest, numerals in the serif. */
@Composable
internal fun RidgeExploreSummary(m: ExploreSummaryModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RidgeEyebrow(stringResource(R.string.explore_over_visible_window))
                Text(stringResource(R.string.explore_summary), style = RidgeType.titleLg, color = Ridge.ink, modifier = Modifier.semantics { heading() })
            }
            Text(m.points, style = RidgeType.bodySm.copy(fontWeight = FontWeight.Medium), color = Ridge.inkMuted)
        }
        SummaryTile(Modifier.fillMaxWidth(), stringResource(R.string.explore_average), m.average, m.accent, 44, m.averageCaption, tint = true)
        Row(horizontalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            SummaryTile(Modifier.weight(1f), stringResource(R.string.explore_min), m.min, Ridge.ink, 36)
            SummaryTile(Modifier.weight(1f), stringResource(R.string.explore_max), m.max, Ridge.ink, 36)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            SummaryTile(
                Modifier.weight(1f), stringResource(R.string.explore_delta_vs_prev), m.delta,
                when (m.deltaGood) { true -> Ridge.brand; false -> Ridge.stateLow; null -> Ridge.ink }, 36, m.deltaCaption, pill = m.deltaPill, pillGood = m.deltaGood,
            )
            SummaryTile(Modifier.weight(1f), stringResource(R.string.explore_latest), m.latest, m.accent, 36, m.latestCaption, tint = true)
        }
    }
}

@Composable
private fun SummaryTile(
    modifier: Modifier, label: String, value: String, color: Color, size: Int,
    caption: String? = null, tint: Boolean = false, pill: String? = null, pillGood: Boolean? = null,
) {
    val (num, unit) = splitUnit(value)
    val shape = RoundedCornerShape(Ridge.radiusLg)
    Column(
        modifier
            .clip(shape)
            .background(Ridge.glassFill)
            .then(if (tint) Modifier.background(Brush.linearGradient(listOf(Ridge.brand.copy(alpha = 0.10f), Color.Transparent))) else Modifier)
            .border(BorderStroke(1.dp, Ridge.glassStroke), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RidgeEyebrow(label)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(num, style = RidgeType.numeralMd.copy(fontSize = size.sp, lineHeight = (size + 4).sp), color = color, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, style = RidgeType.bodySm.copy(fontSize = if (size >= 44) 18.sp else 15.sp, fontWeight = FontWeight.Medium), color = Ridge.inkMuted, modifier = Modifier.padding(bottom = 4.dp))
            if (pill != null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val c = if (pillGood == true) Ridge.brand else Ridge.stateLow
                    Text(
                        pill, style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = c, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(Ridge.radiusPill)).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        caption?.let { Text(it, style = TRN_SUB, color = Ridge.inkMuted, maxLines = 1) }
    }
}

/**
 * The value-axis bounds of a bar chart: zero at the bottom (bars start at zero), a tenth of headroom above the tallest
 * bar or the top of the normal band. Never collapses to nothing when every value is zero.
 */
internal fun exploreBarBounds(values: List<Double>, normal: ClosedFloatingPointRange<Double>?): Pair<Double, Double> {
    val top = maxOf(values.maxOrNull() ?: 0.0, normal?.endInclusive ?: 0.0, 0.0)
    return 0.0 to (if (top <= 0.0) 1.0 else top * 1.1)
}

/**
 * The window's points with a 0 for every calendar day that has none, from the first point's day up to [through]: the bars of a
 * metric that is only recorded on some days (Active calories) sit on their real days with empty slots between them.
 */
internal fun fillEmptyDays(points: List<SeriesPoint>, through: java.time.LocalDate): List<SeriesPoint> {
    if (points.isEmpty()) return points
    val byDay = points.associateBy { it.day }
    val first = runCatching { java.time.LocalDate.parse(points.minOf { it.day }) }.getOrNull() ?: return points
    val last = maxOf(through, runCatching { java.time.LocalDate.parse(points.maxOf { it.day }) }.getOrDefault(through))
    val out = ArrayList<SeriesPoint>()
    var d = first
    while (!d.isAfter(last)) {
        val key = d.toString()
        out.add(byDay[key] ?: SeriesPoint(key, 0.0))
        d = d.plusDays(1)
    }
    return out
}
