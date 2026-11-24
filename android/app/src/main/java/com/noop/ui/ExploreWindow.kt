package com.noop.ui

import com.noop.data.WhoopRepository
import com.noop.metrics.MetricsV2
import com.noop.metrics.V2Change

/*
 * ExploreWindow.kt — Explore's window ranges and the sparse-widen rule, kept free of Compose and Android statics so they
 * are tested on the JVM (R9d, G-04). Moved verbatim from TrendsExploreScreen.kt, plus the V2-10 calendar window.
 */

// MARK: - Window range (W / M / 3M / 6M / 1Y / ALL)

internal enum class ExploreRange(val days: Int?, val label: String, val windowName: String) {
    Week(7, "W", "week"),
    Month(30, "M", "month"),
    Quarter(90, "3M", "quarter"),
    Half(180, "6M", "6 months"),
    Year(365, "1Y", "year"),
    All(null, "ALL", "all time");

    /** This range plus every larger range, ascending , the auto-widen search order. */
    val widening: List<ExploreRange>
        get() = entries.dropWhile { it != this }

    /** True when this range can reach PAST the bounded dashboard cap (WhoopRepository.RECENT_DAYS_CAP),
     *  so the built-in series must come from the UNCAPPED full history rather than the bounded `recentDays`
     *  flow to avoid silently truncating a deep import (#797 Explore-'All' follow-up). Only the open-ended
     *  'All' range ([days] == null) does; every fixed range is ≤ the cap, so it keeps using the cheap flow. */
    val reachesDeep: Boolean get() = days == null || days > WhoopRepository.RECENT_DAYS_CAP
}

// MARK: - A loaded series point (day string + value), oldest first.

internal data class SeriesPoint(val day: String, val value: Double)

/** The readings in [range]'s window, relative to the latest reading (the series is sorted ascending by day). v10 took
 *  the trailing N readings, close to N days only for a one-row-per-day series; with [calendar] (V2-10) the window is
 *  the N calendar days ending on the latest reading. */
internal fun List<SeriesPoint>.windowFor(
    range: ExploreRange,
    calendar: Boolean = MetricsV2.on(V2Change.EXPLORE_CALENDAR_WINDOW),
): List<SeriesPoint> {
    val days = range.days ?: return this
    if (isEmpty()) return emptyList()
    if (!calendar) return takeLast(days)
    // R9d (G-04, V2-10): the window is the calendar days ending on the latest reading. Counting readings made "M" the
    // last 30 readings, 30 weeks of a weekly series, and a non-empty series could never widen.
    val from = java.time.LocalDate.parse(last().day).minusDays((days - 1).toLong()).toString()
    return filter { it.day >= from }
}

/**
 * The range Explore shows (macOS sparse-widen rule, G-04): [range] if its window holds enough readings, else the smallest
 * larger range that does, else All. v10 widened only an EMPTY window, which a window counted back from the latest
 * reading never is; with V2-10 a window needs two readings (a trend), so a weekly series on W widens to M. Pure.
 */
internal fun exploreEffectiveRange(
    series: List<SeriesPoint>,
    range: ExploreRange,
    calendar: Boolean = MetricsV2.on(V2Change.EXPLORE_CALENDAR_WINDOW),
): ExploreRange {
    if (series.isEmpty()) return range
    val needed = if (calendar) minOf(2, series.size) else 1
    return range.widening.firstOrNull { series.windowFor(it, calendar).size >= needed } ?: ExploreRange.All
}
