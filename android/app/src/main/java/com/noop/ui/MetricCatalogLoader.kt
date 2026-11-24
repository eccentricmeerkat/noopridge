package com.noop.ui

import com.noop.NoopApplication
import com.noop.data.DailyMetric
import com.noop.data.WhoopRepository
import com.noop.metrics.DayKey
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.metrics.loadV10MetricInputs

// B2-X: reads a catalogue entry's daily series. One reader for Insights and Today, so a metric shows the same line in both.

/**
 * The entry's series, oldest to newest, in its display unit. [days] is the merged daily history; [inputs] loads the
 * metric registry's inputs once (null-safe) for registry-backed entries; [imperial] converts weight (stored in kg) to lb.
 */
internal suspend fun CatalogEntry.loadSeries(
    vm: AppViewModel,
    days: List<DailyMetric>,
    inputs: suspend () -> V10MetricInputs,
    imperial: Boolean = false,
): List<SeriesPoint> = when (val src = source) {
    is CatSource.Daily -> days.mapNotNull { d -> src.pick(d)?.takeIf { it.isFinite() }?.let { SeriesPoint(d.day, it) } }
    is CatSource.Registry -> runCatching {
        val i = inputs()
        val first = days.minOfOrNull { it.day }?.let { DayKey.of(it) } ?: DayKey(i.today.minusDays(180))
        V10MetricRegistry(i).series(src.id, first, DayKey(i.today))
            .mapNotNull { mv -> mv.value?.takeIf { it.isFinite() }?.let { SeriesPoint(mv.day.toString(), it * src.scale) } }
    }.getOrDefault(emptyList())
    is CatSource.Series -> runCatching {
        val byDay = java.util.TreeMap<String, Double>()
        for (device in src.devices) {
            val ids = if (device == null) WhoopRepository.importedSourceIdsFor(vm.activeStrapId) else listOf(device)
            for (id in ids) for (r in vm.repo.metricSeries(id, src.key, "0000-00-00", "9999-99-99")) {
                if (r.value.isFinite()) byDay.putIfAbsent(r.day, r.value)
            }
        }
        byDay.map { SeriesPoint(it.key, it.value) }
    }.getOrDefault(emptyList())
    is CatSource.HrDaily -> if (!com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.HR_DAILY_STATS)) emptyList() else runCatching {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        // Bounded: ten years of 5-minute buckets is a million rows; 400 days covers every range but 'All' on a long history.
        val earliest = today.minusDays(HR_DAILY_MAX_DAYS)
        val first = days.minOfOrNull { it.day }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?.let { if (it.isBefore(earliest)) earliest else it } ?: earliest
        val buckets = vm.repo.hrBucketsUnion(
            vm.activeStrapId, first.atStartOfDay(zone).toEpochSecond(), java.time.Instant.now().epochSecond, 300L,
        )
        dailyHrStats(buckets, src.stat, zone)
    }.getOrDefault(emptyList())
    CatSource.WorkoutKcal -> runCatching {
        val now = java.time.Instant.now().epochSecond
        CalorieMetrics.workoutKcalByDay(vm.repo.workoutsAllSources(vm.deviceId, 0L, now))
            .toSortedMap().map { SeriesPoint(it.key, it.value) }
    }.getOrDefault(emptyList())
    CatSource.HcActive -> runCatching {
        CalorieMetrics.hcActiveByDay(vm.repo.appleDaily("health-connect", "0000-01-01", "9999-12-31"))
            .toSortedMap().map { SeriesPoint(it.key, it.value) }
    }.getOrDefault(emptyList())
    CatSource.Weight -> runCatching {
        // Health Connect first: on Android it is the live source, Apple Health an export.
        val byDay = java.util.TreeMap<String, Double>()
        for (device in listOf("health-connect", "apple-health")) {
            for (r in vm.repo.appleDaily(device, "0000-01-01", "9999-12-31")) {
                r.weightKg?.takeIf { it.isFinite() && it > 0 }?.let { byDay.putIfAbsent(r.day, it) }
            }
        }
        byDay.map { SeriesPoint(it.key, if (imperial) UnitFormatter.kgToPounds(it.value) else it.value) }
    }.getOrDefault(emptyList())
}

/** The registry's inputs for the running app (cached by the caller). */
internal suspend fun registryInputsFor(vm: AppViewModel): V10MetricInputs =
    loadV10MetricInputs(vm.getApplication<NoopApplication>())

/** The newest imported body weight in kg (Health Connect or Apple Health), or null when none was imported. */
internal suspend fun latestImportedWeightKg(vm: AppViewModel): Double? = runCatching {
    latestWeightKg(
        vm.repo.appleDaily("apple-health", "0000-01-01", "9999-12-31"),
        vm.repo.appleDaily("health-connect", "0000-01-01", "9999-12-31"),
    )
}.getOrNull()
