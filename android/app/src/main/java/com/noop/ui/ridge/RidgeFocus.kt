package com.noop.ui.ridge

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge

/** The Ridge focus ring (design system p. 16): 2 dp solid [Ridge.focusRing] (cream), 2 dp outside the control,
 *  drawn only while [interaction] holds keyboard/D-pad focus. [corner] is the control's own corner radius. */
@Composable
fun Modifier.ridgeFocusRing(interaction: InteractionSource, corner: Dp): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    return drawWithContent {
        drawContent()
        if (focused) {
            val gap = 2.dp.toPx(); val w = 2.dp.toPx(); val o = gap + w / 2f
            val r = corner.toPx() + o
            drawRoundRect(
                color = Ridge.focusRing,
                topLeft = Offset(-o, -o),
                size = Size(size.width + 2 * o, size.height + 2 * o),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = w),
            )
        }
    }
}
