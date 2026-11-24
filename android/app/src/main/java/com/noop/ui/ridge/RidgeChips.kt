package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge
import com.noop.ui.RidgeType

/** Chip kinds (design system p. 12): [Outline] "MAINTAIN", [Solid] "● SOLID", [Status] a live state such as
 *  "● WHOOP SYNCING", [Alert] "BETA", [Dropdown] a picker such as "vs typical ⌄". */
enum class RidgeChipKind { Outline, Solid, Status, Alert, Dropdown }

/**
 * A Noop Ridge chip (B-02). Labels are uppercase Inter label style, except [RidgeChipKind.Dropdown], which keeps
 * the caller's casing. 36 dp tall; with [onClick] the touch target grows to 48 dp. [dot] adds the leading state dot
 * ([RidgeChipKind.Solid] and [RidgeChipKind.Status] show it by default).
 */
@Composable
fun RidgeChip(
    text: String,
    modifier: Modifier = Modifier,
    kind: RidgeChipKind = RidgeChipKind.Outline,
    dot: Boolean = kind == RidgeChipKind.Solid || kind == RidgeChipKind.Status,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Ridge.radiusPill)
    val ink = when (kind) {
        RidgeChipKind.Solid -> Ridge.inkOnLiquid
        RidgeChipKind.Alert -> Ridge.stateDepleted
        RidgeChipKind.Dropdown -> Ridge.inkMuted
        else -> Ridge.ink
    }
    val surface = when (kind) {
        RidgeChipKind.Solid -> Modifier.background(Ridge.brand, shape)
        RidgeChipKind.Outline -> Modifier.border(1.5.dp, Ridge.inkMuted, shape)
        RidgeChipKind.Status -> Modifier.background(Ridge.glassFill, shape).border(1.dp, Ridge.glassStroke, shape)
        RidgeChipKind.Alert -> Modifier.border(1.dp, Ridge.stateDepleted, shape)
        RidgeChipKind.Dropdown -> Modifier.background(Ridge.glassFill, shape).border(1.dp, Ridge.glassStroke, shape)
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.heightIn(min = 48.dp) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(if (kind == RidgeChipKind.Alert) 28.dp else 36.dp)
                .ridgeFocusRing(interaction, Ridge.radiusPill)
                .clip(shape)
                .then(surface)
                .padding(horizontal = if (kind == RidgeChipKind.Alert) Ridge.space3 else Ridge.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ridge.space2),
        ) {
            if (dot) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (kind == RidgeChipKind.Solid) Ridge.inkOnLiquid else Ridge.brand),
                )
            }
            Text(
                if (kind == RidgeChipKind.Dropdown) text else text.uppercase(),
                style = if (kind == RidgeChipKind.Dropdown) RidgeType.bodySm else RidgeType.label,
                color = ink,
                maxLines = 1,
            )
            if (kind == RidgeChipKind.Dropdown) {
                Icon(RidgeIcons.chevronDown, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}
