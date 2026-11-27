package com.noop.ui

import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

/*
 * RS-3c: the Sleep score, a measure of how well a night went, beside Rest (owner, 26 Nov 2026; option A of the RS-3 plan).
 *
 *     Sleep score = 0.50 × Hours vs needed + 0.25 × Consistency + 0.25 × Efficiency        (each 0–100)
 *
 *  - Hours vs needed: asleep ÷ the need as it stood going into the night, capped at 100. The need is the Sleep page's
 *    Sleep needed: the one base need, plus the recent-Effort part, plus the sleep-debt payback (RS-1-2).
 *  - Consistency: how close the bed and wake times of this night and the three before are. Each spread (SD) is turned into
 *    0–100, a 120-minute spread scoring 0 (the app's bedtime scale), and the two are averaged. The night itself and at least
 *    two of the three before must be tracked.
 *  - Efficiency: share of time in bed asleep, as stored.
 *
 * The inputs follow the parts a third party names for its own sleep score, less sleep stress, which NOOP cannot measure.
 * No weights are published for it: these are NOOP's choice and are not validated. No sleep stages are used. It does not
 * feed Charge, Rest or Readiness. Apple's published split is kept as an alternative in the RS-3 sprint plan.
 */
object SleepScore {
    const val HOURS_WEIGHT = 0.50
    const val CONSISTENCY_WEIGHT = 0.25
    const val EFFICIENCY_WEIGHT = 0.25
    /** The bed or wake time spread that scores nothing. */
    const val SPREAD_ZERO_MIN = 120.0
    /** Nights before this one that consistency looks at, and how many of them must be tracked. */
    const val PRIOR_NIGHTS = 3
    const val PRIOR_NIGHTS_NEEDED = 2

    data class Parts(val hours: Double, val consistency: Double, val efficiency: Double) {
        val score: Double get() = HOURS_WEIGHT * hours + CONSISTENCY_WEIGHT * consistency + EFFICIENCY_WEIGHT * efficiency
    }

    /** One night's local clock times: minutes after noon to bed, minutes after 18:00 to wake, so neither wraps overnight. */
    data class BedWake(val bedMin: Double, val wakeMin: Double)

    fun bedWake(bedTs: Long, wakeTs: Long, zone: ZoneId): BedWake {
        fun after(ts: Long, anchorHour: Int): Double {
            val t = Instant.ofEpochSecond(ts).atZone(zone).toLocalTime()
            return Math.floorMod(t.hour * 60 + t.minute - anchorHour * 60, 1440).toDouble()
        }
        return BedWake(after(bedTs, 12), after(wakeTs, 18))
    }

    private fun spreadScore(minutes: List<Double>): Double {
        val mean = minutes.average()
        val sd = sqrt(minutes.sumOf { (it - mean) * (it - mean) } / minutes.size)
        return (100.0 * (1.0 - sd / SPREAD_ZERO_MIN)).coerceIn(0.0, 100.0)
    }

    /** [tonight] against the [prior] nights that are tracked; null with fewer than [PRIOR_NIGHTS_NEEDED] of them. */
    fun consistency(tonight: BedWake, prior: List<BedWake>): Double? {
        if (prior.size < PRIOR_NIGHTS_NEEDED) return null
        val nights = prior.take(PRIOR_NIGHTS) + tonight
        return (spreadScore(nights.map { it.bedMin }) + spreadScore(nights.map { it.wakeMin })) / 2.0
    }

    /** The three parts, or null when any input is missing: a part is never guessed or left out of the weights. */
    fun parts(asleepMin: Double?, needMin: Double?, efficiencyPct: Double?, consistency: Double?): Parts? {
        if (asleepMin == null || asleepMin <= 0.0 || needMin == null || needMin <= 0.0 || efficiencyPct == null || consistency == null) return null
        return Parts(
            hours = (asleepMin / needMin * 100.0).coerceIn(0.0, 100.0),
            consistency = consistency.coerceIn(0.0, 100.0),
            efficiency = efficiencyPct.coerceIn(0.0, 100.0),
        )
    }
}
