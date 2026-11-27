package com.noop.ui

import java.time.LocalDate

/**
 * True when [points] holds any reading up to [today]. Fix 5: any reading at all, not just the last 90 days, because the chart
 * widens to show older readings and a row tagged "No data" over a chart that has data read as a bug (Blood oxygen).
 */
internal fun hasRecentData(points: List<SeriesPoint>, today: LocalDate): Boolean {
    val to = today.toString()
    return points.any { it.day <= to && it.value.isFinite() }
}

/**
 * The metric keys tagged "No data" (Insights' picker and the Key Metrics editor): [load] reads each metric's series (the same
 * reader the chart uses), and a metric with no reading at all is returned. A read that throws counts as no data.
 */
internal suspend fun metricsWithoutRecentData(
    keys: List<String>,
    today: LocalDate,
    load: suspend (String) -> List<SeriesPoint>,
): Set<String> = keys.filterTo(LinkedHashSet()) { key ->
    !hasRecentData(runCatching { load(key) }.getOrDefault(emptyList()), today)
}
