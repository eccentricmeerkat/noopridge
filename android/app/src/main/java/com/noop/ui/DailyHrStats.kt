package com.noop.ui

import com.noop.data.HrBucket
import java.time.Instant
import java.time.ZoneId

/** Which day statistic of the heart-rate samples a series shows. */
internal enum class HrDayStat { AVERAGE, MAX }

/** How far back the daily heart-rate series is read (days); a longer history is cut here to bound the read. */
internal const val HR_DAILY_MAX_DAYS = 400L

/** A day needs this many 5-minute buckets (one hour of wear) before it gets a value, so a few stray samples cannot fabricate one. */
internal const val HR_DAILY_MIN_BUCKETS = 12

/**
 * One point per local day: the mean of the day's 5-minute bucket means ([HrDayStat.AVERAGE]) or the day's highest sample
 * ([HrDayStat.MAX]). Days are the phone's local calendar days, the same boundaries the rest of Insights uses. Oldest first.
 */
internal fun dailyHrStats(buckets: List<HrBucket>, stat: HrDayStat, zone: ZoneId, minBuckets: Int = HR_DAILY_MIN_BUCKETS): List<SeriesPoint> =
    buckets.groupBy { Instant.ofEpochSecond(it.bucket).atZone(zone).toLocalDate() }
        .filterValues { it.size >= minBuckets }
        .toSortedMap()
        .map { (day, bs) ->
            val v = when (stat) {
                HrDayStat.AVERAGE -> bs.map { it.avgBpm }.average()
                HrDayStat.MAX -> bs.maxOf { it.maxBpm }
            }
            SeriesPoint(day.toString(), v)
        }
