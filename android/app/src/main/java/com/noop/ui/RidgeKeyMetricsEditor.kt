package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.noop.R
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeButtonKind
import com.noop.ui.ridge.RidgeSegmentedPills
import kotlin.math.roundToInt

// B2-X (owner, 20 Nov, from a reference image): the Edit Key Metrics editor in the Ridge style. A glass sheet with a serif
// title; the Detailed tiles switch and window pills; the shown tiles as separate glass rows, each with its Explore icon, a
// drag handle to reorder and a remove button; Reset and Done; then the tiles that can be added, grouped by category. At most
// KeyMetric.MAX_SHOWN tiles can be shown.

/** The catalogue entry whose icon and colour a tile uses (the same ones Insights' metric list shows). */
internal fun keyMetricEntry(m: KeyMetric): CatalogEntry? = MetricCatalog.forKey(
    when (m) {
        KeyMetric.CHARGE -> "recovery"
        KeyMetric.EFFORT -> "strain"
        KeyMetric.REST -> "sleep"
        KeyMetric.HRV -> "hrv"
        KeyMetric.RESTING_HR -> "rhr"
        KeyMetric.BLOOD_OXYGEN -> "spo2"
        KeyMetric.RESPIRATORY -> "resp"
        KeyMetric.STEPS -> "steps"
        KeyMetric.WEIGHT -> "weight"
        KeyMetric.CALORIES -> "active_kcal"
        KeyMetric.SKIN_TEMP -> "skin"
        else -> m.catalogKey
    },
)

private val ROW_HEIGHT = 56.dp
private val ROW_GAP = 8.dp

@Composable
internal fun RidgeKeyMetricsEditor(
    initial: List<KeyMetric>,
    initialDetailed: Boolean,
    initialWindowDays: Int,
    onDismiss: () -> Unit,
    onSave: (List<KeyMetric>, Boolean, Int) -> Unit,
) {
    val shown = remember { mutableStateListOf<KeyMetric>().apply { addAll(initial) } }
    val hidden = remember { mutableStateListOf<KeyMetric>().apply { addAll(KeyMetric.entries.filter { it !in initial }) } }
    var detailed by remember { mutableStateOf(initialDetailed) }
    var windowDays by remember { mutableIntStateOf(initialWindowDays) }
    val titles = KeyMetric.entries.associateWith { stringResource(it.titleRes) }
    val groups = CatCategory.entries.associateWith { stringResource(it.labelRes) }
    val windowLabels = mapOf(
        7 to stringResource(R.string.today_range_1_week), 14 to stringResource(R.string.today_range_2_weeks), 30 to stringResource(R.string.today_range_1_month),
    )
    val atLimit = shown.size >= KeyMetric.MAX_SHOWN
    val density = LocalDensity.current
    val stepPx = with(density) { (ROW_HEIGHT + ROW_GAP).toPx() }
    var dragging by remember { mutableStateOf<KeyMetric?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    com.noop.ui.ridge.RidgeSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = Ridge.space5), verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Text(stringResource(R.string.kme_ridge_title), style = RidgeType.titleSm, color = Ridge.ink, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.kme_ridge_sub), style = RidgeType.bodySm, color = Ridge.inkMuted)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.l10n_today_screen_detailed_tiles_0801721b), style = RidgeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Ridge.ink)
                    Text(stringResource(R.string.kme_ridge_detailed_sub), style = RidgeType.bodySm, color = Ridge.inkMuted)
                }
                RidgeSwitch(detailed, stringResource(R.string.l10n_today_screen_detailed_tiles_0801721b)) { detailed = it }
            }
            if (detailed) {
                RidgeSegmentedPills(
                    items = listOf(7, 14, 30), selected = windowDays, onSelect = { windowDays = it },
                    label = { windowLabels.getValue(it) }, fill = true,
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
            Text(
                if (shown.size <= KeyMetric.MAX_SHOWN) stringResource(R.string.tile_ridge_shown_of, shown.size, KeyMetric.MAX_SHOWN)
                else stringResource(R.string.kme_ridge_over, shown.size, KeyMetric.MAX_SHOWN),
                style = RidgeType.eyebrow, color = if (shown.size <= KeyMetric.MAX_SHOWN) Ridge.inkSubtle else Ridge.stateLow,
            )

            Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                shown.toList().forEach { m ->
                    key(m) {
                        val entry = keyMetricEntry(m)
                        val title = titles.getValue(m)
                        val active = dragging == m
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT)
                                .zIndex(if (active) 1f else 0f)
                                .graphicsLayer { translationY = if (active) dragOffset else 0f }
                                .then(if (active) Modifier.shadow(8.dp, shape) else Modifier)
                                .clip16(shape)
                                .background(if (active) Ridge.glassFillStrong else Ridge.glassFill, shape)
                                .border(1.dp, if (active) Ridge.brand.copy(alpha = 0.5f) else Ridge.glassStroke, shape)
                                .padding(start = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            entry?.let { Icon(it.icon, contentDescription = null, tint = it.accent(), modifier = Modifier.size(22.dp)) }
                                ?: Box(Modifier.size(22.dp))
                            Text(title, style = RidgeType.body, color = Ridge.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            // The drag handle: press and drag to reorder; the row follows the finger and the others make way.
                            val dragDesc = stringResource(R.string.kme_ridge_drag, title)
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = dragDesc }
                                    .pointerInput(m) {
                                        detectDragGestures(
                                            onDragStart = { dragging = m; dragOffset = 0f },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val cur = shown.indexOf(m)
                                                val target = (cur + (dragOffset / stepPx).roundToInt()).coerceIn(0, shown.lastIndex)
                                                if (target != cur) {
                                                    shown.add(target, shown.removeAt(cur))
                                                    dragOffset -= (target - cur) * stepPx
                                                }
                                            },
                                            onDragEnd = { dragging = null; dragOffset = 0f },
                                            onDragCancel = { dragging = null; dragOffset = 0f },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.DragHandle, contentDescription = null, tint = Ridge.inkMuted) }
                            val removeDesc = stringResource(R.string.today_customize_hide, title)
                            val canRemove = shown.size > 1
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = removeDesc }
                                    .clickable(enabled = canRemove) { shown.remove(m); hidden.add(m) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, contentDescription = null, tint = if (canRemove) Ridge.inkMuted else Ridge.inkSubtle, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = Ridge.space2), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.l10n_today_screen_reset_44c57abd), style = RidgeType.body, color = Ridge.inkMuted,
                    modifier = Modifier.clickable {
                        shown.clear(); shown.addAll(KeyMetric.defaultOrder)
                        hidden.clear(); hidden.addAll(KeyMetric.entries.filter { it !in KeyMetric.defaultOrder })
                        detailed = false; windowDays = 14
                    }.padding(vertical = 12.dp, horizontal = 4.dp),
                )
                Box(Modifier.weight(1f))
                RidgeButton(
                    text = stringResource(R.string.l10n_today_screen_done_e9b450d1),
                    onClick = { onSave(shown.toList(), detailed, windowDays) },
                    kind = RidgeButtonKind.Primary, enabled = shown.isNotEmpty(),
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
            Text(stringResource(R.string.kme_ridge_add), style = RidgeType.eyebrow, color = Ridge.inkSubtle)
            if (atLimit) Text(stringResource(R.string.tile_ridge_limit, KeyMetric.MAX_SHOWN), style = RidgeType.bodySm, color = Ridge.stateLow)
            hidden.groupBy { groups.getValue(keyMetricCategory(it)) }.forEach { (group, items) ->
                Text(group.uppercase(), style = RidgeType.eyebrow, color = Ridge.brand, modifier = Modifier.padding(top = Ridge.space2))
                items.forEach { m ->
                    val entry = keyMetricEntry(m)
                    val title = titles.getValue(m)
                    val showDesc = stringResource(R.string.today_customize_show, title)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn56()
                            .clickable(enabled = !atLimit) { hidden.remove(m); shown.add(m) }
                            .semantics { contentDescription = showDesc },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        entry?.let { Icon(it.icon, contentDescription = null, tint = it.accent().copy(alpha = if (atLimit) 0.4f else 1f), modifier = Modifier.size(22.dp)) }
                            ?: Box(Modifier.size(22.dp))
                        Text(title, style = RidgeType.body, color = if (atLimit) Ridge.inkSubtle else Ridge.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.Filled.Add, contentDescription = null, tint = if (atLimit) Ridge.inkSubtle else Ridge.brand, modifier = Modifier.padding(end = 8.dp).size(22.dp))
                    }
                }
            }
        }
    }
}

private fun Modifier.clip16(shape: RoundedCornerShape): Modifier = this.then(Modifier.graphicsLayer { clip = true; this.shape = shape })

private fun Modifier.heightIn56(): Modifier = this.then(Modifier.height(48.dp))

/** The category a tile is listed under (the original eleven map to theirs). */
internal fun keyMetricCategory(m: KeyMetric): CatCategory = keyMetricEntry(m)?.category ?: CatCategory.HEALTH
