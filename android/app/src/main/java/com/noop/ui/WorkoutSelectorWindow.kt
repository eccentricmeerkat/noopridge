package com.noop.ui

/**
 * The visible time window of the Edit workout "Start and end" selector (BS-x2-1 item 2).
 *
 * All values are unix SECONDS. The window always lies inside the calendar day that starts at `dayStartSec`
 * (the selector moves the handles within one day), so a workout near midnight gets a shorter margin on that
 * side rather than a window that leaks into the next day.
 */
internal data class SelectorWindow(val fromSec: Long, val toSec: Long) {
    val spanSec: Long get() = toSec - fromSec
}

internal object WorkoutSelectorWindows {

    /** Margin shown either side of the predicted start and end by default. */
    const val DEFAULT_MARGIN_SEC: Long = 60L * 60L

    /** Zoomed in no further than this (keeps the handles grabbable and the line readable). */
    const val MIN_SPAN_SEC: Long = 10L * 60L

    const val DAY_SEC: Long = 24L * 60L * 60L

    /** Predicted start − 60 min to predicted end + 60 min, clamped to the day. */
    fun default(startSec: Long, endSec: Long, dayStartSec: Long): SelectorWindow {
        val dayEnd = dayStartSec + DAY_SEC
        val lo = (startSec - DEFAULT_MARGIN_SEC).coerceAtLeast(dayStartSec)
        val hi = (endSec + DEFAULT_MARGIN_SEC).coerceAtMost(dayEnd)
        return if (hi - lo >= MIN_SPAN_SEC) SelectorWindow(lo, hi) else widen(lo, dayStartSec)
    }

    /**
     * Scale the window by [zoom] (> 1 zooms in) about [focusFrac] (0..1 across the window) and shift it by
     * [panFrac] of the current span, then clamp the result to the day and to [MIN_SPAN_SEC].
     */
    fun transform(w: SelectorWindow, zoom: Float, focusFrac: Float, panFrac: Float, dayStartSec: Long): SelectorWindow {
        val newSpan = (w.spanSec / zoom.coerceIn(0.1f, 10f).toDouble())
            .toLong().coerceIn(MIN_SPAN_SEC, DAY_SEC)
        val focusSec = w.fromSec + (w.spanSec * focusFrac.coerceIn(0f, 1f)).toLong()
        var from = focusSec - (newSpan * focusFrac.coerceIn(0f, 1f)).toLong() - (w.spanSec * panFrac).toLong()
        from = from.coerceIn(dayStartSec, dayStartSec + DAY_SEC - newSpan)
        return SelectorWindow(from, from + newSpan)
    }

    private fun widen(lo: Long, dayStartSec: Long): SelectorWindow {
        val from = lo.coerceIn(dayStartSec, dayStartSec + DAY_SEC - MIN_SPAN_SEC)
        return SelectorWindow(from, from + MIN_SPAN_SEC)
    }
}
