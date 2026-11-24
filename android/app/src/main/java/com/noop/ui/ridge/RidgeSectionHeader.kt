package com.noop.ui.ridge

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge
import com.noop.ui.RidgeType

/** An uppercase Inter eyebrow ("TODAY · SUN, 20 SEP"), tracked, in [Ridge.inkMuted]. */
@Composable
fun RidgeEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = RidgeType.eyebrow, color = Ridge.inkMuted, modifier = modifier, maxLines = 1)
}

/**
 * The chevron a card carries on its eyebrow row (`Trends.dc.html`, B2-2a-1): a hairline ring around a small
 * chevron, opening that metric in Insights. [description] is its accessibility label; the icon itself is
 * decorative, so a screen reader announces one button, not a button and a shape.
 */
@Composable
fun RidgeCardChevron(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, Ridge.glassStroke, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(RidgeIcons.chevronRight, contentDescription = null, tint = Ridge.inkMuted, modifier = Modifier.size(16.dp)) }
}

/**
 * The Ridge section header (design system p. 12): an optional [eyebrow], a Playfair [title] ("Key Metrics") and an
 * optional trailing note or action aligned to the title's baseline ("14-day trend"). The title is a heading
 * for accessibility.
 */
@Composable
fun RidgeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        if (eyebrow != null) RidgeEyebrow(eyebrow)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title, style = RidgeType.titleLg, color = Ridge.ink,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            trailing?.invoke()
        }
    }
}

/** The muted trailing note a section header carries ("14-day trend"). */
@Composable
fun RidgeHeaderNote(text: String) {
    Text(text, style = RidgeType.bodySm, color = Ridge.inkMuted, maxLines = 1)
}
