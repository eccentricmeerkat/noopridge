package com.noop.ui.ridge

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Noop Ridge line icons (B-02): 24 dp grid, 1.75 stroke, round caps and joins, no fill (design system p. 13).
 * Path data is copied verbatim from the prototype boards (the `.dc.html` boards in `design/prototype/`), so each icon is the
 * board's own drawing. Draw with `Icon(..., tint = …)`; the stroke takes the tint.
 */
object RidgeIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.75f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    // Journal caffeine tiles (Journal.dc.html).
    val coffee = icon("coffee", "M5 11h11v3a5 5 0 0 1-5 5H10a5 5 0 0 1-5-5zM16 12h1.5a2.2 2.2 0 0 1 0 4.4H16M9 4c-1 1 1 2 0 3M13 4c-1 1 1 2 0 3")
    val tea = icon("tea", "M5 11h11v3a5 5 0 0 1-5 5H10a5 5 0 0 1-5-5zM16 12h1.5a2.2 2.2 0 0 1 0 4.4H16M9 3.5V7M7.5 7h3v3h-3z")
    val energyCan = icon("energyCan", "M8 3h8v2l1 1v13a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V6l1-1z", "M12.5 9l-2 3.5h3l-2 3.5")
    val sodaCan = icon("sodaCan", "M8 3h8v2l1 1v13a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V6l1-1z", "M10.5 10h.01M13.5 12h.01M11 15h.01")

    // Body monitor and Vital signs (BodyMonitor.dc.html, VitalSigns.dc.html).
    val heart = icon("heart", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z")
    val thermometer = icon("thermometer", "M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0z")

    // Tabs (More.dc.html bottom bar).
    val sun = icon(
        "sun",
        "M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4" +
            "M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4",
    )
    val bars = icon("bars", "M6 20v-6M12 20V6M18 20v-10")
    val peak = icon("peak", "M2.5 19.5 9 8.5l3.2 5.2L15 9.5l6.5 10zM7.2 11.6l1.8 1.4 1.4-1.3")
    val moon = icon("moon", "M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z")
    /** Hub: the board's circle (`<circle cx=12 cy=12 r=8.5>`), as a path. */
    val hub = icon("hub", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0")

    // UI.
    val chevronRight = icon("chevronRight", "M9 5l7 7-7 7")
    val back = icon("back", "M15 5l-7 7 7 7")
    val chevronDown = icon("chevronDown", "M5 9l7 7 7-7")
    val close = icon("close", "M6 6l12 12M18 6L6 18")
    val check = icon("check", "M5 12l5 5 9-10")
    val plus = icon("plus", "M12 5v14M5 12h14")
    val minus = icon("minus", "M5 12h14")
    val sliders = icon("sliders", "M4 7h10M18 7h2M4 17h4M12 17h8M14 5v4M8 15v4")

    // Hub menu rows (`More.dc.html` `.np-row`, `BodyMore.dc.html`): the board's 22 dp line icons, one per row.
    val trendUp = icon("trendUp", "M3 17l6-6 4 4 8-8M15 7h6v6")
    val dumbbell = icon("dumbbell", "M3 10v4M6 7v10M18 7v10M21 10v4M6 12h12")
    val wind = icon("wind", "M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3")
    val wave = icon("wave", "M2.5 12c1.6-3.5 3.2-3.5 4.8 0s3.2 3.5 4.7 0 3.2-3.5 4.8 0 3.1 3.5 4.7 0")
    val ellipsis = icon("ellipsis", "M8.5 12h.01M12 12h.01M15.5 12h.01")
    val strap = icon("strap", "M9 2.5h6v4H9zM9 17.5h6v4H9zM7 6.5h10v11H7zM12 10v2.5")
    val database = icon(
        "database",
        "M4 7c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 7v10c0 1.7 3.6 3 8 3s8-1.3 8-3V7" +
            "M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3",
    )
    val bolt = icon("bolt", "M13 3 5 13.5h6L10 21l8-10.5h-6z")
    val bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0")
    val chartAxis = icon("chartAxis", "M4 19V5M4 19h16M8 15l3-4 3 2 5-6")
    val info = icon("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M12 11v6M12 7.5h.01")
    val flask = icon("flask", "M9 3h6M10 3v6l-5 9a2 2 0 0 0 1.8 3h10.4a2 2 0 0 0 1.8-3l-5-9V3")
    val droplet = icon("droplet", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z")

    // Content.
    val pulse = icon("pulse", "M3 12h4l2-5 4 10 2-5h6")
    val sparkle = icon("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z")
    val checkCircle = icon("checkCircle", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M8 12.5l2.7 2.7L16 9.5")
    val journal = icon("journal", "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h11")
    val run = icon(
        "run",
        "M15 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3M13 8.5l-3 4 3 2.5-1.5 5M10 12.5l-4 .5M13 8.5l2.5 2.5H19" +
            "M13 15l3.5 1.5L15 20",
    )
}
