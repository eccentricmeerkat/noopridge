package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge
import com.noop.ui.RidgeType

/**
 * The Ridge list row (design system p. 12; Hub `More.dc.html` `.np-row`): a bare 22 dp line [icon], an Inter title and
 * optional muted subtitle, an optional trailing value and a chevron. Minimum height 54 dp. Rows inside one card are
 * separated with [RidgeRowDivider]. [iconTint] colours the icon: a metric icon takes its liquid or data hue, a UI
 * icon stays ink-muted, as on the board. The icon is decorative — the row's text names it — so it carries no content
 * description of its own.
 */
@Composable
fun RidgeListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Ridge.inkMuted,
    subtitle: String? = null,
    trailing: String? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .ridgeFocusRing(interaction, Ridge.radiusSm)
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
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RidgeType.body, color = Ridge.ink, maxLines = 1)
            // The board keeps the subtitle on one line and ellipsises it (`.np-row` `white-space: nowrap`).
            if (subtitle != null) {
                Text(subtitle, style = RidgeType.bodySm, color = Ridge.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) Text(trailing, style = RidgeType.bodySm, color = Ridge.inkMuted, maxLines = 1)
        if (chevron) Icon(RidgeIcons.chevronRight, contentDescription = null, tint = Ridge.inkSubtle, modifier = Modifier.size(18.dp))
    }
}

/** The 1 dp glass hairline between rows in one card. */
@Composable
fun RidgeRowDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
}
