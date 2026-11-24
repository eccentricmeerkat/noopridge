package com.noop.ui

import java.util.TimeZone

/**
 * The visible slice of the night on the Sleep stages chart (BS-x2-2 item 10). Values are seconds from the
 * night's onset, the same axis [StageInterval] uses.
 */
internal data class StageWindow(val fromSec: Double, val toSec: Double) {
    val spanSec: Double get() = toSec - fromSec
}

/** What the scrub tooltip shows: the stage under the finger, its run, and the time pointed at. */
internal data class StageTip(val stage: String, val startSec: Double, val endSec: Double, val atSec: Double) {
    val durationSec: Double get() = endSec - startSec
}

internal object SleepStageWindows {

    /** About 3.5 h on screen at the default zoom. */
    const val VISIBLE_SEC = 3.5 * 3600.0

    /** Fix 5: every night is shown whole (the chart no longer pans); no single night is longer than a day. */
    const val NO_SCROLL_MAX_SEC = 24.0 * 3600.0

    fun scrollable(nightSec: Double): Boolean = nightSec > NO_SCROLL_MAX_SEC

    /** The whole night when it is short, otherwise the first [VISIBLE_SEC]. */
    fun default(nightSec: Double): StageWindow =
        if (!scrollable(nightSec)) StageWindow(0.0, nightSec.coerceAtLeast(1.0)) else StageWindow(0.0, VISIBLE_SEC)

    /** Move the window by [deltaSec] (positive = later), staying inside [0, nightSec]. */
    fun pan(w: StageWindow, deltaSec: Double, nightSec: Double): StageWindow {
        if (!scrollable(nightSec)) return default(nightSec)
        val span = w.spanSec
        val from = (w.fromSec + deltaSec).coerceIn(0.0, (nightSec - span).coerceAtLeast(0.0))
        return StageWindow(from, from + span)
    }

    fun secAtX(xPx: Float, widthPx: Float, w: StageWindow): Double {
        if (widthPx <= 0f) return w.fromSec
        return w.fromSec + (xPx / widthPx).coerceIn(0f, 1f) * w.spanSec
    }

    fun xOfSec(sec: Double, widthPx: Float, w: StageWindow): Float =
        (widthPx * ((sec - w.fromSec) / w.spanSec)).toFloat()

    /** The run that contains [sec], or null in a gap the stager left unlabelled (no stage is borrowed from a neighbour). */
    fun tipAt(intervals: List<StageInterval>, sec: Double): StageTip? =
        intervals.firstOrNull { sec >= it.startSec && sec <= it.endSec }
            ?.let { StageTip(canonicalStage(it.stage), it.startSec, it.endSec, sec) }

    /** Whole local-hour marks inside [w] as (fraction across the window, unix seconds); every second hour over five hours so labels never crowd. */
    fun hourMarks(onsetTs: Long, w: StageWindow): List<Pair<Float, Long>> {
        val first = onsetTs + w.fromSec.toLong()
        val last = onsetTs + w.toSec.toLong()
        val offset = TimeZone.getDefault().getOffset(first * 1000L) / 1000L
        val step = if (w.spanSec > 5.0 * 3600.0) 7200L else 3600L
        var t = (Math.floorDiv(first + offset, step) + 1L) * step - offset
        val out = ArrayList<Pair<Float, Long>>()
        while (t <= last) {
            out.add((((t - onsetTs) - w.fromSec) / w.spanSec).toFloat() to t)
            t += step
        }
        return out
    }
}
