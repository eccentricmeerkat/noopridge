package com.noop.metrics

import java.time.LocalDate

/*
 * MetricRegistry.kt — the Ridge metric contract (Sprint 0 ticket S0-07, from
 * docs/ridge-build/sprint-2/SPRINT_2_PLAN.md §2).
 *
 * One read path for every number a screen shows: the value for a day, a gap-preserving series, the band a value
 * falls in, the personal normal range, and the definition that drives the "How your numbers work" copy. Sprint 0
 * ships only the contract plus [V10MetricRegistry], which delegates to the existing v10 code; no screen reads
 * through it yet.
 *
 * Rules (from the plan): [MetricRegistry.series] returns one entry per day, gaps included, so charts never fill
 * gaps themselves; screens must not compute means, bands or ratios of their own.
 */

/** The logical day a metric belongs to. */
@JvmInline
value class DayKey(val date: LocalDate) : Comparable<DayKey> {
    override fun compareTo(other: DayKey): Int = date.compareTo(other.date)
    override fun toString(): String = date.toString()

    companion object {
        /** Parse a stored `yyyy-MM-dd` day key. */
        fun of(key: String): DayKey = DayKey(LocalDate.parse(key))
    }
}

/**
 * Metrics the registry serves, one per v10 definition. Sprint 0 covered the daily values Today, Health and Trends
 * read directly; R1 (J-01) adds the rest of 06. Where v10 has several definitions of one idea, each has its own id
 * (the four sleep needs, M-NEED #8). Per-workout metrics (HRR, workout Effort, zone minutes) are not day-keyed and
 * are not here; they arrive with the workout page (R7b).
 */
enum class MetricId {
    CHARGE, EFFORT, REST, HRV, RESTING_HR, RESP_RATE, SPO2, SKIN_TEMP_DEV, SLEEP_DURATION, STEPS, STRESS,
    /** The need Rest scores against (pass 1, `personalizedNeedHours` with age). Not stored: today only. */
    SLEEP_NEED_REST,
    /** The fixed need the stored Rest and Charge use (`restFromDaily`, 8 h). */
    SLEEP_NEED_STORED,
    /** The Sleep tab's descriptive need behind Hours vs needed (mean asleep, floor 7.5 h; imported wins). */
    SLEEP_NEED_SLEEP_TAB,
    /** The need sleep debt measures against (`personalizedNeedHours`, age not plumbed). */
    SLEEP_NEED_DEBT,
    /** The one sleep need (R9a V2-1, S0-3): `personalizedNeedHours` over the 21 nights ending that night, with age.
     *  Served for every day; screens read it in place of the four above once [V2Change.ONE_SLEEP_NEED] is on. */
    SLEEP_NEED,
    SLEEP_DEBT, HOURS_VS_NEEDED,
    TRAINING_LOAD_CTL, TRAINING_LOAD_ATL, TRAINING_FORM, ACWR, READINESS,
    ACTIVE_KCAL, MAX_HR, VO2_MAX, FITNESS_AGE, VITALITY, BODY_AGE,
    /** RS-3c: how well a night went (`SleepScore`), beside Rest. Behind [V2Change.SLEEP_SCORE]. */
    SLEEP_SCORE,
}

/** Storage unit of a [MetricValue]. Named `MetricUnit` rather than the plan's `Unit` so it can't shadow `kotlin.Unit`. */
enum class MetricUnit {
    PERCENT, EFFORT_0_100, MILLISECONDS, BPM, BREATHS_PER_MIN, CELSIUS_DELTA, MINUTES, STEPS, STRESS_0_3,
    HOURS, KCAL, LOAD, RATIO, ML_KG_MIN, YEARS, SCORE,
    /** A category stored as its ordinal; [MetricRegistry.band] names it. */
    CATEGORY,
}

enum class Provenance { WHOOP, NOOP, IMPORTED, USER }

/** User-facing confidence. The internal v10 tiers BUILDING and SOLID both read as SETTLED. */
enum class Confidence { CALIBRATING, SETTLED }

enum class MissingReason {
    NOT_WORN, NOT_SYNCED, UNSTAGED, ESTIMATOR_OFF, DEVICE_UNSUPPORTED, NEEDS_AGE, INSUFFICIENT_HISTORY,
    /** v10 computes the value but never stores it, so a past day's value cannot be recovered. */
    NOT_STORED,
}

/** Calibration progress, for example 2 of 4 nights. */
data class Progress(val done: Int, val total: Int)

data class MetricValue(
    val id: MetricId,
    val day: DayKey,
    val value: Double?,
    val unit: MetricUnit,
    val provenance: Provenance?,
    val confidence: Confidence,
    val missing: MissingReason? = null,
    /** Set when [value] is a prior day's reading carried over onto today (Charge carry-over, #543). */
    val carriedFrom: DayKey? = null,
    val progress: Progress? = null,
    /** Set when [value] is not a measurement of its own (R9b): the SpO₂ strap estimate. */
    val provenanceNote: ValueNote? = null,
)

/** What kind of number a [MetricValue] is, when it is not a plain reading. */
enum class ValueNote { STRAP_ESTIMATE }

/** A named band: [lower] inclusive, [upperExclusive] exclusive (null = no upper edge). */
data class Band(val key: String, val lower: Double, val upperExclusive: Double?)

/**
 * The range a value is judged "normal" against, and whether it is personal or the population fallback. [mean] and
 * [sd] are the statistics a personal band was built from, when the band is not simply mean ± k·SD (R9a V2-4 widens a
 * narrow band to a minimum); null for v10's band, whose centre and SD follow from its edges.
 */
data class NormalRange(
    val low: Double,
    val high: Double,
    val basis: Basis,
    val nights: Int,
    val mean: Double? = null,
    val sd: Double? = null,
) {
    enum class Basis { PERSONAL, POPULATION }
}

/** Static facts about a metric: unit, display precision, natural scale and where the v10 formula lives. */
data class MetricDefinition(
    val id: MetricId,
    val unit: MetricUnit,
    val decimals: Int,
    val scaleMin: Double?,
    val scaleMax: Double?,
    /** The frozen formula page under docs/ridge-build/metrics/. */
    val formulaPage: String,
)

interface MetricRegistry {
    fun value(id: MetricId, day: DayKey): MetricValue

    /** One entry per day from [from] to [to] inclusive, gaps included as `value = null` with a reason. */
    fun series(id: MetricId, from: DayKey, to: DayKey): List<MetricValue>

    /** The band [value] falls in, or null when v10 defines no bands for [id]. */
    fun band(id: MetricId, value: Double): Band?

    fun normalRange(id: MetricId, day: DayKey): NormalRange?

    fun definition(id: MetricId): MetricDefinition
}
