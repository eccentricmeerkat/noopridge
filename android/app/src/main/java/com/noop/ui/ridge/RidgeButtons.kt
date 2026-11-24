package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge
import com.noop.ui.RidgeType

/** Button kinds (design system p. 12): [Primary] mint "START SESSION", [Charge] gold "LOG RECOVERY",
 *  [Secondary] outline "EDIT METRICS", [Text] a bare action with an icon, "HISTORY". */
enum class RidgeButtonKind { Primary, Charge, Secondary, Text }

/**
 * A Noop Ridge button (B-02). Uppercase Inter button style, 48 dp minimum height, pill shape. [wizard] gives the
 * fixed 280 x 52 onboarding CTA (H-01), which keeps the same size and position on every page. Disabled buttons
 * keep their shape at 45 % opacity.
 */
@Composable
fun RidgeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: RidgeButtonKind = RidgeButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    wizard: Boolean = false,
) {
    val shape = RoundedCornerShape(Ridge.radiusPill)
    val ink = when (kind) {
        RidgeButtonKind.Primary, RidgeButtonKind.Charge -> Ridge.inkOnLiquid
        RidgeButtonKind.Secondary, RidgeButtonKind.Text -> Ridge.ink
    }
    val surface = when (kind) {
        RidgeButtonKind.Primary -> Modifier.background(Ridge.brand, shape)
        RidgeButtonKind.Charge -> Modifier.background(Ridge.charge, shape)
        RidgeButtonKind.Secondary -> Modifier.border(1.5.dp, Ridge.inkMuted, shape)
        RidgeButtonKind.Text -> Modifier
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (wizard) Modifier.size(width = 280.dp, height = 52.dp) else Modifier.heightIn(min = 48.dp))
            .ridgeFocusRing(interaction, Ridge.radiusPill)
            .clip(shape)
            .then(surface)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (kind == RidgeButtonKind.Text) Ridge.space2 else Ridge.space6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ridge.space2, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(text.uppercase(), style = RidgeType.button, color = ink, maxLines = 1)
    }
}
