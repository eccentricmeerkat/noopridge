package com.noop.ui

import android.content.Context
import androidx.annotation.StringRes
import com.noop.R

// MARK: - Editable Key-Metrics layout (#251)
//
// The Today screen's "Key Metrics" grid was a fixed list of ten tiles in one order. This lets the user
// choose WHICH tiles show and in WHAT order, with the default being the original order so nothing changes
// for anyone who never opens the editor. Persistence is display-only — no metric is computed or stored
// differently; this just decides which of the already-computed tiles render and in what sequence.
//
// Stored as a single comma-joined string of metric keys in SharedPreferences ("today.keyMetrics"), the
// same mechanism every other Android preference uses. Mirrors the macOS KeyMetricPrefs.swift +
// @AppStorage("today.keyMetrics"). Unknown keys are dropped on read so a removed tile can't crash, and
// any known key missing from the saved list is treated as disabled (the editor re-lists it).

/**
 * One of the Today screen's Key-Metric tiles. The [raw] is the stable persisted identifier — keep it
 * byte-identical to the macOS `KeyMetric` enum so a backup/restore reads the same layout on either OS.
 */
enum class KeyMetric(val raw: String, @StringRes val titleRes: Int, val catalogKey: String? = null) {
    CHARGE("charge", R.string.today_metric_charge),
    EFFORT("effort", R.string.today_metric_effort),
    REST("rest", R.string.today_metric_rest),
    HRV("hrv", R.string.today_metric_hrv),
    RESTING_HR("restingHr", R.string.today_metric_resting_hr),
    BLOOD_OXYGEN("bloodOxygen", R.string.today_metric_blood_oxygen),
    RESPIRATORY("respiratory", R.string.today_metric_respiratory),
    STEPS("steps", R.string.today_metric_steps),
    WEIGHT("weight", R.string.today_metric_weight),
    CALORIES("calories", R.string.today_metric_calories),
    // Added 2026-08-24 (queue 11c follow-up): Skin Temp was already a "Your Cards" (DashboardCard)
    // option, but was never offered as a Key Metrics tile — not a bug, just never added. Reuses
    // DashboardCard.SKIN_TEMP's own title resource rather than adding a new one. New case, NOT added
    // to defaultOrder below, so an existing user's saved layout (and a fresh install's default) is
    // byte-identical to before; only opts in via the layout editor.
    SKIN_TEMP("skinTemp", R.string.today_card_skin_temp),

    // B2-X: one opt-in tile per further chartable metric in [MetricCatalog] (never in [defaultOrder], so no saved layout
    // and no fresh install changes). The tile's value, trend and tap come from the catalogue entry named by catalogKey.
    SDNN("sdnn", R.string.cat_ridge_sdnn, "sdnn"),
    AVG_HR("avgHr", R.string.explore_metric_average_heart_rate, "avg_hr"),
    MAX_HR_DAY("maxHrDay", R.string.explore_metric_max_heart_rate, "max_hr"),
    STRESS("stress", R.string.cat_ridge_stress, "stress"),
    SLEEP_HOURS("sleepHours", R.string.l10n_trends_explore_screen_sleep_3cac34e6, "sleep"),
    SLEEP_SCORE("sleepScore", R.string.sleep_score_title, "sleep_score"),
    SLEEP_EFFICIENCY("sleepEfficiency", R.string.l10n_trends_explore_screen_sleep_efficiency_b4b5c293, "efficiency"),
    DEEP_SLEEP("deepSleep", R.string.cat_ridge_deep, "deep_min"),
    REM_SLEEP("remSleep", R.string.cat_ridge_rem, "rem_min"),
    LIGHT_SLEEP("lightSleep", R.string.cat_ridge_light, "light_min"),
    DISTURBANCES("disturbances", R.string.cat_ridge_disturbances, "disturbances"),
    SLEEP_NEED("sleepNeed", R.string.cat_ridge_sleep_need, "sleep_need"),
    SLEEP_DEBT("sleepDebt", R.string.cat_ridge_sleep_debt, "sleep_debt"),
    HOURS_VS_NEEDED("hoursVsNeeded", R.string.cat_ridge_hours_vs_needed, "hours_vs_needed"),
    WORKOUTS("workouts", R.string.cat_ridge_exercise_count, "exercise_count"),
    TRAINING_FITNESS("trainingFitness", R.string.cat_ridge_ctl, "ctl"),
    TRAINING_FATIGUE("trainingFatigue", R.string.cat_ridge_atl, "atl"),
    TRAINING_FORM("trainingForm", R.string.cat_ridge_form, "form"),
    LOAD_RATIO("loadRatio", R.string.cat_ridge_acwr, "acwr"),
    VO2_MAX("vo2Max", R.string.cat_ridge_vo2, "vo2max"),
    FITNESS_AGE("fitnessAge", R.string.cat_ridge_fitness_age, "fitness_age"),
    VITALITY("vitality", R.string.cat_ridge_vitality, "vitality"),
    BODY_AGE("bodyAge", R.string.cat_ridge_body_age, "body_age"),
    BODY_FAT("bodyFat", R.string.cat_ridge_body_fat, "body_fat"),
    LEAN_MASS("leanMass", R.string.cat_ridge_lean_mass, "lean_mass"),
    BMI("bmi", R.string.cat_ridge_bmi, "bmi"),
    CALORIES_IN("caloriesIn", R.string.explore_metric_calories_in, "calories_in"),
    PROTEIN("protein", R.string.explore_metric_protein, "protein_g"),
    CARBS("carbs", R.string.explore_metric_carbs, "carbs_g"),
    FAT("fat", R.string.explore_metric_fat, "fat_g"),
    MOOD("mood", R.string.explore_metric_mood, "mood");

    companion object {
        fun fromRaw(raw: String?): KeyMetric? = entries.firstOrNull { it.raw == raw }

        /** The most tiles Today will show (owner, 20 Nov): the editor stops adding at this many. */
        const val MAX_SHOWN = 9

        /** The original grid order, less Weight: the default when the layout isn't customised. B2-R (D5, owner 24 Nov): nine
         *  tiles, the cap in [MAX_SHOWN], so the editor can always add one. Weight is the one dropped because it needs an
         *  import or a weigh-in, so it would read "No data" for most fresh installs. It is still in the editor. */
        val defaultOrder: List<KeyMetric> = listOf(
            CHARGE, EFFORT, REST, HRV, RESTING_HR,
            BLOOD_OXYGEN, RESPIRATORY, STEPS, CALORIES,
        )
    }
}

/**
 * Display-only persistence for the Key-Metrics layout. Holds an ORDERED list of the enabled tiles; a tile
 * not in the list is hidden. SharedPreferences isn't reactive, so the Today screen reads this once into
 * remembered state (like the other prefs) and re-reads on the recomposition the editor's write triggers.
 * Mirrors the macOS KeyMetricPrefs (@AppStorage "today.keyMetrics").
 */
object KeyMetricPrefs {
    private const val KEY_LAYOUT = "today.keyMetrics"
    private const val KEY_DETAILED = "today.keyMetricsDetailed"

    /** Whether the Key-Metrics tiles render DETAILED — taller/squarer with a 14-day trend graph under the
     *  fill bar. Display-only. Default ON on Android since the glass Today redesign
     *  (docs/assets/noop_UI_x2.png), whose tiles all carry the trend line; the compact ktile look stays one
     *  switch away in the #251 editor. Key name is parity-ready for the macOS/iOS twin
     *  (@AppStorage "today.keyMetricsDetailed"), though their default is unchanged. */
    fun detailed(context: Context): Boolean =
        NoopPrefs.of(context).getBoolean(KEY_DETAILED, true)

    fun setDetailed(context: Context, value: Boolean) {
        NoopPrefs.of(context).edit().putBoolean(KEY_DETAILED, value).apply()
    }

    private const val KEY_WINDOW = "today.keyMetricsWindowDays"

    /** Trailing trend window (calendar days) the DETAILED tiles graph: 7, 14 (default) or 30. Shared key
     *  with the iOS twin; an unknown stored value coerces to 14 so a bad pref can't skew the window math
     *  (this also migrates users off the retired 2-day option). */
    fun detailWindowDays(context: Context): Int =
        NoopPrefs.of(context).getInt(KEY_WINDOW, 14).let { if (it == 7 || it == 14 || it == 30) it else 14 }

    fun setDetailWindowDays(context: Context, value: Int) {
        NoopPrefs.of(context).edit().putInt(KEY_WINDOW, value).apply()
    }

    /** The enabled tiles in display order. An empty/unset string yields the full default order. */
    fun enabled(context: Context): List<KeyMetric> =
        decodeEnabled(NoopPrefs.of(context).getString(KEY_LAYOUT, null))

    /** Persist the enabled tiles in order. Disabled tiles are simply omitted from the stored string. */
    fun setEnabled(context: Context, metrics: List<KeyMetric>) {
        NoopPrefs.of(context).edit().putString(KEY_LAYOUT, encode(metrics)).apply()
    }

    /** Encode an ordered list of enabled tiles into the stored comma-joined string. */
    fun encode(metrics: List<KeyMetric>): String = metrics.joinToString(",") { it.raw }

    /**
     * Decode the stored string into an ordered list of enabled tiles. An empty/unset string yields the
     * full default order (so a fresh install shows every tile). Unknown tokens are ignored, duplicates
     * collapsed; this returns ONLY the enabled tiles in their saved order.
     */
    fun decodeEnabled(raw: String?): List<KeyMetric> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return KeyMetric.defaultOrder
        val seen = LinkedHashSet<KeyMetric>()
        trimmed.split(",").forEach { token ->
            KeyMetric.fromRaw(token.trim())?.let { seen.add(it) }
        }
        return if (seen.isEmpty()) KeyMetric.defaultOrder else seen.toList()
    }
}
