package com.noop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.noop.R
import com.noop.analytics.ReadinessEngine
import com.noop.data.DailyMetric
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - 14-day sparkline windows (built from recentDays)

/** The trailing-window series for each tile, oldest → newest. */
internal data class Window(
    val recovery: List<Double>,
    val strain: List<Double>,
    val sleepMin: List<Double>,
    val hrv: List<Double>,
    val rhr: List<Double>,
    val spo2: List<Double>,
    /** #1599: the strap-estimate trend, kept separate from [spo2] so the tile can swap rather than mix. */
    val spo2Candidate: List<Double> = emptyList(),
    val resp: List<Double>,
    // #616: the Steps tile carried no `spark` series, so it drew no trend line while every other tile did.
    // On-device DailyMetric.steps (the strap @57 count) — the same signal the Steps tile VALUE reads
    // (on-device-first), matching iOS kSparks "steps". (Calories is imported-first, so its spark is threaded
    // separately as caloriesSpark, not read off a DailyMetric column here.)
    val steps: List<Double>,
    // Added 2026-08-24 (queue 11c follow-up) for the new Skin Temp Key Metrics tile — matches the iOS
    // `sparks["skin_temp"]` sparkline added at the same time.
    val skinTemp: List<Double>,
)

/**
 * Build the trailing trend windows from `recentDays` over the chosen span (7 / 14 / 30 calendar days —
 * the editor's detailed-graph window). Each series drops null days from the trailing calendar window
 * only, so stale imports do not draw a current-day trend.
 */
@Composable
internal fun rememberTrendWindow(
    days: List<com.noop.data.DailyMetric>,
    anchorDay: LocalDate,
    windowDays: Int,
    // #1599: the SpO2 candidate per day, so the Blood Oxygen tile has a series to draw at all.
    // `AnalyticsEngine` writes `spo2Pct = null` on every computed day — a calibrated reading only ever
    // arrives from an IMPORT — so on a strap-only install `series { it.spo2Pct }` is empty by
    // construction and the tile rendered a value above blank space where every neighbour had a line.
    // Already empty when the display toggle is off (the flow returns emptyMap), so reading it here needs
    // no second gate.
    spo2Candidate: Map<String, Double> = emptyMap(),
): Window =
    androidx.compose.runtime.remember(days, anchorDay, windowDays, spo2Candidate) {
        // Trailing CALENDAR days ending today, NOT the last N stored rows, which on an old import
        // were months-old data shown as a fresh trend (issue #23). ISO yyyy-MM-dd sorts chronologically.
        val cutoff = anchorDay.minusDays((windowDays - 1).toLong()).toString()
        val end = anchorDay.toString()
        val recent = days.filter { it.day >= cutoff && it.day <= end }
        fun series(pick: (DailyMetric) -> Double?): List<Double> = recent.mapNotNull(pick)
        Window(
            recovery = series { it.recovery },
            strain = series { it.strain },
            sleepMin = series { it.totalSleepMin },
            hrv = series { it.avgHrv },
            rhr = series { it.restingHr?.toDouble() },
            spo2 = series { it.spo2Pct },
            // #1599: the strap estimate as its OWN series, not merged into the one above. The tile swaps
            // wholesale between them so a single caption can describe every point on the line.
            spo2Candidate = recent.mapNotNull { spo2Candidate[it.day] },
            resp = series { it.respRateBpm },
            steps = series { it.steps?.toDouble() },   // #616
            skinTemp = series { it.skinTempDevC },
        )
    }

// MARK: - Derived text (ported from TodayView.swift)

@Composable
internal fun greetingWord(): String {
    // BS-x2-1: the greeting follows the same device-clock slot as the Today photograph, so they never disagree.
    val slot by rememberHeroSlot()
    return uiString(heroGreetingRes(slot))
}

@Composable
internal fun synthesisWord(score: Double?): String {
    if (score == null) return uiString(R.string.today_no_data)
    // #1405: these are CHARGE/recovery-level words, a different axis from the ReadinessEngine training
    // verdict (Run down / Strained / Balanced / Primed). They must NOT share a word, or the Synthesis
    // card ("Steady") and the Charge-breakdown Readiness card ("Primed") read as the same thing
    // contradicting itself. So the [70,88) band is "Strong" (which also matches this card's own "Charge is
    // strong" detail copy), leaving "Primed" exclusively to the readiness engine. Keep parity with Swift.
    return when {
        score < 25 -> uiString(R.string.today_synthesis_depleted)
        score < 50 -> uiString(R.string.today_synthesis_low)
        score < 70 -> uiString(R.string.today_synthesis_steady)
        score < 88 -> uiString(R.string.today_synthesis_strong)
        else -> uiString(R.string.today_synthesis_peak)
    }
}

@Composable
internal fun synthesisDetail(d: DailyMetric?): String {
    val rec = d?.recovery
        ?: return uiString(R.string.today_synthesis_no_metrics)
    val res = when {
        rec < 50 && d.totalSleepMin == null -> R.string.today_synthesis_charge_low
        rec < 50 && d.totalSleepMin!! / 60.0 >= 7 -> R.string.today_synthesis_charge_low_sleep_consistent
        rec < 50 -> R.string.today_synthesis_charge_low_sleep_short
        rec < 70 && d.totalSleepMin == null -> R.string.today_synthesis_charge_steady
        rec < 70 && d.totalSleepMin!! / 60.0 >= 7 -> R.string.today_synthesis_charge_steady_sleep_consistent
        rec < 70 -> R.string.today_synthesis_charge_steady_sleep_short
        d.totalSleepMin == null -> R.string.today_synthesis_charge_strong
        d.totalSleepMin / 60.0 >= 7 -> R.string.today_synthesis_charge_strong_sleep_consistent
        else -> R.string.today_synthesis_charge_strong_sleep_short
    }
    return uiString(res)
}

internal fun sleepValue(d: DailyMetric?): String {
    val m = d?.totalSleepMin ?: return NO_DATA
    val total = m.roundToInt()
    return "${total / 60}h ${total % 60}m"
}

/**
 * The Rest tile's caption, hours-in-bed for the day, the figure that used to be the tile's VALUE
 * before #248 moved the Rest score there. Falls back to the efficiency read-out when no duration is
 * banked, and to null so the tile shows no caption line when neither exists. Mirrors macOS restCaption.
 */
private fun restCaption(d: DailyMetric?): String? = when {
    d?.totalSleepMin != null -> sleepValue(d)
    d?.efficiency != null -> String.format(Locale.getDefault(), "%.0f%% eff", d.efficiency)
    else -> null
}

/** Group-separated integer display from a Double (e.g. 12 345 steps), matching the Apple Health tiles. */
internal fun todayIntString(v: Double): String {
    val n = v.roundToInt()
    return if (kotlin.math.abs(n) >= 1000) String.format(Locale.getDefault(), "%,d", n) else "$n"
}

internal const val NO_DATA = "noop:no-data"

/** The dashboard-card placeholder for a baseline-relative metric (Stress) that is still seeding its window,  *  an honest "building your baseline" state rather than a bare dash (#706/#684). Rendered dimmed like NO_DATA. */
internal const val STRESS_CALIBRATING = "noop:calibrating"

@Composable
internal fun localizedMetricValue(value: String): String = when (value) {
    NO_DATA -> uiString(R.string.today_no_data)
    STRESS_CALIBRATING -> uiString(R.string.today_calibrating)
    else -> value
}

internal fun todayGrouped(value: Int): String =
    String.format(Locale.getDefault(), "%,d", value)

// MARK: - Key-Metrics layout editor (#251)
//
// A Today-local dialog (no new nav destination, another lane owns the nav graph) for choosing which
// Key-Metric tiles show on the Control Center and in what order. Display-only: it edits the persisted
// `today.keyMetrics` layout, never any stored metric. Uses the shared Shown / Hidden editor rows.

/** The Key-Metrics header's trailing label for the chosen detailed-graph window. */
@Composable
internal fun trendWindowLabel(days: Int): String = when (days) {
    7 -> uiString(R.string.today_trend_7_days)
    30 -> uiString(R.string.today_trend_30_days)
    else -> uiString(R.string.today_trend_14_days)
}

