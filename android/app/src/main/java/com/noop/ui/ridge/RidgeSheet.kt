package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge

/**
 * The Ridge bottom sheet (SQ8, Main.dc.html's sheet): a slide-up panel over a 62% dark scrim, 28 dp top corners, the
 * prototype's dark gradient with a hairline on top, a 40 × 4 grab handle, at most 86% of the screen, scrolling inside.
 * Dismissed by the scrim or a drag down. Used by the Today day picker and the Hub's notifications panel ([top] is the
 * gradient's top colour: the Hub prototype's sheet starts bluer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RidgeSheet(onDismiss: () -> Unit, top: Color = Ridge.sheetTop, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = shape,
        containerColor = Color.Transparent,
        scrimColor = Ridge.sheetScrim,
        dragHandle = null,
        tonalElevation = 0.dp,
        // The panel paints down to the screen edge itself (navigationBarsPadding below); the default insets left a strip
        // under it where the page showed through the transparent container.
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .maxHeightFraction(0.86f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(top, Ridge.sheetBottom)))
                .border(1.dp, Ridge.glassStroke, shape)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 34.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Ridge.sheetHandle),
            )
            content()
        }
    }
}

/** At most [fraction] of the height on offer; shorter content keeps its own height. */
private fun Modifier.maxHeightFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val cap = if (constraints.hasBoundedHeight) (constraints.maxHeight * fraction).toInt() else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(maxHeight = cap.coerceAtLeast(constraints.minHeight)))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
