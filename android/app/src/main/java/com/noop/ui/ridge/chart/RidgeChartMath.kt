package com.noop.ui.ridge.chart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The pure half of the Ridge chart primitives (R-01, rules R1–R8 in `05-chart-catalogue.md`): value axes, gaps,
 * the rolling mean, the scrub hit test and the sleep stage stack. Nothing here draws or reads storage, so every
 * rule is unit-tested without a device. Values arrive one per logical day, oldest first, `null` for an untracked
 * day (R3: a gap, never a zero).
 */

/** A value axis: the drawn domain [lo]..[hi] and the labelled [ticks] inside it (R1: at least three). */
data class Axis(val lo: Double, val hi: Double, val ticks: List<Double>) {
    val span: Double get() = (hi - lo).takeIf { it > 0.0 } ?: 1.0

    /** [v]'s height fraction in the domain, 0 at [lo] and 1 at [hi], clamped. */
    fun frac(v: Double): Float = ((v - lo) / span).toFloat().coerceIn(0f, 1f)
}

/**
 * The heart-health axis (§6.5): from the lower of the points and the band to the higher, padded 10%, widened
 * symmetrically to [minSpan], then snapped outward to [step] with [tickCount] even ticks on whole steps. With fewer than three
 * points the domain is the minimum span around their mean (or the band's middle), so one night still reads.
 */
fun heartAxis(
    points: List<Double>,
    band: ClosedFloatingPointRange<Double>?,
    minSpan: Double,
    step: Double,
    tickCount: Int = 4,
): Axis {
    val pool = points + listOfNotNull(band?.start, band?.endInclusive)
    var lo: Double
    var hi: Double
    if (points.size < 3) {
        val mid = when {
            points.isNotEmpty() -> points.average()
            band != null -> (band.start + band.endInclusive) / 2
            else -> 0.0
        }
        val half = maxOf(minSpan, band?.let { it.endInclusive - it.start } ?: 0.0) / 2
        lo = mid - half
        hi = mid + half
    } else {
        lo = pool.min()
        hi = pool.max()
        val pad = (hi - lo) * 0.10
        lo -= pad
        hi += pad
    }
    if (hi - lo < minSpan) {
        val mid = (lo + hi) / 2
        lo = mid - minSpan / 2
        hi = mid + minSpan / 2
    }
    lo = floor(lo / step) * step
    hi = ceil(hi / step) * step
    // Ticks fall on whole steps: the span grows to a multiple of (n - 1) steps, split evenly above and below.
    val n = tickCount.coerceAtLeast(3)
    val gaps = n - 1
    val tickStep = ceil((hi - lo) / step / gaps) * step
    val extra = tickStep * gaps - (hi - lo)
    lo -= floor(extra / step / 2) * step
    hi = lo + tickStep * gaps
    return Axis(lo, hi, (0 until n).map { lo + tickStep * it })
}

/**
 * The sleep-hours axis (§7.5 as amended in R4a): from 0, because a stacked bar on a truncated axis hides its bottom
 * stage and R1 allows truncation only for non-ratio values. The top is 9 h, raised for a longer night, rounded up to
 * a multiple of 3 so the ticks sit every 3 h.
 */
fun sleepHoursAxis(hours: List<Double>): Axis {
    val hi = ceil(maxOf(9.0, hours.maxOrNull() ?: 9.0) / 3.0) * 3.0
    return Axis(0.0, hi, generateSequence(0.0) { it + 3.0 }.takeWhile { it <= hi + 1e-9 }.toList())
}

/** A fixed 0–100 score axis (§2: Charge), ticked every 25. */
val scoreAxis: Axis = Axis(0.0, 100.0, listOf(0.0, 25.0, 50.0, 75.0, 100.0))

/**
 * RS-3e: Effort's axis, fitted to the data as Calories' is, so ordinary days fill the plot instead of its bottom third.
 * Values stay on 0–100; with [whoop] the ticks fall on whole numbers of the 0–21 scale. It starts at 0, and never tops out
 * below half the scale (10 of 21), so a quiet week does not look like a hard one.
 */
fun effortAxis(values: List<Double>, whoop: Boolean): Axis {
    val max = values.maxOrNull() ?: 0.0
    if (!whoop) {
        val top = (kotlin.math.ceil(max / 25.0) * 25.0).coerceIn(50.0, 100.0)
        return Axis(0.0, top, (0..(top / 25.0).toInt()).map { it * 25.0 })
    }
    val needed = max * 21.0 / 100.0
    val ticks = when {
        needed <= 10.0 -> listOf(0.0, 5.0, 10.0)
        needed <= 15.0 -> listOf(0.0, 5.0, 10.0, 15.0)
        else -> listOf(0.0, 7.0, 14.0, 21.0)
    }.map { it * 100.0 / 21.0 }
    return Axis(0.0, ticks.last(), ticks)
}

/**
 * The trailing [window]-day mean at each index of [values], or null where fewer than [minN] of those days are
 * tracked (§6.3: 4 of 7). Indices before the first full window still average what exists, so pass [lead] extra
 * leading days and the result is the slice after them, aligned with the visible window.
 */
fun rollingMean(values: List<Double?>, window: Int = 7, minN: Int = 4, lead: Int = 0): List<Double?> =
    values.indices.drop(lead).map { i ->
        val xs = (maxOf(0, i - window + 1)..i).mapNotNull { values[it] }
        if (xs.size >= minN) xs.average() else null
    }

/**
 * Index runs a line may join (R3): consecutive tracked indices, bridging at most [maxGap] untracked days, so a line
 * breaks across a gap longer than one day. A run of one index is kept; the caller draws it as a lone point.
 */
fun lineRuns(values: List<Double?>, maxGap: Int = 1): List<List<Int>> {
    val runs = mutableListOf<MutableList<Int>>()
    var lastTracked = -1
    for (i in values.indices) {
        if (values[i] == null) continue
        if (lastTracked < 0 || i - lastTracked - 1 > maxGap) runs.add(mutableListOf())
        runs.last().add(i)
        lastTracked = i
    }
    return runs
}

/**
 * The x fraction (0..1) of slot [i] of [n]: point charts put the first and last day on the edges, bar charts put
 * each bar in the middle of its slot.
 */
fun slotFrac(i: Int, n: Int, bars: Boolean): Float = when {
    n <= 1 -> 0.5f
    bars -> (i + 0.5f) / n
    else -> i.toFloat() / (n - 1)
}

/** The scrubbed index for a touch at [frac] across the plot (R2): the nearest slot, clamped to the data. */
fun scrubIndex(frac: Float, n: Int, bars: Boolean): Int {
    if (n <= 1) return 0
    val f = frac.coerceIn(0f, 1f)
    val raw = if (bars) floor(f * n).toInt() else (f * (n - 1)).roundToInt()
    return raw.coerceIn(0, n - 1)
}

/**
 * The x-axis label slots (R1/R8): up to [max] indices spread evenly from first to last, always both ends, never a
 * duplicate on a short window.
 */
fun axisLabelSlots(n: Int, max: Int = 5): List<Int> = when {
    n <= 0 -> emptyList()
    n == 1 -> listOf(0)
    else -> (0 until minOf(max, n)).map { q -> (q * (n - 1).toDouble() / (minOf(max, n) - 1)).roundToInt() }.distinct()
}

/** The percent change from [prior] to [current], whole, or null when either is missing or [prior] is zero. */
fun percentChange(current: Double?, prior: Double?): Int? {
    if (current == null || prior == null || prior == 0.0) return null
    return ((current - prior) / prior * 100).roundToInt()
}

/**
 * Whether [v] is an outlier (§6.8): beyond ±4 SD of the personal baseline. With no trusted baseline, nothing is.
 */
fun isOutlier(v: Double, baseline: Double?, sigma: Double?): Boolean =
    baseline != null && sigma != null && sigma > 0.0 && abs(v - baseline) > 4 * sigma

/** One night on the sleep-by-stage chart (§7). */
sealed interface SleepNight {
    /** No sleep recorded (R3): a dashed outline, "Not tracked". */
    data object Untracked : SleepNight

    /** Duration without stages: one neutral bar, "No stage data". */
    data class Unstaged(val asleepMin: Double) : SleepNight

    /** Light, Deep and REM minutes, scaled so they sum to [asleepMin] (Awake is not part of the bar). */
    data class Staged(val asleepMin: Double, val lightMin: Double, val deepMin: Double, val remMin: Double) : SleepNight
}

/**
 * Classify a night and scale its stages to its duration (§7.10: stages summing ≠ duration → stack scaled to
 * duration). The duration is [totalSleepMin] (time asleep) when stored, else the stage sum.
 */
fun sleepNight(totalSleepMin: Double?, lightMin: Double?, deepMin: Double?, remMin: Double?): SleepNight {
    val l = lightMin?.coerceAtLeast(0.0) ?: 0.0
    val d = deepMin?.coerceAtLeast(0.0) ?: 0.0
    val r = remMin?.coerceAtLeast(0.0) ?: 0.0
    val stageSum = l + d + r
    val asleep = totalSleepMin?.takeIf { it > 0.0 } ?: stageSum.takeIf { it > 0.0 } ?: return SleepNight.Untracked
    if (stageSum <= 0.0) return SleepNight.Unstaged(asleep)
    val k = asleep / stageSum
    return SleepNight.Staged(asleep, l * k, d * k, r * k)
}
