package com.noop.ui

import android.content.Context
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.noop.R
import com.noop.analytics.VitalBands
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeIcons
import kotlinx.coroutines.delay

// B2 Hub3a (VitalSigns.dc.html): the Vital signs page, new under Ridge (v10's Health Monitor and Vital Signs were retired
// into Explore). The figures, the ranges and the skin-temperature setting are the ones the old vitals grid used.

/** The vitals the board shows, in its order, for the latest day with data; each already banded against the wearer's own normal. */
internal fun ridgeLatestVitals(context: Context, days: List<com.noop.data.DailyMetric>): List<Vital> {
    val latest = days.lastOrNull() ?: return emptyList()
    val all = vitalsFor(
        latest, days, UnitPrefs.temperature(context), emptyMap(), NoopPrefs.spo2CandidateDisplay(context), emptyMap(),
        UnitPrefs.skinTempPreferred(context),
    )
    return listOf("resp", "spo2", "rhr", "hrv", "skin").mapNotNull { k -> all.firstOrNull { it.key == k } }
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

/** Vital signs: the live heart rate, then one tile per reading with its in-range chip; each tile opens that metric. */
@Composable
internal fun RidgeVitalSignsScreen(vm: AppViewModel, onVitalClick: (String) -> Unit) {
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
    val vitals = remember(days, spo2) {
        ridgeLatestVitals(context, days).map { v ->
            val l = spo2
            if (v.key == "spo2" && v.value == null && l != null) {
                val day = l.day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
                v.copy(
                    value = l.value,
                    banding = VitalBands.band(l.value, emptyList(), 95.0..100.0, null),
                    secondary = (if (l.estimate) "$estimateWord · " else "") + day,
                    isEstimate = l.estimate,
                )
            } else v
        }
    }
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
            VitalBands.Band.NO_DATA -> R.string.vital_ridge_no_reading
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

/** The live heart rate: Live or Waiting, the bpm in the serif, the last minute as a line, and where it comes from. */
@Composable
private fun RidgeLiveHeartCard(vm: AppViewModel) {
    val live by vm.live.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val hr = displayHr(bpm, live)
    val isLive = hr != null
    // Final review: opening Vital signs asks the strap to stream, as Live and Body monitor do. The Ridge rebuild (Hub3a)
    // dropped this request, so the card only moved when another screen happened to be streaming. Ref-counted in the
    // ViewModel, so handing off between Live and here never drops the stream.
    DisposableEffect(vm) {
        vm.requestRealtimeHr()
        onDispose { vm.releaseRealtimeHr() }
    }
    val history = remember { mutableStateListOf<LiveHrSample>() }
    val latest by rememberUpdatedState(hr)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                appendLiveHrSample(history, latest, System.currentTimeMillis(), cap = 60)
                delay(1000)
            }
        }
    }
    var strap by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.activeStrapId) {
        strap = runCatching { vm.pairedDevices() }.getOrDefault(emptyList()).firstOrNull { it.id == vm.activeStrapId }?.let { displayName(it) }
    }
    val heart = if (isLive) Ridge.dataHeart else Ridge.inkSubtle
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RidgeEyebrow(stringResource(R.string.vital_ridge_heart))
                Box(Modifier.weight(1f))
                val pill = if (isLive) Ridge.brand else Ridge.inkSubtle
                Text(
                    stringResource(if (isLive) R.string.vital_ridge_live else R.string.vital_ridge_waiting),
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
            // The last minute streams right to left: the newest reading sits at the right edge and slides left as time passes
            // (a frame-clock tick while live; once a second under Reduce motion, battery saver or Quiet motion).
            val streamNow = remember { mutableLongStateOf(System.currentTimeMillis()) }
            val calm = rememberPoseStill()
            LaunchedEffect(isLive, calm) {
                while (isLive) {
                    if (calm) delay(1000) else withFrameNanos { }
                    streamNow.longValue = System.currentTimeMillis()
                }
            }
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val now = streamNow.longValue
                val pts = liveHrStream(history.toList(), now)
                val color = Ridge.dataHeart.copy(alpha = if (pts.size > 1) 1f else 0.15f)
                val path = Path()
                if (pts.size > 1) {
                    pts.forEachIndexed { i, p ->
                        val x = size.width * p.x
                        val y = size.height * p.y
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    // Carry the line to the right edge at the newest value, so it reads as a continuous stream between beats.
                    path.lineTo(size.width, size.height * pts.last().y)
                } else {
                    path.moveTo(0f, size.height / 2); path.lineTo(size.width, size.height / 2)
                }
                drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (pts.size > 1) drawCircle(color, 3.5.dp.toPx(), Offset(size.width, size.height * pts.last().y))
            }
            Text(
                if (isLive) stringResource(R.string.vital_ridge_from, strap ?: stringResource(R.string.vital_ridge_your_strap))
                else stringResource(R.string.vital_ridge_waiting_note),
                style = TRN_LEGEND, color = Ridge.inkMuted,
            )
        }
    }
}
