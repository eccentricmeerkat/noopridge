package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import com.noop.ui.ridgeGlass

// SQ9 S1: the small shared parts the Hub pages are rebuilt from (the audit's §5): the header action, the grouped
// setting row and the page-hero orb. The accordion card is in RidgeAccordion.kt; the segmented control, stepper and
// toggle are in RidgeControls.kt.

/**
 * A pill action at the right of a tool page's title ("Backup", "Timer", "Advanced", a version): a glass pill with a
 * 1 dp stroke, an optional line [icon] and a label, 31 dp tall like the other header controls.
 */
@Composable
fun RidgeHeaderAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Ridge.radiusPill)
    Row(
        modifier
            .heightIn(min = 31.dp)
            .ridgeFocusRing(interaction, Ridge.radiusPill)
            .clip(shape)
            .ridgeGlass(radius = Ridge.radiusPill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Ridge.space3, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ridge.space1),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(16.dp))
        Text(label, style = RidgeType.bodySm, color = Ridge.ink, maxLines = 1)
    }
}

/**
 * One row of a grouped settings list (Settings, Notifications, Backup, Test Centre): a [title] with an optional
 * one-line [subtitle] on the left and a [trailing] control (a toggle, stepper, value or chevron) on the right. Rows
 * in one [GlassCard] are separated with [RidgeRowDivider]. A row with [onClick] is a button as a whole.
 */
@Composable
fun RidgeSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = Ridge.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ridge.space3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RidgeType.body, color = Ridge.ink)
            if (subtitle != null) Text(subtitle, style = RidgeType.bodySm, color = Ridge.inkMuted)
        }
        trailing?.invoke()
    }
}

/**
 * A page-hero orb (Stress, Hydration, Live, Breathe, Devices, Power saving, the scoring guide): a glass disc of
 * [diameter] carrying a serif [value] with a small [caption] under it, its rim and wash in [tint]. This is the page
 * size of the Today orb family; it draws no ring, so a gauge goes in [content] when a page needs one.
 */
@Composable
fun RidgeHeroOrb(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    tint: Color = Ridge.brand,
    diameter: Dp = 168.dp,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .size(diameter)
            .ridgeGlass(radius = diameter / 2, strong = true, tint = tint)
            .border(1.dp, tint.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content?.invoke()
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = Ridge.space4)) {
            Text(value, style = RidgeType.numeralLg, color = Ridge.ink, maxLines = 1, textAlign = TextAlign.Center)
            RidgeEyebrow(caption)
        }
    }
}

/** A small serif stat tile (the board's workout stats): an eyebrow [label] over a serif [value], on a glass fill. */
@Composable
fun RidgeStatTile(label: String, value: String, modifier: Modifier = Modifier, boardStyle: Boolean = false) {
    // B2-1b: boardStyle is Main.dc.html's pop-up tile: 18 dp corners, a hairline, a 20/24 serif value, 4 dp apart.
    val shape = RoundedCornerShape(if (boardStyle) Ridge.radiusMd else Ridge.radiusSm)
    Column(
        modifier
            .clip(shape)
            .background(Ridge.glassFillStrong)
            .then(if (boardStyle) Modifier.border(1.dp, Ridge.glassStroke, shape) else Modifier)
            .padding(horizontal = Ridge.space3, vertical = Ridge.space3),
        verticalArrangement = Arrangement.spacedBy(if (boardStyle) 4.dp else 2.dp),
    ) {
        RidgeEyebrow(label)
        Text(
            value,
            style = if (boardStyle) RidgeType.titleSm.copy(fontSize = 20.sp, lineHeight = 24.sp) else RidgeType.titleSm,
            color = Ridge.ink,
            maxLines = 1,
        )
    }
}
