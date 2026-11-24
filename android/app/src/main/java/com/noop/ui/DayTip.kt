package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where a calendar tile's day tooltip goes (BS-x2-1 item 5). All values are window pixels.
 *
 * The tooltip sits at least [gapPx] above the pressed tile so the thumb never covers it, is clamped to the
 * window's left and right edges by [marginPx], and flips BELOW the tile only when there is no room above
 * (the top row, under [topLimitPx], the status bar).
 */
internal object DayTipPlacement {
    const val GAP_DP = 24

    fun place(anchor: IntRect, popup: IntSize, window: IntSize, gapPx: Int, marginPx: Int, topLimitPx: Int): IntOffset {
        val maxX = (window.width - popup.width - marginPx).coerceAtLeast(marginPx)
        val x = (anchor.center.x - popup.width / 2).coerceIn(marginPx, maxX)
        val above = anchor.top - gapPx - popup.height
        val y = if (above >= topLimitPx) {
            above
        } else {
            (anchor.bottom + gapPx).coerceAtMost((window.height - popup.height - marginPx).coerceAtLeast(topLimitPx))
        }
        return IntOffset(x, y)
    }
}

/** The tiles of one calendar grid and the one that is currently shown. Hit-testing is lazy, so scrolling needs no bookkeeping. */
internal class DayTipRegistry {
    var surface: LayoutCoordinates? = null
    private val tiles = HashMap<LocalDate, LayoutCoordinates>()
    var date by mutableStateOf<LocalDate?>(null)
    var anchor by mutableStateOf<IntRect?>(null)

    fun register(date: LocalDate, coords: LayoutCoordinates) { tiles[date] = coords }

    /** The tile under [p] (surface-local), allowing the 2 dp between tiles. */
    fun hit(p: Offset): LocalDate? {
        val s = surface?.takeIf { it.isAttached } ?: return null
        for ((d, c) in tiles) {
            if (!c.isAttached) continue
            val r: Rect = s.localBoundingBoxOf(c)
            if (p.x >= r.left - 3f && p.x <= r.right + 3f && p.y >= r.top - 3f && p.y <= r.bottom + 3f) return d
        }
        return null
    }

    fun show(d: LocalDate) {
        val c = tiles[d]?.takeIf { it.isAttached } ?: return
        val b = c.boundsInWindow()
        date = d
        anchor = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
    }

    fun hide() { date = null; anchor = null }
}

internal val LocalDayTip = compositionLocalOf<DayTipRegistry?> { null }

/**
 * Wraps one calendar grid. A press held for 300 ms shows the day of the tile under the finger above it, and the
 * tooltip follows the finger as it drags across tiles. The touch handler only observes (initial pass, nothing is
 * consumed), so scrolling and taps keep working; a move past touch slop before the hold cancels it.
 */
@Composable
internal fun DayTipSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val reg = remember { DayTipRegistry() }
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val gapPx = with(density) { DayTipPlacement.GAP_DP.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val topLimitPx = WindowInsets.statusBars.getTop(density)

    androidx.compose.runtime.CompositionLocalProvider(LocalDayTip provides reg) {
        Box(
            modifier
                .onGloballyPositioned { reg.surface = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val start = down.position
                        var last = start
                        val job: Job = scope.launch {
                            delay(300)
                            reg.hit(last)?.let { reg.show(it) }
                        }
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            last = ch.position
                            val shown = reg.date
                            if (shown == null) {
                                if ((last - start).getDistance() > slop) job.cancel()
                            } else {
                                reg.hit(last)?.let { if (it != shown) reg.show(it) }
                            }
                        } while (ev.changes.any { it.pressed })
                        job.cancel()
                        reg.hide()
                    }
                },
        ) {
            content()
            val d = reg.date
            val a = reg.anchor
            if (d != null && a != null) {
                val text = remember(d) {
                    val loc = Locale.getDefault()
                    "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, loc)} · ${d.month.getDisplayName(TextStyle.SHORT, loc)} ${d.dayOfMonth}".uppercase(loc)
                }
                Popup(
                    popupPositionProvider = object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
                        ): IntOffset = DayTipPlacement.place(a, popupContentSize, windowSize, gapPx, marginPx, topLimitPx)
                    },
                ) {
                    Text(
                        text, style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = Ridge.ink,
                        modifier = Modifier
                            .background(Ridge.bg000, RoundedCornerShape(Ridge.radiusSm))
                            .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusSm))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** A calendar tile: registers its bounds with the surrounding [DayTipSurface] so the day can be shown while it is pressed. */
@Composable
internal fun DayTipBox(date: LocalDate, enabled: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val reg = LocalDayTip.current
    Box(
        if (enabled && reg != null) modifier.onGloballyPositioned { reg.register(date, it) } else modifier,
        content = content,
    )
}
