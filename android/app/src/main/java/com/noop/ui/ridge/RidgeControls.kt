package com.noop.ui.ridge

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import com.noop.ui.rememberReduceMotion

/**
 * The Ridge toggle (`Settings.dc.html`): a 52 x 32 track, brand mint when on and a glass grey when off, with a 24 dp
 * knob in ink-on-liquid (on) or cream (off). The knob slides in 160 ms, instantly under reduced motion. The touch
 * target is 52 x 48.
 */
@Composable
fun RidgeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val reduce = rememberReduceMotion()
    val x by animateDpAsState(if (checked) 24.dp else 4.dp, if (reduce) tween(0) else tween(160), label = "knob")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 48.dp)
            .toggleable(
                value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = interaction, indication = null, onValueChange = onCheckedChange,
            )
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .ridgeFocusRing(interaction, 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (checked) Ridge.brand else Ridge.toggleTrackOff),
        ) {
            Box(
                Modifier.offset(x = x, y = 4.dp).size(24.dp).clip(CircleShape)
                    .background(if (checked) Ridge.inkOnLiquid else Ridge.ink),
            )
        }
    }
}

/**
 * Ridge segmented pills (`Trends.dc.html` range pills): a pill container in glass fill with a 1 dp glass stroke;
 * the selected segment is brand mint with ink-on-liquid text, the others are transparent with muted ink. Each
 * segment is at least 48 dp tall (B-02). With [fill] the pills span the row in equal segments (the Trends range).
 */
@Composable
fun <T> RidgeSegmentedPills(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    itemPadding: androidx.compose.ui.unit.Dp = Ridge.space4,
) {
    val shape = RoundedCornerShape(Ridge.radiusPill)
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(Ridge.glassFill, shape)
            .border(1.dp, Ridge.glassStroke, shape)
            .padding(Ridge.space1)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Ridge.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            val on = item == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 40.dp)
                    .widthIn(min = 48.dp)
                    .ridgeFocusRing(interaction, Ridge.radiusPill)
                    .clip(shape)
                    .background(if (on) Ridge.brand else Color.Transparent, shape)
                    .selectable(
                        selected = on, role = Role.Tab, interactionSource = interaction, indication = null,
                        onClick = { onSelect(item) },
                    )
                    .padding(horizontal = itemPadding, vertical = Ridge.space2),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(item), style = RidgeType.bodySm, color = if (on) Ridge.inkOnLiquid else Ridge.inkMuted, maxLines = 1)
            }
        }
    }
}

/**
 * The Ridge stepper (`Intervals.dc.html`): minus and plus buttons around the value, clamped to [range] in [step]
 * steps, each button a 48 dp target with "Decrease / Increase [label]" for accessibility. The value reads in
 * Playfair numerals with an optional [unit].
 */
@Composable
fun RidgeStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    unit: String? = null,
) {
    Row(
        modifier.semantics { stateDescription = "$value${unit?.let { " $it" } ?: ""}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ridge.space2),
    ) {
        StepButton(RidgeIcons.minus, "Decrease ${label.lowercase()}", enabled = value - step >= range.first) {
            onValueChange((value - step).coerceIn(range))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Ridge.space1)) {
            Text(value.toString(), style = RidgeType.numeralMd, color = Ridge.ink)
            if (unit != null) Text(unit, style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
        StepButton(RidgeIcons.plus, "Increase ${label.lowercase()}", enabled = value + step <= range.last) {
            onValueChange((value + step).coerceIn(range))
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .selectable(
                selected = false, enabled = enabled, role = Role.Button, interactionSource = interaction,
                indication = null, onClick = onClick,
            )
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).ridgeFocusRing(interaction, 18.dp).clip(CircleShape)
                .background(Ridge.glassFillStrong).border(1.dp, Ridge.glassStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Ridge.ink, modifier = Modifier.size(18.dp)) }
    }
}
