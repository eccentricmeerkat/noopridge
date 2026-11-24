package com.noop.ui

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS-x2-1 item 5: where a calendar tile's tooltip goes, at the corners of the screen. */
class DayTipPlacementTest {
    private val window = IntSize(1080, 2400)
    private val popup = IntSize(260, 90)
    private val gap = 72   // 24 dp at 3x
    private val margin = 24
    private val top = 120  // status bar

    private fun place(anchor: IntRect) = DayTipPlacement.place(anchor, popup, window, gap, margin, top)

    @Test fun middleTileGetsTheTooltipAboveWithTheFullGap() {
        val a = IntRect(500, 1000, 540, 1040)
        val p = place(a)
        assertEquals(a.top - gap - popup.height, p.y)
        assertTrue(p.y + popup.height + gap <= a.top)
        assertEquals(a.center.x - popup.width / 2, p.x)
    }

    @Test fun firstColumnIsClampedToTheLeftEdge() {
        val p = place(IntRect(0, 1000, 40, 1040))
        assertEquals(margin, p.x)
    }

    @Test fun lastColumnIsClampedToTheRightEdge() {
        val p = place(IntRect(1040, 1000, 1080, 1040))
        assertEquals(window.width - popup.width - margin, p.x)
    }

    @Test fun topRowFlipsBelowWhenThereIsNoRoomAbove() {
        val a = IntRect(500, 150, 540, 190)
        val p = place(a)
        assertEquals(a.bottom + gap, p.y)
    }

    @Test fun lastRowKeepsTheTooltipAboveAndOnScreen() {
        val a = IntRect(500, 2300, 540, 2340)
        val p = place(a)
        assertEquals(a.top - gap - popup.height, p.y)
        assertTrue(p.y + popup.height <= window.height)
    }

    @Test fun flippedTooltipStaysOnScreenOnALowAnchorWithNoRoomAbove() {
        val p = DayTipPlacement.place(IntRect(500, 160, 540, 2380), popup, window, gap, margin, top)
        assertTrue(p.y + popup.height <= window.height - margin)
    }
}
