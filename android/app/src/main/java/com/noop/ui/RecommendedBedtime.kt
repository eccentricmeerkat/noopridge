package com.noop.ui

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Tonight's recommended bedtime and the wake time it works back from, as minutes since local midnight. [paybackMin] is the
 * part of the time that repays sleep debt (0 when there is none, or the change is off).
 */
internal data class Bedtime(val bedMin: Int, val wakeMin: Int, val fromAlarm: Boolean, val paybackMin: Int = 0)

/**
 * BS-x2-2 item 11. Recommended bedtime = wake target minus tonight's sleep need, plus a debt payback.
 *
 * Debt payback (owner, 24 Nov 2026, [com.noop.metrics.V2Change.BEDTIME_DEBT_PAYBACK]): when you are in sleep debt, part of it
 * is added to the need, so a debt moves the time earlier. The part is [PAYBACK_SHARE] of the debt, at most [PAYBACK_CAP_MIN],
 * and nothing under [PAYBACK_MIN_DEBT_MIN] (the 'on target' band). It repays a debt over several nights instead of asking
 * for one very long one. The share and the cap are a judgement, not a published standard.
 *
 * The wake target is the alarm time when an alarm is set, otherwise the user's typical wake time over the last
 * [WINDOW_NIGHTS] nights. Fewer than [MIN_NIGHTS] nights, or no known need, gives null: the card says so rather than
 * showing a time that was made up.
 */
internal object RecommendedBedtime {
    const val MIN_NIGHTS = 4
    const val WINDOW_NIGHTS = 14
    const val PAYBACK_SHARE = 0.25
    const val PAYBACK_CAP_MIN = 60
    const val PAYBACK_MIN_DEBT_MIN = 10.0
    private const val DAY_MIN = 24 * 60

    /**
     * One wake clock time per night (the latest wake on each local day) from the sessions' end times, for the last
     * [WINDOW_NIGHTS] days before [nowSec]. Naps are the caller's to leave out.
     */
    fun wakeMinutes(endTsList: List<Long>, nowSec: Long, zone: ZoneId = ZoneId.systemDefault()): List<Int> {
        val from = nowSec - WINDOW_NIGHTS * 86_400L
        return endTsList.filter { it in from..nowSec }
            .map { Instant.ofEpochSecond(it).atZone(zone) }
            .groupBy { it.toLocalDate() }
            .map { (_, ends) -> ends.maxOf { it.toLocalTime().toSecondOfDay() } / 60 }
    }

    /**
     * The median wake time of day. Minutes are measured from noon so a wake shortly after midnight and one shortly before
     * it sit next to each other instead of 24 hours apart. Null under [MIN_NIGHTS] nights.
     */
    fun typicalWakeMin(wakeMinutes: List<Int>): Int? {
        if (wakeMinutes.size < MIN_NIGHTS) return null
        val shifted = wakeMinutes.map { Math.floorMod(it - 12 * 60, DAY_MIN) }.sorted()
        val n = shifted.size
        val mid = if (n % 2 == 1) shifted[n / 2].toDouble() else (shifted[n / 2 - 1] + shifted[n / 2]) / 2.0
        return Math.floorMod(mid.roundToInt() + 12 * 60, DAY_MIN)
    }

    /** Bedtime for a wake time and a need, wrapping past midnight. */
    fun bedtimeMin(wakeMin: Int, needMin: Double): Int = Math.floorMod(wakeMin - needMin.roundToInt(), DAY_MIN)

    /** Minutes of [debtMin] to repay tonight: [PAYBACK_SHARE] of it, at most [PAYBACK_CAP_MIN], none below [PAYBACK_MIN_DEBT_MIN]. */
    fun paybackMin(debtMin: Double?): Int {
        val debt = debtMin?.takeIf { it >= PAYBACK_MIN_DEBT_MIN } ?: return 0
        return minOf(PAYBACK_CAP_MIN.toDouble(), debt * PAYBACK_SHARE).roundToInt()
    }

    fun compute(
        alarmWakeMin: Int?, typicalWakeMin: Int?, needHours: Double?, nights: Int,
        debtMin: Double? = null,
        paybackOn: Boolean = com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.BEDTIME_DEBT_PAYBACK),
    ): Bedtime? {
        if (nights < MIN_NIGHTS) return null
        val need = needHours?.takeIf { it > 0.0 } ?: return null
        val wake = alarmWakeMin ?: typicalWakeMin ?: return null
        val payback = if (paybackOn) paybackMin(debtMin) else 0
        return Bedtime(bedtimeMin(wake, need * 60.0 + payback), wake, alarmWakeMin != null, payback)
    }
}
