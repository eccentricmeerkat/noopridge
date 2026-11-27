package com.noop.ui

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * RS-3b: Readiness, which replaces Synthesis (owner, 26 Nov 2026).
 *
 *   Morning readiness = Charge^0.80 × Rest^0.20
 *   Readiness now     = Morning readiness × (1 − 0.5 × min(1, Effort / Effort target))
 *
 * All on 0–100. The formula is the owner's, who chose the weights from a comparison over recorded history (65/35 was the first
 * proposal). They are NOOP's starting values and are not validated against any outcome. The categories are Synthesis's
 * five, judged on the rounded score, so a card that reads "70" is never filed under the band below 70.
 */
internal object ReadinessScore {
    const val CHARGE_WEIGHT = 0.80
    const val REST_WEIGHT = 1.0 - CHARGE_WEIGHT
    /** The most Effort can take off the morning score: half, once the target is reached. */
    const val EFFORT_CAP = 0.50

    fun morning(charge: Double, rest: Double): Double =
        charge.coerceIn(0.0, 100.0).pow(CHARGE_WEIGHT) * rest.coerceIn(0.0, 100.0).pow(REST_WEIGHT)

    /** Today's plan's Effort target on 0–100 ([planEffortCeiling]'s figure): the top of Charge's optimal band. */
    fun effortTarget(charge: Double): Int? = optimalStrainRange(charge)?.let { (it.high * 100.0 / 21.0).roundToInt() }

    fun now(morning: Double, effort: Double, target: Int): Double =
        morning * (1.0 - EFFORT_CAP * min(1.0, effort.coerceAtLeast(0.0) / target))

    /** 0 = Depleted … 4 = Peak, over [SYNTHESIS_BAND_FLOORS]. */
    fun band(score: Double): Int = SYNTHESIS_BAND_FLOORS.indexOfLast { score.roundToInt() >= it }.coerceAtLeast(0)
}

/**
 * The "readiness now" last shown on [day]: its smoothed [value], the category it is held in, and the [morning] score it
 * was worked from. Kept so the next reading can ease from it.
 */
internal data class ReadinessShown(val day: String, val morning: Double, val value: Double, val band: Int, val atMs: Long)

/**
 * RS-3b smoothing (D3), so one noisy heart-rate reading doesn't flip the category. The shown value moves toward each new
 * reading with a half-life of [HALF_LIFE_MS], and the category changes only once the value is [BAND_MARGIN] points past an
 * edge. Both are starting guesses. A new day, a first reading, or a changed morning score (Charge or Rest arrived or was
 * re-scored) starts afresh at the reading itself: only the Effort part is eased.
 */
internal object ReadinessSmoothing {
    const val HALF_LIFE_MS = 15L * 60L * 1000L
    const val BAND_MARGIN = 2.0
    private const val MORNING_CHANGED = 0.5

    fun step(previous: ReadinessShown?, day: String, morning: Double, reading: Double, nowMs: Long): ReadinessShown {
        if (previous == null || previous.day != day || kotlin.math.abs(previous.morning - morning) > MORNING_CHANGED) {
            return ReadinessShown(day, morning, reading, ReadinessScore.band(reading), nowMs)
        }
        // A clock set backwards counts as no time passed.
        val elapsed = (nowMs - previous.atMs).coerceAtLeast(0L)
        val moved = 1.0 - 0.5.pow(elapsed.toDouble() / HALF_LIFE_MS)
        val value = previous.value + (reading - previous.value) * moved
        return ReadinessShown(day, morning, value, holdBand(previous.band, value), nowMs)
    }

    fun holdBand(held: Int, value: Double): Int {
        var b = held
        while (b < SYNTHESIS_BAND_FLOORS.lastIndex && value >= SYNTHESIS_BAND_FLOORS[b + 1] + BAND_MARGIN) b++
        while (b > 0 && value < SYNTHESIS_BAND_FLOORS[b] - BAND_MARGIN) b--
        return b
    }
}

/** What the Today card has to say. */
internal sealed class ReadinessCardModel {
    /** Charge is still learning the baseline: [nights] done. */
    data class Calibrating(val nights: Int) : ReadinessCardModel()
    object NoCharge : ReadinessCardModel()
    object NoRest : ReadinessCardModel()
    /** A past day, or ([carriedFrom] set) last night's morning figure carried onto today before tonight is scored. */
    data class Morning(val charge: Double, val rest: Double, val morning: Double, val carriedFrom: String? = null) : ReadinessCardModel()
    /** Today, scored. [effort] is null until any Effort is known; [target] is the plan's. */
    data class Live(val charge: Double, val rest: Double, val morning: Double, val effort: Double?, val target: Int, val reading: Double) :
        ReadinessCardModel()
}

/** RS-3f: what the opened card names as the cause of its category. */
internal enum class ReadinessReason {
    LOW_CHARGE, LOW_REST, LOW_BOTH, MODERATE_CHARGE, LOWER_REST, HIGH_CHARGE, HIGH_BOTH, GOOD_REST, SOLID_BOTH, EFFORT,
}

/**
 * RS-3f: the cause of [band], from the whole numbers the orbs show. A category below the morning's can only be Effort's
 * doing. Otherwise Charge is named first, as it carries most of the weight; Rest is named when it is what stands out.
 */
internal fun readinessReason(charge: Double, rest: Double, morning: Double, band: Int): ReadinessReason {
    if (band < ReadinessScore.band(morning)) return ReadinessReason.EFFORT
    val c = charge.roundToInt()
    val r = rest.roundToInt()
    return when {
        band <= 1 -> when {
            c < 50 && r < 50 -> ReadinessReason.LOW_BOTH
            c < 50 -> ReadinessReason.LOW_CHARGE
            r < 50 -> ReadinessReason.LOW_REST
            // Only a rounding edge reaches here: name the weaker of the two.
            else -> if (c <= r) ReadinessReason.LOW_CHARGE else ReadinessReason.LOW_REST
        }
        band == 2 -> when {
            c < 50 -> ReadinessReason.LOW_CHARGE
            c < 70 -> ReadinessReason.MODERATE_CHARGE
            else -> ReadinessReason.LOWER_REST
        }
        c >= 70 -> if (r >= 85) ReadinessReason.HIGH_BOTH else ReadinessReason.HIGH_CHARGE
        else -> if (r >= 85) ReadinessReason.GOOD_REST else ReadinessReason.SOLID_BOTH
    }
}

/** The card's state from the same Charge, Rest and Effort the orbs show. No missing input is ever guessed. */
internal fun readinessCardModel(
    charge: Double?,
    chargeCarriedFrom: String?,
    rest: Double?,
    effort: Double?,
    calibratingNights: Int?,
    isToday: Boolean,
): ReadinessCardModel {
    if (calibratingNights != null) return ReadinessCardModel.Calibrating(calibratingNights)
    if (charge == null) return ReadinessCardModel.NoCharge
    if (rest == null) return ReadinessCardModel.NoRest
    val morning = ReadinessScore.morning(charge, rest)
    if (!isToday || chargeCarriedFrom != null) return ReadinessCardModel.Morning(charge, rest, morning, chargeCarriedFrom.takeIf { isToday })
    val target = ReadinessScore.effortTarget(charge) ?: return ReadinessCardModel.NoCharge
    return ReadinessCardModel.Live(charge, rest, morning, effort, target, ReadinessScore.now(morning, effort ?: 0.0, target))
}
