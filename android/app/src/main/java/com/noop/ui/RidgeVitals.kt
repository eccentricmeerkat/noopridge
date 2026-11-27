package com.noop.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.VitalBands
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeIcons

// B2 Hub3a (VitalSigns.dc.html): the Vital signs page, new under Ridge (v10's Health Monitor and Vital Signs were retired
// into Explore). The figures, the ranges and the skin-temperature setting are the ones the old vitals grid used.

/** The vitals the board shows, in its order, for the latest day with data; each already banded against the wearer's own normal. */
internal fun ridgeLatestVitals(context: Context, days: List<com.noop.data.DailyMetric>): List<Vital> {
    val latest = days.lastOrNull() ?: return emptyList()
    val all = vitalsFor(
        latest, days, UnitPrefs.temperature(context), emptyMap(), NoopPrefs.spo2CandidateDisplay(context), emptyMap(),
        UnitPrefs.skinTempPreferred(context),
    )
    val five = listOf("resp", "spo2", "rhr", "hrv", "skin").mapNotNull { k -> all.firstOrNull { it.key == k } }
    return withCarriedHrv(five, latest, days, context.getString(R.string.vital_ridge_hrv_from)) { row ->
        vitalsFor(
            row, days.filter { it.day <= row.day }, UnitPrefs.temperature(context), emptyMap(),
            NoopPrefs.spo2CandidateDisplay(context), emptyMap(), UnitPrefs.skinTempPreferred(context),
        ).firstOrNull { it.key == "hrv" }
    }
}

/** How far back Vital signs looks for an HRV reading when the latest night has none. */
internal const val HRV_CARRY_MAX_DAYS = 30L

/**
 * RS-3e: the latest night can be without HRV (not recorded, or withheld) while earlier nights have it, which left the tile
 * blank beside an HRV on Today. The newest earlier reading within [HRV_CARRY_MAX_DAYS] is shown instead, dated. Up to
 * [CARRY_FRESHNESS_DAYS] old it is banded as on its own night; older, it is shown but judged neither in nor out of range,
 * so Today's alert and Body Monitor never count an old night as today's.
 */
internal fun withCarriedHrv(
    vitals: List<Vital>,
    latest: com.noop.data.DailyMetric,
    days: List<com.noop.data.DailyMetric>,
    /** "From %1$s", dating the carried reading. */
    fromFormat: String,
    hrvOn: (com.noop.data.DailyMetric) -> Vital?,
): List<Vital> {
    if (latest.avgHrv != null) return vitals
    val row = lastHrvRow(days, latest.day) ?: return vitals
    val age = runCatching {
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(row.day), java.time.LocalDate.parse(latest.day))
    }.getOrNull() ?: return vitals
    if (age > HRV_CARRY_MAX_DAYS) return vitals
    val carried = hrvOn(row) ?: return vitals
    val date = java.time.LocalDate.parse(row.day)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
    return vitals.map {
        if (it.key != "hrv") it else carried.copy(
            secondary = String.format(java.util.Locale.getDefault(), fromFormat, date),
            deltaText = null,
            banding = if (age <= CARRY_FRESHNESS_DAYS) carried.banding else carried.banding.copy(band = VitalBands.Band.NO_DATA),
        )
    }
}

private fun vitalIcon(key: String): ImageVector = when (key) {
    "resp" -> RidgeIcons.wind
    "spo2" -> RidgeIcons.droplet
    "rhr" -> RidgeIcons.heart
    "hrv" -> RidgeIcons.pulse
    else -> RidgeIcons.thermometer
}

private fun vitalHue(key: String): Color = when (key) {
    "resp" -> Ridge.dataBreath
    "spo2" -> Ridge.effort
    "rhr" -> Ridge.dataHeart
    "hrv" -> Ridge.dataHrv
    else -> Ridge.dataEnergy
}

/**
 * RS-2a: Blood oxygen from the registry (the newest reading, the strap estimate included, however old) in place of the latest
 * day's calibrated column, which a strap never fills. The Vital signs page did this alone, so Body Monitor counted one vital
 * fewer than the page showed ("1 of 3" beside 2 of 4).
 */
internal fun withRegistrySpo2(vitals: List<Vital>, spo2: LatestSpo2?, estimateWord: String): List<Vital> = vitals.map { v ->
    if (v.key == "spo2" && v.value == null && spo2 != null) {
        val day = spo2.day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
        v.copy(
            value = spo2.value,
            banding = VitalBands.band(spo2.value, emptyList(), 95.0..100.0, null),
            secondary = (if (spo2.estimate) "$estimateWord · " else "") + day,
            isEstimate = spo2.estimate,
        )
    } else v
}

/** The five vitals as Vital signs shows them, for every surface that summarises them (the page, Body Monitor, Today's alert). */
@Composable
internal fun rememberRidgeVitals(vm: AppViewModel): List<Vital> {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    // Fix 5: Blood oxygen reads the registry like Trends and Insights (the strap estimate included, however old the latest night is),
    // not only today's calibrated column.
    var spo2 by remember { mutableStateOf<LatestSpo2?>(null) }
    LaunchedEffect(days) {
        spo2 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val inputs = registryInputsFor(vm)
                latestSpo2(com.noop.metrics.V10MetricRegistry(inputs), inputs.today)
            }.getOrNull()
        }
    }
    val estimateWord = stringResource(R.string.trends_ridge_sig_strap_estimate)
    return remember(days, spo2, estimateWord) { withRegistrySpo2(ridgeLatestVitals(context, days), spo2, estimateWord) }
}

/** Vital signs: the live heart rate, then one tile per reading with its in-range chip; each tile opens that metric. */
@Composable
internal fun RidgeVitalSignsScreen(vm: AppViewModel, onVitalClick: (String) -> Unit) {
    val vitals = rememberRidgeVitals(vm)
    ScreenScaffold(
        title = stringResource(R.string.vital_ridge_title),
        subtitle = stringResource(R.string.vital_ridge_sub),
    ) {
        RidgeLiveHeartCard(vm)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            vitals.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { v -> RidgeVitalTile(v, Modifier.weight(1f)) { onVitalClick(v.key) } }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        com.noop.ui.RidgeAboutCard(stringResource(R.string.vital_ridge_about), stringResource(R.string.vital_ridge_about_body))
    }
}

@Composable
private fun RidgeVitalTile(v: Vital, modifier: Modifier, onClick: () -> Unit) {
    val band = v.banding.band
    val chipColor = when (band) {
        VitalBands.Band.IN_RANGE -> Ridge.brand
        VitalBands.Band.OUT_OF_RANGE -> Ridge.stateModerate
        VitalBands.Band.NO_DATA -> Ridge.inkSubtle
    }
    val status = stringResource(
        when (band) {
            VitalBands.Band.IN_RANGE -> R.string.vital_ridge_in_range
            VitalBands.Band.OUT_OF_RANGE -> R.string.vital_ridge_out_of_range
            // RS-3e: an older carried HRV has a value but no verdict.
            VitalBands.Band.NO_DATA -> if (v.value != null) R.string.vital_ridge_older else R.string.vital_ridge_no_reading
        },
    )
    val shape = RoundedCornerShape(Ridge.radiusLg)
    val value = v.value?.let { v.format(it) } ?: "—"
    val a11y = stringResource(R.string.vital_ridge_a11y, v.label, value, v.unit, status)
    Column(
        modifier
            .clip(shape)
            .background(Ridge.glassFill)
            .border(1.dp, Ridge.glassStroke, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = a11y }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(vitalIcon(v.key), contentDescription = null, tint = vitalHue(v.key), modifier = Modifier.size(18.dp))
            Text(v.label, style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = Ridge.inkMuted, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = RidgeType.titleSm.copy(fontSize = 34.sp, lineHeight = 38.sp), color = if (v.value == null) Ridge.inkSubtle else Ridge.ink, maxLines = 1)
            if (v.value != null) Text(v.unit, style = TRN_LEGEND.copy(fontSize = 13.sp), color = Ridge.inkMuted, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
        }
        (v.secondary ?: v.deltaText?.trim('(', ')')?.let { stringResource(R.string.vital_ridge_vs_last, it) })?.let {
            Text(it, style = TRN_LEGEND, color = Ridge.inkMuted, maxLines = 2)
        }
        Text(
            status, style = TRN_LEGEND.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = chipColor, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(Ridge.radiusPill)).background(chipColor.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The live heart rate: Live, Waiting or Not connected, the bpm in the serif, a heartbeat trace, and where it comes from. */
@Composable
private fun RidgeLiveHeartCard(vm: AppViewModel) {
    val live by vm.live.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    // RS-1-1 item 4: three states (Live, Waiting, Not connected). A stale bpm after the link drops is not shown.
    val state = heartCardState(live.connected, displayHr(bpm, live))
    val isLive = state == HeartCardState.LIVE
    val hr = if (isLive) displayHr(bpm, live) else null
    // Final review: opening Vital signs asks the strap to stream, as Live and Body monitor do. The Ridge rebuild (Hub3a)
    // dropped this request, so the card only moved when another screen happened to be streaming. Ref-counted in the
    // ViewModel, so handing off between Live and here never drops the stream.
    DisposableEffect(vm) {
        vm.requestRealtimeHr()
        onDispose { vm.releaseRealtimeHr() }
    }
    var strap by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.activeStrapId) {
        strap = runCatching { vm.pairedDevices() }.getOrDefault(emptyList()).firstOrNull { it.id == vm.activeStrapId }?.let { displayName(it) }
    }
    RidgeHeartCardContent(state, hr, strap, animate = !rememberPoseStill())
}

/**
 * The heart card's face for one [state] (stateless, so each state has a snapshot). [animate] false draws the Live trace
 * once and still (Reduce motion, battery saver, Quiet motion, and snapshots).
 */
@Composable
internal fun RidgeHeartCardContent(state: HeartCardState, hr: Int?, strap: String?, animate: Boolean) {
    val isLive = state == HeartCardState.LIVE
    val heart = if (isLive) Ridge.dataHeart else Ridge.inkSubtle
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RidgeEyebrow(stringResource(R.string.vital_ridge_heart))
                Box(Modifier.weight(1f))
                val pill = when (state) {
                    HeartCardState.LIVE -> Ridge.brand
                    HeartCardState.WAITING -> Ridge.effort
                    HeartCardState.NOT_CONNECTED -> Ridge.inkSubtle
                }
                Text(
                    stringResource(
                        when (state) {
                            HeartCardState.LIVE -> R.string.vital_ridge_live
                            HeartCardState.WAITING -> R.string.vital_ridge_waiting
                            HeartCardState.NOT_CONNECTED -> R.string.vital_ridge_not_connected
                        },
                    ),
                    style = TRN_LEGEND.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = pill,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Ridge.radiusPill))
                        .border(1.dp, pill.copy(alpha = 0.4f), RoundedCornerShape(Ridge.radiusPill))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(RidgeIcons.heart, contentDescription = null, tint = heart, modifier = Modifier.size(40.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(hr?.toString() ?: "--", style = RidgeType.numeralLg.copy(fontSize = 64.sp, lineHeight = 66.sp), color = Ridge.ink)
                    Text(stringResource(R.string.vital_ridge_bpm), style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = Ridge.inkMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            // RS-1-1 item 4: a drawn heartbeat on a faint grid, sliding right to left one beat every 2 s (not the readings;
            // the numeral above is the reading). Waiting or Not connected: a flat grey line that does not move. Reduce
            // motion, battery saver or Quiet motion: the Live trace is drawn once and stays still.
            val phase = if (isLive && animate) {
                rememberInfiniteTransition().animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(HEART_TRACE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
                )
            } else null
            val path = remember { Path() }
            val grid = Ridge.glassStroke.copy(alpha = 0.10f)
            Canvas(Modifier.fillMaxWidth().height(64.dp).clipToBounds()) {
                // The faint square grid behind the line (reference).
                val cell = 16.dp.toPx()
                var gx = 0f
                while (gx <= size.width) { drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), 1f); gx += cell }
                var gy = size.height / 2 % cell
                while (gy <= size.height) { drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f); gy += cell }
                val mid = size.height / 2
                if (!isLive) {
                    drawLine(Ridge.inkSubtle.copy(alpha = 0.6f), Offset(0f, mid), Offset(size.width, mid), 2.dp.toPx(), cap = StrokeCap.Round)
                    return@Canvas
                }
                path.reset()
                heartTrace(phase?.value ?: 0f).forEachIndexed { i, p ->
                    val x = size.width * p.x
                    val y = size.height * p.y
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                // A soft glow under the line, then the line itself.
                drawPath(path, Ridge.dataHeart.copy(alpha = 0.22f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Ridge.dataHeart, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Text(
                when (state) {
                    HeartCardState.LIVE -> stringResource(R.string.vital_ridge_from, strap ?: stringResource(R.string.vital_ridge_your_strap))
                    HeartCardState.WAITING -> stringResource(R.string.vital_ridge_waiting_note)
                    HeartCardState.NOT_CONNECTED -> stringResource(R.string.vital_ridge_not_connected_note)
                },
                style = TRN_LEGEND, color = Ridge.inkMuted,
            )
        }
    }
}
