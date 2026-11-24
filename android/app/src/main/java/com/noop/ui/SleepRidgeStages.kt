package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.data.DailyMetric
import com.noop.ui.ridge.GlassCard
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Ridge Sleep stages (B2-4a-5, `Sleep.dc.html` "Sleep stages")
//
// The board's hypnogram card: four lanes (Awake, REM, Light, Deep) of rounded stage blocks at their real times, a
// proportional strip, and four tiles with each stage's time, share and difference from the usual night. Every figure
// comes from the night's own stage minutes and timestamped segments; the "usual" is the mean of the nights before it.

/** Nights the "vs 14-night avg" line averages (the board's wording), taken from the nights before the one shown. */
internal const val STAGE_AVG_NIGHTS = 14

/** Fewest earlier nights an average needs before a difference is shown. */
internal const val STAGE_AVG_MIN_NIGHTS = 3

/** Display smoothing for the lanes and the wake count: a stretch shorter than this is absorbed, as the Ribbon does. */
private const val RIDGE_HYPNOGRAM_SMOOTH_SEC = 300.0

/** A wake counts once it lasts this long after you first fell asleep (the same 5 minutes the lanes smooth to). */
private const val WAKE_MIN_SEC = 300.0

/** The "Same as avg" band, as the board has it: under 4 minutes either way is the same night. */
internal const val STAGE_SAME_BAND_MIN = 4

/** The usual night's minutes per stage (null when there are too few earlier nights, or no figure for a stage). */
internal data class StageAverages(val deep: Double?, val rem: Double?, val light: Double?, val awake: Double?)

/**
 * The awake minutes of a stored night, the way [buildSleepModel] derives them: time-in-bed implied by efficiency
 * (asleep / efficiency − asleep), else six minutes per recorded disturbance, else none.
 */
internal fun awakeMinOf(d: DailyMetric): Double? {
    val asleep = d.totalSleepMin ?: ((d.deepMin ?: 0.0) + (d.remMin ?: 0.0) + (d.lightMin ?: 0.0)).takeIf { it > 0.0 } ?: return null
    val eff = d.efficiency?.let { if (it > 1.0) it / 100.0 else it }
    return when {
        eff != null && eff in 0.01..0.999 -> maxOf(0.0, asleep / eff - asleep)
        d.disturbances != null -> d.disturbances * 6.0
        else -> 0.0
    }
}

/**
 * The mean minutes per stage over the up-to-[nights] (default [STAGE_AVG_NIGHTS]) nights with stage data that come BEFORE [dayKey], or null
 * with fewer than [STAGE_AVG_MIN_NIGHTS] of them. The shown night is left out so it is not compared with itself.
 */
internal fun stageAveragesBefore(days: List<DailyMetric>, dayKey: String?, nights: Int = STAGE_AVG_NIGHTS): StageAverages? {
    val prior = days
        .filter { (dayKey == null || it.day < dayKey) && (it.deepMin ?: 0.0) + (it.remMin ?: 0.0) + (it.lightMin ?: 0.0) > 0.0 }
        .sortedBy { it.day }
        .takeLast(nights)
    if (prior.size < STAGE_AVG_MIN_NIGHTS) return null
    fun mean(xs: List<Double>) = xs.takeIf { it.isNotEmpty() }?.average()
    return StageAverages(
        deep = mean(prior.mapNotNull { it.deepMin }.filter { it > 0.0 }),
        rem = mean(prior.mapNotNull { it.remMin }.filter { it > 0.0 }),
        light = mean(prior.mapNotNull { it.lightMin }.filter { it > 0.0 }),
        awake = mean(prior.mapNotNull { awakeMinOf(it) }),
    )
}

/**
 * Wakes in the night: awake stretches of at least [WAKE_MIN_SEC] that begin after the first asleep stretch and end
 * before the last one. The final waking up is not a wake, and neither is time awake before falling asleep.
 */
internal fun wakeCount(intervals: List<StageInterval>): Int {
    val firstSleep = intervals.indexOfFirst { canonicalStage(it.stage) != "awake" }
    val lastSleep = intervals.indexOfLast { canonicalStage(it.stage) != "awake" }
    if (firstSleep < 0 || lastSleep <= firstSleep) return 0
    return intervals.subList(firstSleep + 1, lastSleep).count {
        canonicalStage(it.stage) == "awake" && it.endSec - it.startSec >= WAKE_MIN_SEC
    }
}

/** The signed whole-minute difference from the usual, or null when it is within the "same" band or unknown. */
internal fun stageDeltaMin(current: Double, usual: Double?): Int? {
    val d = usual?.let { (current - it).roundToInt() } ?: return null
    return d.takeIf { abs(it) >= STAGE_SAME_BAND_MIN }
}

/** A signed whole-minute difference with a true minus: "+4m", "\u22121m" (shared by the tiles and the breakdown). */
internal fun signedMinutes(deltaMin: Int): String = (if (deltaMin > 0) "+" else "\u2212") + "${abs(deltaMin)}m"

/** A stage's tile tone: more Deep or REM is good, more Awake is not, Light carries no judgement (a neutral ink). */
internal enum class DeltaTone { GOOD, WATCH, NEUTRAL }

internal fun deltaTone(stage: String, deltaMin: Int): DeltaTone = when (stage) {
    "deep", "rem" -> if (deltaMin > 0) DeltaTone.GOOD else DeltaTone.WATCH
    "awake" -> if (deltaMin < 0) DeltaTone.GOOD else DeltaTone.WATCH
    else -> DeltaTone.NEUTRAL
}

private fun clockOfTs(ts: Long, uses24Hour: Boolean): String =
    java.time.Instant.ofEpochSecond(ts).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern(com.noop.analytics.ClockFormat.hourMinutePattern(uses24Hour), java.util.Locale.getDefault()))

private val LANES = listOf("awake", "rem", "light", "deep")

private fun stageColor(stage: String): Color = when (stage) {
    "deep" -> Ridge.sleepDeep
    "rem" -> Ridge.sleepRem
    "light" -> Ridge.sleepLight
    else -> Ridge.sleepAwake
}

/**
 * The board's "Sleep stages" card. [segments] are the night's timestamped stages; without them (an import with only
 * minutes) the lanes are left out, with a line saying so, and the strip and tiles still read from [stages].
 */
@Composable
internal fun RidgeSleepStagesCard(
    stages: Stages,
    segments: List<PersistedSegment>?,
    onsetTs: Long?,
    wakeTs: Long?,
    averages: StageAverages?,
    uses24Hour: Boolean,
) {
    val origin = onsetTs ?: segments?.minOfOrNull { it.start }
    val end = wakeTs ?: segments?.maxOfOrNull { it.end }
    val intervals = remember(segments, origin) {
        if (segments == null || origin == null) emptyList()
        else displaySmoothed(
            segments.sortedBy { it.start }.map { StageInterval(it.stage, (it.start - origin).toDouble(), (it.end - origin).toDouble()) },
            RIDGE_HYPNOGRAM_SMOOTH_SEC,
        )
    }
    val spanSec = if (origin != null && end != null) (end - origin).toDouble().coerceAtLeast(1.0) else 1.0
    val wakes = wakeCount(intervals)
    val asleepText = durationText(stages.asleep)
    val a11y = stringResource(
        R.string.sleep_ridge_stages_a11y,
        durationText(stages.deep), durationText(stages.rem), durationText(stages.light), durationText(stages.awake),
    )
    GlassCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = a11y }, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                    Text(stringResource(R.string.sleep_ridge_stages_title), style = TRN_H2, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                    Text(
                        if (onsetTs != null && wakeTs != null) {
                            stringResource(R.string.sleep_ridge_stages_sub, clockOfTs(onsetTs, uses24Hour), clockOfTs(wakeTs, uses24Hour), asleepText)
                        } else stringResource(R.string.sleep_ridge_asleep, asleepText),
                        style = TRN_SUB, color = Ridge.inkMuted,
                    )
                }
                if (wakes > 0) {
                    Text(
                        if (wakes == 1) stringResource(R.string.sleep_ridge_stages_wake_one) else stringResource(R.string.sleep_ridge_stages_wakes, wakes),
                        style = TRN_LEGEND.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Ridge.sleepAwake, maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.Top)
                            .background(Ridge.sleepAwake.copy(alpha = 0.14f), RoundedCornerShape(Ridge.radiusPill))
                            .border(1.dp, Ridge.sleepAwake.copy(alpha = 0.4f), RoundedCornerShape(Ridge.radiusPill))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            if (intervals.isNotEmpty() && origin != null && end != null) {
                Lanes(intervals, spanSec, onsetTs ?: origin, wakeTs ?: end, uses24Hour)
            } else {
                Text(stringResource(R.string.sleep_ridge_stages_no_timeline), style = TRN_LEGEND, color = Ridge.inkSubtle)
            }
            // The proportional strip: Deep, REM, Light, Awake, as the board orders it.
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("deep" to stages.deep, "rem" to stages.rem, "light" to stages.light, "awake" to stages.awake).forEach { (s, m) ->
                    if (m > 0.0) Box(Modifier.weight(m.toFloat().coerceAtLeast(0.5f)).height(14.dp).background(stageColor(s)))
                }
            }
            // The board's 2x2: Deep and REM over Light and Awake.
            val tiles = listOf(
                Triple("deep", R.string.trends_ridge_stage_deep, stages.deep to averages?.deep),
                Triple("rem", R.string.trends_ridge_stage_rem, stages.rem to averages?.rem),
                Triple("light", R.string.trends_ridge_stage_light, stages.light to averages?.light),
                Triple("awake", R.string.sleep_ridge_stage_awake, stages.awake to averages?.awake),
            )
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (stage, label, p) ->
                        StageTile(Modifier.weight(1f).fillMaxHeight(), stage, stringResource(label), p.first, stages.total, p.second)
                    }
                }
            }
        }
    }
}

/**
 * The four lanes, the stage blocks at their times, and the hour axis under them (BS-x2-2 item 10).
 *
 * A night longer than four hours shows about 3.5 h at a time: drag sideways to pan. Press and hold, then drag, to scrub:
 * a tooltip above the chart gives the stage, its start to end and how long that run lasted (placed with the item 5
 * rule, above the finger). A tap shows the same tooltip at that point. A short night is shown whole and does not scroll.
 */
@Composable
private fun Lanes(intervals: List<StageInterval>, spanSec: Double, onsetTs: Long, wakeTs: Long, uses24Hour: Boolean) {
    val laneLabels = listOf(
        stringResource(R.string.sleep_ridge_stage_awake), stringResource(R.string.trends_ridge_stage_rem),
        stringResource(R.string.trends_ridge_stage_light), stringResource(R.string.trends_ridge_stage_deep),
    )
    var window by remember(spanSec) { mutableStateOf(SleepStageWindows.default(spanSec)) }
    var probeX by remember { mutableStateOf<Float?>(null) }
    var chartBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val scrollable = SleepStageWindows.scrollable(spanSec)
    val density = androidx.compose.ui.platform.LocalDensity.current
    Column(
        Modifier.fillMaxWidth().background(Ridge.bg000.copy(alpha = 0.45f), RoundedCornerShape(Ridge.radiusSm)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(44.dp).height(LANE_AREA_HEIGHT)) {
                laneLabels.forEach { Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) { Text(it, style = TRN_LEGEND.copy(fontSize = 10.sp), color = Ridge.inkSubtle, maxLines = 1) } }
            }
            Canvas(
                Modifier.weight(1f).height(LANE_AREA_HEIGHT)
                    .onGloballyPositioned { chartBounds = it.boundsInWindow() }
                    .pointerInput(spanSec) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w = size.width.toFloat()
                            val lp = awaitLongPressOrCancellation(down.id)
                            if (lp != null) {
                                // Scrub: the tooltip follows the finger until it lifts.
                                probeX = lp.position.x
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    probeX = ch.position.x
                                    ch.consume()
                                }
                                probeX = null
                            } else {
                                val cur = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (cur == null || !cur.pressed) {
                                    // A tap: show the tooltip here, or clear it if one is up.
                                    probeX = if (probeX == null) down.position.x else null
                                } else if (scrollable) {
                                    val dx0 = cur.position.x - down.position.x
                                    val dy0 = cur.position.y - down.position.y
                                    if (kotlin.math.abs(dx0) >= kotlin.math.abs(dy0)) {
                                        probeX = null
                                        horizontalDrag(down.id) { ch ->
                                            val dxPx = ch.positionChange().x
                                            window = SleepStageWindows.pan(window, (-dxPx / w * window.spanSec).toDouble(), spanSec)
                                            ch.consume()
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                val laneH = size.height / 4f
                for (r in 0 until 4) {
                    val y = laneH * (r + 0.5f)
                    drawLine(RidgeChart.grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val blockH = laneH * 0.66f
                intervals.forEach { iv ->
                    if (iv.endSec < window.fromSec || iv.startSec > window.toSec) return@forEach
                    val rank = LANES.indexOf(canonicalStage(iv.stage)).let { if (it < 0) 2 else it }
                    val x0 = SleepStageWindows.xOfSec(iv.startSec, size.width, window).coerceIn(0f, size.width)
                    val x1 = SleepStageWindows.xOfSec(iv.endSec, size.width, window).coerceIn(0f, size.width)
                    val w = (x1 - x0).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(
                        stageColor(canonicalStage(iv.stage)), Offset(x0, laneH * rank + (laneH - blockH) / 2),
                        Size(w.coerceAtMost(size.width - x0), blockH), CornerRadius(4.dp.toPx()),
                    )
                }
                probeX?.let { px ->
                    val x = px.coerceIn(0f, size.width)
                    drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                }
            }
        }
        val marks = remember(onsetTs, window) { SleepStageWindows.hourMarks(onsetTs, window) }
        // The axis shares the chart's left inset; a mark sits at its true time, centred on its hour.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 44.dp).height(14.dp)) {
            val widthDp = maxWidth
            marks.forEach { (frac, ts) ->
                val label = clockOfTs(ts, uses24Hour)
                Text(
                    label, style = TRN_LEGEND.copy(fontSize = 10.sp), color = Ridge.inkSubtle, maxLines = 1, softWrap = false,
                    modifier = Modifier.offset(x = (widthDp * frac - 16.dp).coerceIn(0.dp, (widthDp - 32.dp).coerceAtLeast(0.dp))),
                )
            }
        }
    }
    val bounds = chartBounds
    val px = probeX
    if (bounds != null && px != null) {
        val tip = SleepStageWindows.tipAt(intervals, SleepStageWindows.secAtX(px, bounds.width, window))
        val gapPx = with(density) { 8.dp.roundToPx() }
        val marginPx = with(density) { 8.dp.roundToPx() }
        val topLimitPx = WindowInsets.statusBars.getTop(density)
        val anchor = androidx.compose.ui.unit.IntRect((bounds.left + px).toInt(), bounds.top.toInt(), (bounds.left + px).toInt(), bounds.bottom.toInt())
        androidx.compose.ui.window.Popup(
            popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize,
                ): androidx.compose.ui.unit.IntOffset = DayTipPlacement.place(anchor, popupContentSize, windowSize, gapPx, marginPx, topLimitPx)
            },
        ) {
            val atTs = onsetTs + SleepStageWindows.secAtX(px, bounds.width, window).toLong()
            Column(
                Modifier
                    .background(Ridge.bg000, RoundedCornerShape(Ridge.radiusSm))
                    .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusSm))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (tip != null) {
                    val name = laneLabels[LANES.indexOf(tip.stage).let { if (it < 0) 2 else it }]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(stageColor(tip.stage), CircleShape))
                        Text(name, style = TRN_LEGEND.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Ridge.ink)
                    }
                    Text(
                        "${clockOfTs(onsetTs + tip.startSec.toLong(), uses24Hour)} – ${clockOfTs(onsetTs + tip.endSec.toLong(), uses24Hour)} · ${durationText(tip.durationSec / 60.0)}",
                        style = TRN_LEGEND, color = Ridge.ink,
                    )
                } else {
                    Text(clockOfTs(atTs, uses24Hour), style = TRN_LEGEND, color = Ridge.ink)
                }
            }
        }
    }
}

private val LANE_AREA_HEIGHT = 132.dp

/** One stage's tile: its dot and name, the time in serif, the share of time in bed, and the difference from the usual. */
@Composable
private fun StageTile(modifier: Modifier, stage: String, label: String, minutes: Double, totalMin: Double, usual: Double?) {
    val delta = stageDeltaMin(minutes, usual)
    Column(
        modifier
            .background(Ridge.glassFill, RoundedCornerShape(Ridge.radiusSm))
            .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusSm))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(7.dp).background(stageColor(stage), CircleShape))
            Text(label.uppercase(), style = TRN_STAT_LABEL.copy(fontSize = 9.sp, letterSpacing = 0.6.sp), color = Ridge.inkSubtle, maxLines = 1, softWrap = false)
        }
        Text(
            durationText(minutes), style = RidgeType.numeralMd.copy(fontSize = 16.sp, lineHeight = 20.sp),
            color = Ridge.ink, maxLines = 1, softWrap = false,
        )
        Text(
            if (totalMin > 0.0) stringResource(R.string.sleep_ridge_stage_percent, (minutes / totalMin * 100.0).roundToInt()) else stringResource(R.string.trends_ridge_not_tracked),
            style = TRN_LEGEND, color = Ridge.inkMuted,
        )
        if (usual != null) {
            val text = if (delta == null) stringResource(R.string.sleep_ridge_stages_same)
            else stringResource(R.string.sleep_ridge_stages_delta, signedMinutes(delta))
            val tone = delta?.let { deltaTone(stage, it) } ?: DeltaTone.NEUTRAL
            Text(
                text, style = TRN_LEGEND.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = when (tone) { DeltaTone.GOOD -> Ridge.brand; DeltaTone.WATCH -> Ridge.stateLow; DeltaTone.NEUTRAL -> Ridge.inkSubtle },
            )
        }
    }
}

// MARK: - Stage breakdown (B2-4b-1, `Sleep.dc.html` "Stage breakdown")

/** The 30-night window the board's "30 day average" side compares against. */
internal const val STAGE_COMPARE_NIGHTS = 30

/** A stage's typical range as a share of time in bed, in percent (owner D14: the board's values, one set for everyone). */
internal data class TypicalRange(val lo: Int, val hi: Int)

/**
 * The board's typical ranges (Awake 4 to 12%, REM 18 to 26%, Light 38 to 50%, Deep 10 to 18% of time in bed), shown as
 * "Typical range". A general adult guide: they shift with age and are measured against lab polysomnography, while the
 * strap estimates stages (see B2-4a_DECISIONS.md D14). Not a per-age or per-sex table.
 */
internal fun typicalRange(stage: String): TypicalRange = when (stage) {
    "awake" -> TypicalRange(4, 12)
    "rem" -> TypicalRange(18, 26)
    "light" -> TypicalRange(38, 50)
    else -> TypicalRange(10, 18) // deep
}

internal enum class RangeStatus { BELOW, TYPICAL, ABOVE }

internal fun rangeStatus(stage: String, sharePct: Int): RangeStatus {
    val r = typicalRange(stage)
    return when {
        sharePct < r.lo -> RangeStatus.BELOW
        sharePct > r.hi -> RangeStatus.ABOVE
        else -> RangeStatus.TYPICAL
    }
}

/** The board's tone for a status chip: more Awake is not good; Light is good only when typical; Deep and REM only fall short when low. */
internal fun rangeStatusIsGood(stage: String, status: RangeStatus): Boolean = when (stage) {
    "awake" -> status != RangeStatus.ABOVE
    "light" -> status == RangeStatus.TYPICAL
    else -> status != RangeStatus.BELOW
}

/** What the bars' marker compares against: the typical range (a ghost span) or the previous 30 nights (a dot). */
internal enum class StageCompare { RANGE, AVERAGE }

/** A stage's share of the 30-night mean night (its mean minutes over the mean time in bed), in percent; null without all four means. */
internal fun averageSharePct(stage: String, a: StageAverages?): Double? {
    val awake = a?.awake ?: return null; val rem = a.rem ?: return null; val light = a.light ?: return null; val deep = a.deep ?: return null
    val total = awake + rem + light + deep
    if (total <= 0.0) return null
    val v = when (stage) { "awake" -> awake; "rem" -> rem; "light" -> light; else -> deep }
    return v / total * 100.0
}

/** The bars' full width, in percent of time in bed (the board's scale). */
private const val BREAKDOWN_SCALE_PCT = 70.0

/**
 * The board's "Stage breakdown" card: four bars (Total awake, REM, Light, Deep), each "name • share • time" with its
 * share drawn against either the typical range (a dashed span, with a status chip) or the 30-night average (a dot, with
 * the difference), switched by the toggle at the foot.
 */
@Composable
internal fun RidgeStageBreakdownCard(stages: Stages, efficiencyText: String, average30: StageAverages?) {
    var compare by rememberSaveable { mutableStateOf(StageCompare.RANGE) }
    val total = stages.total
    val rows = listOf(
        Triple("awake", stringResource(R.string.sleep_ridge_stagebreak_total_awake), stages.awake),
        Triple("rem", stringResource(R.string.trends_ridge_stage_rem), stages.rem),
        Triple("light", stringResource(R.string.trends_ridge_stage_light), stages.light),
        Triple("deep", stringResource(R.string.trends_ridge_stage_deep), stages.deep),
    )
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space4)) {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                Text(stringResource(R.string.sleep_ridge_stagebreak_title), style = TRN_H2, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.sleep_ridge_stagebreak_lead, durationText(total), efficiencyText), style = TRN_SUB, color = Ridge.inkMuted)
            }
            rows.forEach { (stage, name, minutes) ->
                val pct = if (total > 0.0) (minutes / total * 100.0).roundToInt() else 0
                val avgPct = averageSharePct(stage, average30)
                val avgMin = when (stage) { "awake" -> average30?.awake; "rem" -> average30?.rem; "light" -> average30?.light; else -> average30?.deep }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.sleep_ridge_stagebreak_row, name, pct, durationText(minutes)),
                            style = TRN_SUB.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Ridge.ink, modifier = Modifier.weight(1f),
                        )
                        if (compare == StageCompare.RANGE) {
                            val status = rangeStatus(stage, pct)
                            val good = rangeStatusIsGood(stage, status)
                            val tone = if (good) Ridge.brand else Ridge.stateLow
                            Text(
                                stringResource(when (status) {
                                    RangeStatus.TYPICAL -> R.string.sleep_ridge_stagebreak_typical
                                    RangeStatus.ABOVE -> R.string.sleep_ridge_stagebreak_above
                                    RangeStatus.BELOW -> R.string.sleep_ridge_stagebreak_below
                                }),
                                style = TRN_LEGEND.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = tone, maxLines = 1,
                                modifier = Modifier
                                    .background(tone.copy(alpha = 0.14f), RoundedCornerShape(Ridge.radiusPill))
                                    .border(1.dp, tone.copy(alpha = 0.4f), RoundedCornerShape(Ridge.radiusPill))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        } else if (avgMin != null) {
                            val d = stageDeltaMin(minutes, avgMin)
                            Text(
                                if (d == null) stringResource(R.string.sleep_ridge_stages_same)
                                else stringResource(R.string.sleep_ridge_stagebreak_delta, signedMinutes(d)),
                                style = TRN_LEGEND, color = Ridge.inkSubtle, maxLines = 1,
                            )
                        }
                    }
                    BreakdownBar(stage, pct.toDouble(), if (compare == StageCompare.RANGE) typicalRange(stage) else null, if (compare == StageCompare.AVERAGE) avgPct else null)
                }
            }
            // The key for the marker, centred, then the compare toggle.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (compare == StageCompare.RANGE) {
                    Box(Modifier.size(width = 16.dp, height = 10.dp).border(1.dp, Ridge.ink.copy(alpha = 0.7f), RoundedCornerShape(5.dp)).background(Ridge.ink.copy(alpha = 0.14f), RoundedCornerShape(5.dp)))
                } else {
                    Box(Modifier.width(2.5.dp).height(12.dp).background(Ridge.ink.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
                }
                Text(
                    stringResource(if (compare == StageCompare.RANGE) R.string.sleep_ridge_stagebreak_legend_range else R.string.sleep_ridge_stagebreak_legend_avg),
                    style = TRN_LEGEND, color = Ridge.inkMuted, modifier = Modifier.padding(start = 8.dp),
                )
            }
            val toggleLabels = mapOf(
                StageCompare.RANGE to stringResource(R.string.sleep_ridge_stagebreak_toggle_range),
                StageCompare.AVERAGE to stringResource(R.string.sleep_ridge_stagebreak_toggle_avg),
            )
            com.noop.ui.ridge.RidgeSegmentedPills(
                items = StageCompare.entries.toList(),
                selected = compare,
                onSelect = { compare = it },
                label = { toggleLabels.getValue(it) },
                fill = true,
            )
            Text(stringResource(R.string.sleep_ridge_stagebreak_footnote), style = TRN_LEGEND, color = Ridge.inkSubtle)
        }
    }
}

/** One bar: the track, the typical-range ghost or the 30-night marker, and the stage's share drawn over them. */
@Composable
private fun BreakdownBar(stage: String, sharePct: Double, range: TypicalRange?, avgPct: Double?) {
    val color = stageColor(stage)
    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        val w = size.width
        val trackTop = 4.dp.toPx(); val trackH = 22.dp.toPx()
        drawRoundRect(Ridge.glassFill, Offset(0f, trackTop), Size(w, trackH), CornerRadius(trackH / 2))
        range?.let {
            val x0 = (w * (it.lo / BREAKDOWN_SCALE_PCT)).toFloat(); val x1 = (w * (it.hi / BREAKDOWN_SCALE_PCT)).toFloat().coerceAtMost(w)
            drawRoundRect(Ridge.ink.copy(alpha = 0.14f), Offset(x0, 0f), Size(x1 - x0, size.height), CornerRadius(15.dp.toPx()))
            drawRoundRect(
                Ridge.ink.copy(alpha = 0.7f), Offset(x0, 0f), Size(x1 - x0, size.height), CornerRadius(15.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
            )
        }
        val fillW = (w * (sharePct / BREAKDOWN_SCALE_PCT)).toFloat().coerceIn(w * 0.08f, w)
        drawRoundRect(color.copy(alpha = 0.28f), Offset(-2.dp.toPx(), trackTop - 2.dp.toPx()), Size(fillW + 4.dp.toPx(), trackH + 4.dp.toPx()), CornerRadius(trackH / 2 + 2.dp.toPx())) // the glow
        drawRoundRect(color, Offset(0f, trackTop), Size(fillW, trackH), CornerRadius(trackH / 2))
        avgPct?.let {
            val c = Offset((w * (it / BREAKDOWN_SCALE_PCT)).toFloat().coerceIn(7.dp.toPx(), w - 7.dp.toPx()), size.height / 2)
            // BS-x2-2: a thin soft tick, not a black disc with a white ring (owner: they stood out too much).
            val tickW = 2.5.dp.toPx(); val tickH = trackH + 6.dp.toPx()
            drawRoundRect(Ridge.ink.copy(alpha = 0.55f), Offset(c.x - tickW / 2, c.y - tickH / 2), Size(tickW, tickH), CornerRadius(tickW / 2))
        }
    }
}
