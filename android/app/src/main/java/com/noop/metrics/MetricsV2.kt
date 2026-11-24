package com.noop.metrics

import android.content.Context
import com.noop.BuildConfig

/*
 * MetricsV2.kt — the `metricsV2` switch (R9a, 13-release-plan). R9a is the one release that changes numbers users
 * see; every correction is a [V2Change] that stays off until the product owner approves it line by line
 * (releases/R9a/R9a_PLAN.md). With every change off, each number is v10's, unchanged.
 */

/** One approved-or-pending correction, with the decision it implements. */
enum class V2Change(val decision: String, val summary: String) {
    ONE_SLEEP_NEED("S0-3", "One sleep need: personalizedNeedHours over 21 nights, with age"),
    PLAN_SLEEP_HALF("S0-3 · D-08", "Today's plan names tonight's sleep need"),
    SLEPT_ON_TARGET("D-14", "Slept on target: asleep ≥ that night's need"),
    NORMAL_BAND_60("D-05 · J-04", "Normal band: mean ± 1 SD of the last 60 tracked nights"),
    PAST_DAY_LOAD("#25 · J-03", "A past day's load ratio uses only rows up to that day"),
    REST_CARRY_BOUND("#20", "Today's Rest never carries a future-dated night"),
    NO_ASSUMED_AGE("#27 · J-05", "No age set means no age, not 30"),
    LOGICAL_DAY("J-02", "The 04:00 day follows the wall clock on DST mornings; Workouts' heatmap uses it"),
    CONSISTENCY_PER_NIGHT("R9c · #699", "Sleep consistency scores one bedtime per bridged night, not every block"),
    EXPLORE_CALENDAR_WINDOW("R9d · G-04", "Explore's W/M/3M… are calendar days back from the latest reading, not a count of readings"),
    NAP_PLAN_TARGET("SQ5-c", "Today's plan: tonight's sleep target less today's nap (asleep minutes)"),
    SPO2_ONE_SOURCE("BS-x2-2 · item 9", "Blood oxygen: the calibrated or imported value, else the strap estimate tagged Estimate, on every surface"),
    HR_DAILY_STATS("BS-x2-3 · item 18", "Insights: Average and Max heart rate are each day's mean and peak of the heart-rate samples (they had no data)"),
    BEDTIME_DEBT_PAYBACK("Owner, 24 Nov 2026", "Recommended bedtime adds part of your sleep debt: a quarter of it, up to an hour"),
    ;

    /** A change that only makes sense with another on: both read the one need. */
    val requires: V2Change?
        get() = when (this) {
            PLAN_SLEEP_HALF, SLEPT_ON_TARGET -> ONE_SLEEP_NEED
            NAP_PLAN_TARGET -> PLAN_SLEEP_HALF
            else -> null
        }
}

/**
 * Which [V2Change]s are on. Release builds use [APPROVED] only; debug builds also read the inspector's switches,
 * loaded once at process start ([load]), so a switch applies after the inspector restarts the app.
 */
object MetricsV2 {
    /** The changes the product owner approved, line by line (DECISIONS_LOG): R9a's eight, R9c's V2-9, R9d's V2-10. */
    val APPROVED: Set<V2Change> = setOf(
        V2Change.ONE_SLEEP_NEED, V2Change.PLAN_SLEEP_HALF, V2Change.SLEPT_ON_TARGET, V2Change.NORMAL_BAND_60,
        V2Change.PAST_DAY_LOAD, V2Change.REST_CARRY_BOUND, V2Change.NO_ASSUMED_AGE, V2Change.LOGICAL_DAY,
        V2Change.CONSISTENCY_PER_NIGHT, V2Change.EXPLORE_CALENDAR_WINDOW,
        // BS-x2-2 item 9: the owner chose option C (21 Nov 2026). Remove this line to turn the change off.
        V2Change.SPO2_ONE_SOURCE,
        // BS-x2-3 item 18: Average and Max heart rate had no source at all; added on the same footing. Remove to turn off.
        V2Change.HR_DAILY_STATS,
        // Recommended bedtime repays part of the sleep debt: the owner asked for it (24 Nov 2026). Remove to turn it off.
        V2Change.BEDTIME_DEBT_PAYBACK,
    )

    @Volatile
    var active: Set<V2Change> = APPROVED
        private set

    fun on(change: V2Change): Boolean = isOn(change, active)

    /** [change] is on in [set] when it, and the change it requires, are both in it. */
    fun isOn(change: V2Change, set: Set<V2Change>): Boolean =
        change in set && change.requires.let { it == null || it in set }

    private const val PREFS = "metrics_v2"

    /** Read the debug switches (no-op in release builds). Called from `NoopApplication.onCreate`. */
    fun load(context: Context) {
        if (!BuildConfig.DEBUG) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        active = V2Change.entries.filterTo(mutableSetOf()) { prefs.getBoolean(it.name, it in APPROVED) }
    }

    /** Debug inspector: save one switch. The caller restarts the app so every screen reads it. */
    fun save(context: Context, change: V2Change, on: Boolean) {
        if (!BuildConfig.DEBUG) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(change.name, on).commit()
        active = if (on) active + change else active - change
    }

    /** Tests: set the active changes directly. Pair with [resetForTest] in an `@After`, since this is process state. */
    internal fun setForTest(changes: Set<V2Change>) {
        active = changes
    }

    /** Tests: back to what a release build runs ([APPROVED]). */
    internal fun resetForTest() {
        active = APPROVED
    }
}
