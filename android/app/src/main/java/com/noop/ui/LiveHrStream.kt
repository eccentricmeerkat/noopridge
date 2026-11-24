package com.noop.ui

/** One point of the live heart-rate stream as fractions of the plot: x is 0 (oldest, left) to 1 (now, right), y is 0 (top, highest) to 1. */
internal data class StreamPoint(val x: Float, val y: Float)

/**
 * The live heart-rate stream (Fix 5): readings placed by when they arrived, the newest at the right edge and the older ones
 * further left, so as time passes the line slides right to left like a heart-rate monitor. Only the last [windowMs] shows.
 * The vertical scale fits the visible readings, never narrower than [minSpanBpm], and keeps a margin top and bottom.
 */
internal fun liveHrStream(
    samples: List<LiveHrSample>,
    nowMs: Long,
    windowMs: Long = 60_000L,
    minSpanBpm: Double = 16.0,
): List<StreamPoint> {
    val visible = samples.filter { it.timeMs in (nowMs - windowMs)..nowMs }
    if (visible.isEmpty()) return emptyList()
    val lo = visible.minOf { it.bpm }
    val hi = visible.maxOf { it.bpm }
    val span = maxOf(hi - lo, minSpanBpm)
    val mid = (hi + lo) / 2.0
    val bottom = mid - span / 2.0
    return visible.map { s ->
        val x = (1.0 - (nowMs - s.timeMs).toDouble() / windowMs).toFloat().coerceIn(0f, 1f)
        val y = (1.0 - (s.bpm - bottom) / span).toFloat().coerceIn(0.08f, 0.92f)
        StreamPoint(x, y)
    }
}
