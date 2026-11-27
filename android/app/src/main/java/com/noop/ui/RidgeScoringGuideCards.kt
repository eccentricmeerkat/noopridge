package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow

// RS-3g: the guide's cards, to ScoringGuide.dc.html's values. The icons are the board's own 24-unit line drawings.

internal object GuideIcons {
    const val MEASURES = "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16M12 8.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7M12 2v3M12 19v3M2 12h3M19 12h3"
    const val CAPTURED = "M4 12h3l2-6 4 12 2-6h5M3 20h18"
    const val MEANS = "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10zM16 7l5-4M17 3h4v4"
    const val CONFIDENCE = "M4 18a8 8 0 1 1 16 0M12 18l4-6M12 16.8a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4"
    const val NOTE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M12 11v5M12 8h.01"
}

@Composable
private fun GuideIcon(data: String, size: Dp, color: Color, stroke: Float = 1.6f) {
    val path = remember(data) { PathParser().parsePathString(data).toPath() }
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        scale(unit, unit, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The board's row: the icon in its own column behind a hairline, and [content] beside it. */
@Composable
private fun IconRow(icon: String, divider: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(40.dp).fillMaxHeight().heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            GuideIcon(icon, 30.dp, Ridge.brand)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** One of the glass cards: an eyebrow over a sentence or two, beside its icon. */
@Composable
internal fun GuideTextCard(icon: String, title: String, text: String, textColor: Color = Ridge.ink) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        IconRow(icon, Ridge.glassStroke) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RidgeEyebrow(title, Modifier.semantics { heading() })
                Text(text, style = RidgeType.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = textColor)
            }
        }
    }
}

/**
 * "What it means": the one card ringed in the brand green. [rows], when there are any, list the bands (or the reader's own
 * ranges) under [rowsTitle]; the note closes it.
 */
@Composable
internal fun GuideMeansCard(means: String, note: String, rowsTitle: String?, rows: (@Composable () -> Unit)?) {
    val shape = RoundedCornerShape(Ridge.radiusLg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(0f to Ridge.brand.copy(alpha = 0.16f), 0.7f to Ridge.bg100.copy(alpha = 0.55f)))
            .border(1.dp, Ridge.brand.copy(alpha = 0.5f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(Ridge.space3),
    ) {
        IconRow(GuideIcons.MEANS, Ridge.brand.copy(alpha = 0.35f)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.guide_ridge_means), style = RidgeType.titleSm.copy(fontSize = 20.sp, lineHeight = 24.sp), color = Ridge.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(means, style = RidgeType.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = Ridge.ink)
            }
        }
        if (rows != null && rowsTitle != null) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.brand.copy(alpha = 0.28f)))
                RidgeEyebrow(rowsTitle, Modifier.padding(top = 14.dp, bottom = 2.dp))
                rows()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            GuideIcon(GuideIcons.NOTE, 16.dp, Ridge.inkMuted, 1.75f)
            Text(note, style = RidgeType.bodySm.copy(fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic), color = Ridge.inkMuted)
        }
    }
}

/** A band or a range: its swatch, name and note, with [value] at the end. */
@Composable
internal fun GuideRow(first: Boolean, color: Color, name: String, note: String, value: String) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(width = 12.dp, height = 32.dp).background(color, RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = Ridge.ink)
            Text(note, style = RidgeType.bodySm.copy(fontSize = 13.sp), color = Ridge.inkMuted)
        }
        Text(value, style = RidgeType.titleSm.copy(fontSize = 16.sp, lineHeight = 20.sp), color = Ridge.ink, maxLines = 1)
    }
}
