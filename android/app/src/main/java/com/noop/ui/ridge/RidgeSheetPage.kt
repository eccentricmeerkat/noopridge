package com.noop.ui.ridge

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.LocalGlassCards
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import com.noop.ui.pageStatusBarPadding

/**
 * A full Ridge page for what v10 shows as a full-screen popup (the scoring guide, How NOOP works, What's new): the
 * Ridge backdrop, a back chevron ([backDescription] is its label), the "Hub" [eyebrow], a serif [title] and
 * muted [subtitle], then [content] on glass cards, scrolling under the bar. The popup's "Got it" button is gone; back
 * closes it. The same page serves a Hub route and the other places that still present it in a dialog.
 */
@Composable
fun RidgeSheetPage(
    eyebrow: String,
    title: String,
    subtitle: String?,
    backDescription: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalGlassCards provides true) {
        Box(Modifier.fillMaxSize().background(Ridge.bg100)) {
            RidgePageBackdrop()
            Column(
                modifier
                    .fillMaxSize()
                    .pageStatusBarPadding()
                    .verticalScroll(scroll)
                    .padding(horizontal = Ridge.space5)
                    .padding(top = Ridge.space4, bottom = Ridge.space6 + BottomBarStyleStore.barHeightForContent()),
                verticalArrangement = Arrangement.spacedBy(Ridge.space4),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space3)) {
                    RidgeBackButton(backDescription, onClose)
                    RidgeEyebrow(eyebrow)
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                    Text(title, style = RidgeType.titleLg, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                    if (subtitle != null) Text(subtitle, style = RidgeType.bodySm, color = Ridge.inkMuted)
                }
                content()
            }
        }
    }
}

/** The back chevron a Ridge page opens with (the board's, beside the title): a 40 dp touch target, no disc; [description] is its label. */
@Composable
fun RidgeBackButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(RidgeIcons.back, contentDescription = null, tint = Ridge.ink, modifier = Modifier.size(24.dp)) }
}
