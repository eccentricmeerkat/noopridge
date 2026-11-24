package com.noop.ui.ridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import com.noop.ui.rememberReduceMotion

/**
 * The Ridge accordion card (SQ9 S1, the owner's Notifications example): one glass card whose header reads, left to
 * right, a mint line [icon], a 1 dp divider, a small uppercase [eyebrow] over a serif [title], and a chevron that
 * turns when the card is open. Tapping the header opens or folds [content], which sits under a hairline. The open
 * state survives rotation ([rememberSaveable]); [initiallyExpanded] sets where it starts. Under reduced motion the
 * body appears at once.
 */
@Composable
fun RidgeAccordionCard(
    eyebrow: String,
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    summary: String? = null,
    controlledExpanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var localExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val expanded = controlledExpanded ?: localExpanded
    val reduce = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .ridgeFocusRing(interaction, Ridge.radiusLg)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { if (onToggle != null) onToggle() else localExpanded = !localExpanded }
                    .padding(horizontal = Ridge.space5, vertical = Ridge.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Ridge.space4),
            ) {
                // B2 Hub5a (Settings.dc.html): a 40 dp rounded icon tile, then eyebrow, serif title and a one-line summary.
                Box(
                    Modifier.size(40.dp).background(Ridge.glassFill, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .border(1.dp, Ridge.glassStroke, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RidgeEyebrow(eyebrow)
                    Text(title, style = RidgeType.titleSm, color = Ridge.ink)
                    if (summary != null) Text(summary, style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.inkMuted)
                }
                Icon(
                    RidgeIcons.chevronDown, contentDescription = null, tint = Ridge.inkMuted,
                    modifier = Modifier.size(24.dp).rotate(if (expanded) 180f else 0f),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (reduce) expandVertically(snap()) else expandVertically() + fadeIn(),
                exit = if (reduce) shrinkVertically(snap()) else shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    RidgeRowDivider()
                    androidx.compose.runtime.CompositionLocalProvider(com.noop.ui.LocalFlatCards provides true) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = Ridge.space5, vertical = Ridge.space4),
                            verticalArrangement = Arrangement.spacedBy(Ridge.space3),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

private fun <T> snap() = androidx.compose.animation.core.snap<T>()
