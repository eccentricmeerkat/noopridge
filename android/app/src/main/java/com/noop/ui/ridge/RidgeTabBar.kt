package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge
import com.noop.ui.RidgeElevation
import com.noop.ui.RidgeType

/** One bottom-bar slot: a stable [key] (the route), its [label] and line [icon]. */
data class RidgeTab(val key: String, val label: String, val icon: ImageVector)

/**
 * The Ridge bottom bar (B-02; design system p. 12, `More.dc.html`): five equal slots, no centre button, a glass
 * bar on the deepest ground with radius-xl corners. The selected tab draws its icon and label in brand mint with a
 * short mint underline and the rest glow behind it; the others are ink-subtle. The caller floats it 16 dp from the
 * edges and clear of the gesture bar (A-01). Tabs are a selectable group with Role.Tab for TalkBack.
 */
@Composable
fun RidgeTabBar(
    tabs: List<RidgeTab>,
    selectedKey: String?,
    onSelect: (RidgeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Ridge.radiusXl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(Ridge.bg000.copy(alpha = 0.88f), shape)
            .background(Ridge.glassFillStrong, shape)
            .border(1.dp, Ridge.glassStroke, shape)
            .padding(horizontal = Ridge.space2)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in tabs) {
            val on = tab.key == selectedKey
            val tint = if (on) Ridge.brand else Ridge.inkSubtle
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .weight(1f)
                    .height(60.dp)
                    .ridgeFocusRing(interaction, Ridge.radiusMd)
                    .selectable(
                        selected = on, role = Role.Tab, interactionSource = interaction, indication = null,
                        onClick = { onSelect(tab) },
                    )
                    .then(if (on) Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(RidgeElevation.glowRest, Color.Transparent),
                                center = Offset(size.width / 2f, size.height * 0.38f),
                                radius = size.minDimension * 0.62f,
                            ),
                            radius = size.minDimension * 0.62f,
                            center = Offset(size.width / 2f, size.height * 0.38f),
                        )
                    } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))  // B2-1a-5: the board's 20 px (was 24)
                Text(tab.label, style = RidgeType.tab, color = tint, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                Box(
                    Modifier.padding(top = 4.dp).width(16.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                        .background(if (on) Ridge.brand else Color.Transparent),
                )
            }
        }
    }
}
