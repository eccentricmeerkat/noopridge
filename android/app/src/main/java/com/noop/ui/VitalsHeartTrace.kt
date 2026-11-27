package com.noop.ui

/*
 * RS-1-1 item 4 (ref_vitals_heart_chart.png): Vital signs' heart card draws a heartbeat trace, not the live readings
 * (owner, 25 Nov 2026). The trace is decoration: the bpm numeral beside it is the reading. Pure, so the shape and the
 * three card states are tested without a canvas.
 */

/** The heart card's three states, in the order they are decided. */
internal enum class HeartCardState { LIVE, WAITING, NOT_CONNECTED }

/** No link is Not connected; a link with no bpm yet is Waiting; a link with a bpm is Live. */
internal fun heartCardState(connected: Boolean, hr: Int?): HeartCardState = when {
    !connected -> HeartCardState.NOT_CONNECTED
    hr == null -> HeartCardState.WAITING
    else -> HeartCardState.LIVE
}

/** One beat slides out at the left and a new one enters at the right every this many ms. */
internal const val HEART_TRACE_PERIOD_MS = 2_000

/** Beats across the card's width, as the reference shows. */
internal const val HEART_TRACE_BEATS = 3

/** A point of the trace: [x] and [y] are fractions of one beat's width and the trace's height (0 is the top). */
internal data class TracePoint(val x: Float, val y: Float)

/**
 * One beat on a flat baseline: a small dip, a tall spike, a deep dip, a small recovery bump, then flat again. The
 * first and last points sit on the baseline at x 0 and 1, so beats join seamlessly when repeated.
 */
internal val HEART_BEAT: List<TracePoint> = listOf(
    TracePoint(0.00f, 0.50f),
    TracePoint(0.40f, 0.50f),
    TracePoint(0.44f, 0.58f),
    TracePoint(0.48f, 0.06f),
    TracePoint(0.53f, 0.94f),
    TracePoint(0.57f, 0.40f),
    TracePoint(0.61f, 0.56f),
    TracePoint(0.65f, 0.50f),
    TracePoint(1.00f, 0.50f),
)

/**
 * The trace's points as fractions of the whole width (0 to 1) and height, scrolled left by [phase] beats (0 to 1, one
 * beat per [HEART_TRACE_PERIOD_MS]). One beat more than [beats] is laid out so the right edge is never empty while
 * the trace slides; points past either edge are clipped by the canvas.
 */
internal fun heartTrace(phase: Float, beats: Int = HEART_TRACE_BEATS): List<TracePoint> {
    val p = phase - kotlin.math.floor(phase)
    val beatW = 1f / beats
    val out = ArrayList<TracePoint>((beats + 1) * HEART_BEAT.size)
    for (b in 0..beats) {
        val start = (b - p) * beatW
        HEART_BEAT.forEachIndexed { i, pt ->
            // The first point of each later beat repeats the previous beat's last point; skip it.
            if (b > 0 && i == 0) return@forEachIndexed
            out += TracePoint(start + pt.x * beatW, pt.y)
        }
    }
    return out
}
