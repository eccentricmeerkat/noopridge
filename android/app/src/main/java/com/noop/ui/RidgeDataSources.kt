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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeButtonKind
import java.util.Locale
import kotlin.math.roundToInt

// B2 Hub4 (owner edit): the Your data page's cards, drawn as `YourData.dc.html` draws them. These are presentation
// only; the importers, the counts and every setting stay in DataSourcesScreen, which passes them in.

/** How a source's state pill reads: on (mint), busy (blue) or off (muted). */
internal enum class SourceTone { ON, BUSY, OFF }

private val SRC_TITLE = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFamily = RidgeType.titleSm.fontFamily)
private val SRC_DESC = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
private val SRC_NOTE = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
private val SRC_PILL = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)

private val hairline = Color(190, 220, 235).copy(alpha = 0.14f)
private val busyBlue get() = Ridge.effort

/** The board's state pill: 24 dp, 11 sp, a 1 dp outline in the state's colour. */
@Composable
internal fun RidgeSourcePill(text: String, tone: SourceTone) {
    val c = when (tone) { SourceTone.ON -> Ridge.brand; SourceTone.BUSY -> busyBlue; SourceTone.OFF -> Ridge.inkSubtle }
    val edge = when (tone) { SourceTone.ON -> Ridge.brand.copy(alpha = 0.4f); SourceTone.BUSY -> busyBlue.copy(alpha = 0.4f); SourceTone.OFF -> Color(190, 220, 235).copy(alpha = 0.25f) }
    Box(
        Modifier.height(24.dp).clip(CircleShape).border(BorderStroke(1.dp, edge), CircleShape).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = SRC_PILL, color = c, maxLines = 1) }
}

/** A plain source card: title and description at the left, the state pill at the right, an optional count, then [content]. */
@Composable
internal fun RidgeSourceCard(
    title: String, description: String, state: String, tone: SourceTone,
    count: String? = null,
    content: @Composable () -> Unit = {},
) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = SRC_TITLE, color = Ridge.ink)
                    Text(description, style = SRC_DESC, color = Ridge.inkMuted)
                }
                RidgeSourcePill(state, tone)
            }
            if (count != null) Text(count, style = SRC_NOTE, color = Ridge.inkSubtle)
            content()
        }
    }
}

/** The board's 52 x 32 switch. */
@Composable
internal fun RidgeSwitch(on: Boolean, description: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Ridge.brand else Color(190, 220, 235).copy(alpha = 0.22f))
            .clickable { onChange(!on) }
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier.padding(start = if (on) 24.dp else 4.dp, top = 4.dp).size(24.dp).clip(CircleShape)
                .background(if (on) Ridge.bg000 else Ridge.ink),
        )
    }
}

/** A row with a title and a note at the left and a switch at the right (the board's "Share computed metrics back"). */
@Composable
internal fun RidgeToggleRow(title: String, note: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium), color = Ridge.ink)
            if (note != null) Text(note, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.inkMuted)
        }
        RidgeSwitch(on, title, onChange)
    }
}

/** One stat cell of the Health Connect card: an icon, a bold line and a muted line. */
internal data class HcStat(val icon: androidx.compose.ui.graphics.vector.ImageVector, val head: String, val sub: String)

/**
 * The Health Connect card (board `hc.card`): a larger 28 dp card that glows mint while connected, a title with its state
 * pill, the description beside a round expand button, three stat cells, then the settings when expanded.
 */
@Composable
internal fun RidgeHealthConnectCard(
    connected: Boolean, state: String, tone: SourceTone, description: String,
    stats: List<HcStat>, open: Boolean, onToggle: () -> Unit,
    expanded: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val surface = if (connected) {
        Modifier
            .background(
                Brush.verticalGradient(listOf(Ridge.brand.copy(alpha = 0.22f), Ridge.brand.copy(alpha = 0.10f), Ridge.bg100.copy(alpha = 0.55f))),
                shape,
            )
            .border(BorderStroke(1.5.dp, Ridge.brand.copy(alpha = 0.55f)), shape)
    } else {
        Modifier.background(Ridge.glassFill, shape).border(BorderStroke(1.dp, Color(190, 220, 235).copy(alpha = 0.16f)), shape)
    }
    Column(Modifier.fillMaxWidth().then(surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.data_ridge_hc), style = RidgeType.titleSm.copy(fontSize = 18.sp, lineHeight = 21.sp), color = Ridge.ink, modifier = Modifier.weight(1f))
            RidgeSourcePill(state, tone)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(description, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = Ridge.inkMuted, modifier = Modifier.weight(1f))
            val cd = stringResource(if (open) R.string.data_ridge_hc_hide else R.string.data_ridge_hc_show)
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Ridge.bg000.copy(alpha = 0.25f))
                    .border(BorderStroke(1.dp, Color(190, 220, 235).copy(alpha = 0.22f)), CircleShape)
                    .clickable(onClick = onToggle).semantics { contentDescription = cd },
                contentAlignment = Alignment.Center,
            ) { Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Ridge.ink) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
        Row(Modifier.fillMaxWidth()) {
            stats.forEachIndexed { i, s ->
                Column(
                    Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(s.icon, null, tint = if (connected) Ridge.brand else Ridge.inkSubtle, modifier = Modifier.size(22.dp))
                    Text(s.head, style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), color = Ridge.ink, maxLines = 1)
                    Text(s.sub, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.inkMuted, maxLines = 2)
                }
            }
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ridge.bg000.copy(alpha = 0.38f))
                    .border(BorderStroke(1.dp, Color(190, 220, 235).copy(alpha = 0.10f)), RoundedCornerShape(18.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { expanded() }
        }
    }
}

/** A full-width outlined action (the board's `nr-btn-secondary`). */
@Composable
internal fun RidgeSourceButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Drawn here, not with RidgeButton, so a long label (the board's "Chart any imported series in Insights") wraps.
    val shape = RoundedCornerShape(Ridge.radiusPill)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape)
            .border(BorderStroke(1.5.dp, Ridge.inkMuted), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(), style = RidgeType.button.copy(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            color = if (enabled) Ridge.ink else Ridge.inkSubtle, maxLines = 2,
        )
    }
}

// MARK: - Imported tab

private data class ImportedKey(val label: String, val key: String, val daily: Boolean)

private data class ImportedTile(val label: String, val value: String, val source: String)

/**
 * The Imported tab: the latest value of each imported series, from Apple Health and Health Connect, one glass tile each
 * with the source it came from, then a button to chart them in Insights. Values are read, never changed.
 */
@Composable
internal fun RidgeImportedTab(vm: AppViewModel, onOpenInsights: () -> Unit) {
    val context = LocalContext.current
    val unitSystem = remember { UnitPrefs.system(context) }
    var tiles by remember { mutableStateOf<List<ImportedTile>?>(null) }
    val sourceApple = stringResource(R.string.data_ridge_src_apple)
    val sourceHc = stringResource(R.string.data_ridge_src_hc)
    val labels = mapOf(
        "steps" to stringResource(R.string.data_ridge_k_steps), "active_kcal" to stringResource(R.string.data_ridge_k_active),
        "resting_hr" to stringResource(R.string.data_ridge_k_rhr), "hrv" to stringResource(R.string.data_ridge_k_hrv),
        "weight" to stringResource(R.string.data_ridge_k_weight), "vo2max" to stringResource(R.string.data_ridge_k_vo2),
        "body_fat" to stringResource(R.string.data_ridge_k_fat), "spo2" to stringResource(R.string.data_ridge_k_spo2),
    )
    LaunchedEffect(Unit) {
        val out = mutableListOf<ImportedTile>()
        val devices = listOf("apple-health" to sourceApple, "health-connect" to sourceHc)
        val keys = listOf(
            ImportedKey("steps", "steps", true), ImportedKey("active_kcal", "active_kcal", true),
            ImportedKey("resting_hr", "resting_hr", false), ImportedKey("hrv", "hrv", false),
            ImportedKey("weight", "weight", true), ImportedKey("vo2max", "vo2max", true),
            ImportedKey("body_fat", "body_fat", false), ImportedKey("spo2", "spo2", false),
        )
        for (k in keys) {
            var best: Triple<String, Double, String>? = null   // day, value, source label
            for ((dev, name) in devices) {
                val pts: List<Pair<String, Double>> = runCatching {
                    if (k.daily) {
                        val rows = vm.repo.appleDaily(dev, "0000-00-00", "9999-99-99")
                        when (k.key) {
                            "steps" -> rows.mapNotNull { r -> r.steps?.let { r.day to it.toDouble() } }
                            "active_kcal" -> rows.mapNotNull { r -> r.activeKcal?.let { r.day to it } }
                            "vo2max" -> rows.mapNotNull { r -> r.vo2max?.let { r.day to it } }
                            else -> rows.mapNotNull { r -> r.weightKg?.let { r.day to it } }
                        }
                    } else {
                        vm.repo.metricSeries(dev, k.key, "0000-00-00", "9999-99-99").map { it.day to it.value }
                    }
                }.getOrDefault(emptyList())
                val last = pts.maxByOrNull { it.first } ?: continue
                if (best == null || last.first > best.first) best = Triple(last.first, last.second, name)
            }
            val b = best ?: continue
            val v = b.second
            val text = when (k.key) {
                "steps" -> String.format(Locale.US, "%,d", v.roundToInt())
                "active_kcal" -> "${v.roundToInt()} kcal"
                "resting_hr" -> "${v.roundToInt()} bpm"
                "hrv" -> "${v.roundToInt()} ms"
                "weight" -> UnitFormatter.massFromKilograms(v, unitSystem)
                "vo2max" -> String.format(Locale.US, "%.1f", v)
                "body_fat" -> String.format(Locale.US, "%.1f%%", v)
                else -> "${v.roundToInt()}%"
            }
            out += ImportedTile(labels.getValue(k.key), text, b.third)
        }
        tiles = out
    }
    val list = tiles
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.data_ridge_imported_intro), style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted)
        Spacer8()
        when {
            list == null -> Text(stringResource(R.string.data_ridge_loading), style = SRC_DESC, color = Ridge.inkSubtle)
            list.isEmpty() -> GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.data_ridge_none_title), style = SRC_TITLE, color = Ridge.ink)
                    Text(stringResource(R.string.data_ridge_none_body), style = SRC_DESC, color = Ridge.inkMuted)
                }
            }
            else -> list.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { t ->
                        GlassCard(Modifier.weight(1f), radius = Ridge.radiusMd, contentPadding = PaddingValues(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t.label.uppercase(), style = RidgeType.eyebrow.copy(fontSize = 9.sp), color = Ridge.inkSubtle, maxLines = 1)
                                Text(t.value, style = RidgeType.titleSm.copy(fontSize = 20.sp, lineHeight = 24.sp), color = Ridge.ink, maxLines = 1)
                                Text(t.source, style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp), color = Ridge.inkSubtle)
                            }
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        Spacer8()
        RidgeSourceButton(stringResource(R.string.data_ridge_chart), onClick = onOpenInsights)
    }
}

@Composable
private fun Spacer8() { Box(Modifier.height(4.dp)) }


// MARK: - Fused tab

/**
 * The Fused tab (board `isFused`): an intro line, the day-owner chip, one glass card of metrics (name and agreement line
 * at the left, the value and a Compare action at the right) and the privacy line. The values, the agreement states and
 * the compare dialog are the engine's, unchanged.
 */
@Composable
internal fun RidgeFusedBody(record: FusedRecord, dayLabel: String, isMultiSource: Boolean, onCompare: (FusedRow) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.data_ridge_f_intro), style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted)
        if (isMultiSource) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.data_ridge_f_owner, dayLabel).uppercase(),
                    style = RidgeType.eyebrow, color = Ridge.inkSubtle,
                )
                RidgeSourcePill(record.dayOwner?.displayName ?: stringResource(R.string.data_ridge_f_none), SourceTone.ON)
            }
        }
        if (record.rows.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.data_ridge_f_empty_title), style = SRC_TITLE, color = Ridge.ink)
                    Text(stringResource(R.string.data_ridge_f_empty_body), style = SRC_DESC, color = Ridge.inkMuted)
                }
            }
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    record.rows.forEachIndexed { i, row ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                        val point = row.point
                        val other = point.contributors.drop(1).firstOrNull()
                        val conflict = point.agreement == com.noop.analytics.AgreementState.CONFLICT
                        val agree = when {
                            !isMultiSource || other == null -> stringResource(R.string.data_ridge_f_only)
                            point.agreement == com.noop.analytics.AgreementState.AGREE ->
                                stringResource(R.string.data_ridge_f_agrees, other.source.displayName, FusionFormat.value(other.value, point.metric))
                            else -> stringResource(R.string.data_ridge_f_says, other.source.displayName, FusionFormat.value(other.value, point.metric))
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(row.label, style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), color = Ridge.ink)
                                Text(agree, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = if (conflict) Ridge.stateLow else Ridge.inkSubtle)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(FusionFormat.value(point.value, point.metric), style = RidgeType.titleSm.copy(fontSize = 18.sp, lineHeight = 22.sp), color = Ridge.ink)
                                if (conflict) {
                                    Text(
                                        stringResource(R.string.data_ridge_f_compare),
                                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), color = Ridge.stateLow,
                                        modifier = Modifier.clickable { onCompare(row) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.data_ridge_f_privacy), style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = Ridge.inkSubtle)
    }
}
