package com.noop.ui

import com.noop.metrics.MetricsV2
import com.noop.metrics.V2Change
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * RS-1-2 item 5 (Sleep.dc.html, Hours vs Needed): tonight's Sleep needed = Base sleep + Recent Effort + Sleep debt.
 *
 *  - Base sleep: the one need the page already uses (the newest of the tiles model's need series). Unchanged.
 *  - Sleep debt: RecommendedBedtime.paybackMin of the same debt the Sleep debt card headlines, so this page and the
 *    Recommended bedtime always agree (a quarter of the debt, at most an hour; Kitamura et al. 2016, Sci Rep 6:35812:
 *    about 1 h of debt takes about four days to recover). Behind V2Change.SLEEP_NEED_DEBT_PART.
 *  - Recent Effort: 20 min for each SD today's Effort sits above the mean of the previous 30 days, 0 to 60 min. No
 *    published dose-response exists (Youngstedt et al. 1997; Kredlow et al. 2015; Knufinke et al. 2018 found none in
 *    elite athletes), so the size is a NOOP heuristic. It changes only the Sleep needed figure on the Sleep page: Rest,
 *    debt, Today's plan and the bedtime keep reading Base. Behind V2Change.SLEEP_NEED_EFFORT_PART.
 *
 * Sources and reasoning: docs/ridge-build/BAU Sprints/RS-1/RS-1_SPRINT_PLAN.md, item 5.
 */

/** Tonight's need in its three parts, in minutes. [effortMin] is null when there is too little Effort history to say. */
internal data class SleepNeedSplit(val baseMin: Double, val effortMin: Int?, val debtMin: Int) {
    val totalMin: Double get() = baseMin + (effortMin ?: 0) + debtMin
}

internal object SleepNeedParts {
    const val EFFORT_MIN_PER_SD = 20.0
    const val EFFORT_CAP_MIN = 60
    const val EFFORT_WINDOW_DAYS = 30
    const val EFFORT_MIN_DAYS = 14

    /**
     * Minutes Recent Effort adds tonight: [EFFORT_MIN_PER_SD] per sample SD that [today] sits above the mean of [history]
     * (the previous [EFFORT_WINDOW_DAYS] days' Effort, today excluded), between 0 and [EFFORT_CAP_MIN]. Null with no
     * Effort today or fewer than [EFFORT_MIN_DAYS] days of history. A flat history (SD 0) adds nothing.
     */
    fun effortPartMin(today: Double?, history: List<Double>): Int? {
        if (today == null || history.size < EFFORT_MIN_DAYS) return null
        val mean = history.average()
        val sd = sqrt(history.sumOf { (it - mean) * (it - mean) } / (history.size - 1))
        if (sd <= 0.0) return 0
        val z = (today - mean) / sd
        return (EFFORT_MIN_PER_SD * z).coerceIn(0.0, EFFORT_CAP_MIN.toDouble()).roundToInt()
    }

    /** Today's Effort and the previous [EFFORT_WINDOW_DAYS] days' values from the daily rows (`strain` is Effort). */
    fun effortInputs(days: List<com.noop.data.DailyMetric>, today: LocalDate): Pair<Double?, List<Double>> {
        val byDay = days.associate { it.day to it.strain }
        val todayEffort = byDay[today.toString()]
        val history = (1..EFFORT_WINDOW_DAYS).mapNotNull { back -> byDay[today.minusDays(back.toLong()).toString()] }
        return todayEffort to history
    }

    /**
     * The split for tonight, or null without a Base need. With both changes off it is Base alone, so the card reads
     * exactly as before (the Effort row shows "—" and the debt row 0).
     */
    fun split(
        baseHours: Double?,
        debtMin: Double?,
        todayEffort: Double?,
        effortHistory: List<Double>,
        debtOn: Boolean = MetricsV2.on(V2Change.SLEEP_NEED_DEBT_PART),
        effortOn: Boolean = MetricsV2.on(V2Change.SLEEP_NEED_EFFORT_PART),
    ): SleepNeedSplit? {
        val base = baseHours?.takeIf { it > 0.0 } ?: return null
        return SleepNeedSplit(
            baseMin = base * 60.0,
            effortMin = if (effortOn) effortPartMin(todayEffort, effortHistory) else null,
            debtMin = if (debtOn) RecommendedBedtime.paybackMin(debtMin) else 0,
        )
    }
}

/** "8h 06m" (the board's hours with two-digit minutes); [signed] gives "+0h 54m". */
internal fun hoursMinutesPadded(minutes: Double, signed: Boolean = false): String {
    val m = minutes.roundToInt().coerceAtLeast(0)
    return (if (signed) "+" else "") + "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
}

/** The Hours of sleep bar's fill, 0 to 1 (a night past the need fills the track; the label keeps the real %). */
internal fun hoursOfSleepFill(sleptMin: Double, needMin: Double): Float =
    if (needMin <= 0.0) 0f else (sleptMin / needMin).coerceIn(0.0, 1.0).toFloat()

/**
 * The Sleep page's split for tonight from the screen's own inputs: the debt the Sleep debt card headlines (the latest
 * tracked night's rolling shortfall, the one the bedtime's payback also reads) and today's Effort against the previous
 * 30 days. One function, so the Hours vs Needed card and the Hours asleep need line always show the same total.
 */
internal fun ridgeSleepNeedSplit(days: List<com.noop.data.DailyMetric>, m: SleepModel?, today: LocalDate = LocalDate.now()): SleepNeedSplit? {
    val (todayEffort, effortHistory) = SleepNeedParts.effortInputs(days, today)
    return SleepNeedParts.split(m?.trendNeedHours?.lastOrNull(), ridgeDebtNow(days, m), todayEffort, effortHistory)
}

/** The latest tracked night's rolling sleep-debt shortfall in minutes, as the Sleep debt card headlines it. */
internal fun ridgeDebtNow(days: List<com.noop.data.DailyMetric>, m: SleepModel?): Double? {
    val last = days.lastOrNull()?.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    m ?: return null
    return buildDebtChart(m.debtByDay, days.filter { (it.totalSleepMin ?: 0.0) > 0.0 }.map { it.day }.toSet(), last).lastNight
}

