package com.noop.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.noop.R
import com.noop.data.DailyMetric
import com.noop.metrics.MetricId

// B2-X: the one catalogue of everything NOOP can chart as a daily line. Insights' metric picker and Today's key-metric
// options both read it, so the two lists cannot drift apart. It only says where each series comes from; no value or
// formula is changed here.

/** The picker's groups, in the order they are listed (the way WHOOP groups its Trend view). */
internal enum class CatCategory(@StringRes val labelRes: Int) {
    EFFORT(R.string.cat_ridge_c_effort),
    RECOVERY(R.string.cat_ridge_c_recovery),
    SLEEP(R.string.cat_ridge_c_sleep),
    HEALTH(R.string.cat_ridge_c_health),
    ACTIVITY(R.string.cat_ridge_c_activity),
    BODY(R.string.cat_ridge_c_body),
    NUTRITION(R.string.cat_ridge_c_nutrition),
    MIND(R.string.cat_ridge_c_mind),
}

/** How a metric's daily series is drawn in Insights Explore (BS-x2-4 item 20). Compare keeps lines for every metric. */
internal enum class CatStyle {
    LINE,

    /** Daily bars from zero; a day with no reading is left out of the line of days (Sleep debt: untracked nights). */
    BAR,

    /** Daily bars on the calendar: a day with no reading is an empty slot, not skipped (Active calories: days without a workout). */
    BAR_DAILY,
}

/** Where a metric's daily series is read from. */
internal sealed interface CatSource {
    /** A stored daily column. */
    class Daily(val pick: (DailyMetric) -> Double?) : CatSource

    /** The metric registry's day series, scaled to the display unit. */
    class Registry(val id: MetricId, val scale: Double = 1.0) : CatSource

    /** A stored long-format series, read from each device id in turn (newest wins per day). `null` is the strap's own
     *  id (its active-id union canonical "my-whoop" set). */
    class Series(val key: String, val devices: List<String?>) : CatSource

    /** Imported body weight in kilograms: Apple Health and Health Connect's daily rows, the newest source per day. */
    data object Weight : CatSource

    /** Calories burned in the saved workouts, summed per day (the "Active calories" metric). */
    data object WorkoutKcal : CatSource

    /** Health Connect's own general-activity energy per day (the "Active energy" metric). Only ever from Health Connect. */
    data object HcActive : CatSource

    /** A day's mean or peak of the heart-rate samples (BS-x2-3 item 18), from the strap's 5-minute buckets. */
    class HrDaily(val stat: HrDayStat) : CatSource
}

/**
 * One metric. [key] is stable: it is the deep-link key Today and Explore already use for the first eight. [tile] is the
 * Today key-metric this entry is offered as (null: not offered), [raw] its saved-layout text.
 */
internal data class CatalogEntry(
    val key: String,
    @StringRes val titleRes: Int,
    val unit: String,
    val category: CatCategory,
    val icon: ImageVector,
    val accent: () -> Color,
    val higherIsBetter: Boolean?,
    val decimals: Int,
    val source: CatSource,
    val aliases: Set<String> = emptySet(),
    val style: CatStyle = CatStyle.LINE,
)

internal object MetricCatalog {
    private val NUTRITION = com.noop.ingest.NutritionCsvImporter.SOURCE_ID
    private val MOOD = com.noop.data.MoodStore.MOOD_DEVICE_ID
    private val APPLE_HC = listOf("apple-health", "health-connect")

    private fun e(
        key: String, @StringRes title: Int, unit: String, cat: CatCategory, icon: ImageVector, accent: () -> Color,
        better: Boolean?, decimals: Int, source: CatSource, vararg aliases: String,
    ) = CatalogEntry(key, title, unit, cat, icon, accent, better, decimals, source, aliases.toSet())

    /** Every chartable metric, grouped by category in list order. */
    val entries: List<CatalogEntry> = listOf(
        // Effort
        e("strain", R.string.l10n_trends_explore_screen_effort_8c974bc6, "/100", CatCategory.EFFORT, Icons.Filled.Bolt, { Palette.strain066 }, null, 1, CatSource.Daily { it.strain }, "effort"),
        // Steps read through the registry (strap, else the phone import, else the on-device estimate): the same figure the Steps page shows.
        e("steps", R.string.cat_ridge_steps, "", CatCategory.EFFORT, Icons.Filled.DirectionsWalk, { Palette.metricCyan }, true, 0, CatSource.Registry(MetricId.STEPS), "steps_est"),
        e("active_kcal", R.string.cat_ridge_active_kcal, "kcal", CatCategory.EFFORT, Icons.Filled.LocalFireDepartment, { Palette.metricAmber }, null, 0, CatSource.Registry(MetricId.ACTIVE_KCAL)),
        e("workout_kcal", R.string.cat_ridge_workout_kcal, "kcal", CatCategory.EFFORT, Icons.Filled.FitnessCenter, { Palette.metricAmber }, null, 0, CatSource.WorkoutKcal).copy(style = CatStyle.BAR_DAILY),
        e("hc_active_kcal", R.string.cat_ridge_hc_active_kcal, "kcal", CatCategory.EFFORT, Icons.Filled.Whatshot, { Palette.metricAmber }, null, 0, CatSource.HcActive),
        e("exercise_count", R.string.cat_ridge_exercise_count, "", CatCategory.EFFORT, Icons.Filled.FitnessCenter, { Palette.strain066 }, null, 0, CatSource.Daily { it.exerciseCount?.toDouble() }),
        e("ctl", R.string.cat_ridge_ctl, "", CatCategory.EFFORT, Icons.Filled.Timeline, { Palette.strain066 }, null, 1, CatSource.Registry(MetricId.TRAINING_LOAD_CTL)),
        e("atl", R.string.cat_ridge_atl, "", CatCategory.EFFORT, Icons.Filled.Timeline, { Palette.strain066 }, null, 1, CatSource.Registry(MetricId.TRAINING_LOAD_ATL)),
        e("form", R.string.cat_ridge_form, "", CatCategory.EFFORT, Icons.Filled.Timeline, { Palette.strain066 }, true, 1, CatSource.Registry(MetricId.TRAINING_FORM)),
        e("acwr", R.string.cat_ridge_acwr, "", CatCategory.EFFORT, Icons.Filled.Speed, { Palette.strain066 }, null, 2, CatSource.Registry(MetricId.ACWR)),
        // Recovery
        e("recovery", R.string.l10n_trends_explore_screen_charge_d4e1aee4, "%", CatCategory.RECOVERY, Icons.Filled.Favorite, { Palette.accent }, true, 0, CatSource.Daily { it.recovery }, "charge"),
        e("hrv", R.string.cat_ridge_hrv, "ms", CatCategory.RECOVERY, Icons.Filled.MonitorHeart, { Palette.metricPurple }, true, 0, CatSource.Daily { it.avgHrv }),
        e("sdnn", R.string.cat_ridge_sdnn, "ms", CatCategory.RECOVERY, Icons.Filled.MonitorHeart, { Palette.metricPurple }, true, 0, CatSource.Daily { it.avgSdnn }),
        e("rhr", R.string.l10n_trends_explore_screen_resting_hr_26677094, "bpm", CatCategory.RECOVERY, Icons.Filled.MonitorHeart, { Palette.metricRose }, false, 0, CatSource.Daily { it.restingHr?.toDouble() }),
        e("resp", R.string.l10n_trends_explore_screen_respiratory_rate_3fbb532f, "rpm", CatCategory.RECOVERY, Icons.Filled.Air, { Palette.accent }, null, 1, CatSource.Daily { it.respRateBpm }),
        e("avg_hr", R.string.explore_metric_average_heart_rate, "bpm", CatCategory.RECOVERY, Icons.Filled.MonitorHeart, { Palette.metricRose }, null, 0, CatSource.HrDaily(HrDayStat.AVERAGE)),
        e("max_hr", R.string.explore_metric_max_heart_rate, "bpm", CatCategory.RECOVERY, Icons.Filled.MonitorHeart, { Palette.metricRose }, null, 0, CatSource.HrDaily(HrDayStat.MAX)),
        e("stress", R.string.cat_ridge_stress, "/3", CatCategory.RECOVERY, Icons.Filled.ShowChart, { Ridge.stateModerate }, false, 1, CatSource.Registry(MetricId.STRESS)),
        // Sleep
        e("sleep", R.string.l10n_trends_explore_screen_sleep_3cac34e6, "h", CatCategory.SLEEP, Icons.Filled.Bedtime, { Palette.accent }, true, 1, CatSource.Daily { it.totalSleepMin?.let { m -> m / 60.0 } }, "rest"),
        // RS-3c: how well each night went, beside Rest.
        e("sleep_score", R.string.sleep_score_title, "/100", CatCategory.SLEEP, Icons.Filled.Bedtime, { Palette.accent }, true, 0, CatSource.Registry(MetricId.SLEEP_SCORE)),
        e("efficiency", R.string.l10n_trends_explore_screen_sleep_efficiency_b4b5c293, "%", CatCategory.SLEEP, Icons.Filled.Bedtime, { Palette.accent }, true, 0, CatSource.Daily { efficiencyPercent(it.efficiency) }),
        e("deep_min", R.string.cat_ridge_deep, "min", CatCategory.SLEEP, Icons.Filled.Hotel, { Palette.metricPurple }, true, 0, CatSource.Daily { it.deepMin }),
        e("rem_min", R.string.cat_ridge_rem, "min", CatCategory.SLEEP, Icons.Filled.Hotel, { Palette.metricPurple }, true, 0, CatSource.Daily { it.remMin }),
        e("light_min", R.string.cat_ridge_light, "min", CatCategory.SLEEP, Icons.Filled.Hotel, { Palette.metricCyan }, null, 0, CatSource.Daily { it.lightMin }),
        e("disturbances", R.string.cat_ridge_disturbances, "", CatCategory.SLEEP, Icons.Filled.Hotel, { Palette.statusWarning }, false, 0, CatSource.Daily { it.disturbances?.toDouble() }),
        e("sleep_need", R.string.cat_ridge_sleep_need, "h", CatCategory.SLEEP, Icons.Filled.Bedtime, { Palette.accent }, null, 1, CatSource.Registry(MetricId.SLEEP_NEED)),
        e("sleep_debt", R.string.cat_ridge_sleep_debt, "min", CatCategory.SLEEP, Icons.Filled.Bedtime, { Palette.statusWarning }, true, 0, CatSource.Registry(MetricId.SLEEP_DEBT)).copy(style = CatStyle.BAR),
        e("hours_vs_needed", R.string.cat_ridge_hours_vs_needed, "%", CatCategory.SLEEP, Icons.Filled.Bedtime, { Palette.accent }, true, 0, CatSource.Registry(MetricId.HOURS_VS_NEEDED)),
        // Health
        e("spo2", R.string.l10n_trends_explore_screen_blood_oxygen_a8ad9ff5, "%", CatCategory.HEALTH, Icons.Filled.Opacity, { Palette.metricCyan }, true, 0, if (com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.SPO2_ONE_SOURCE)) CatSource.Registry(MetricId.SPO2) else CatSource.Daily { it.spo2Pct }),
        e("skin", R.string.cat_ridge_skin_temp, "°C", CatCategory.HEALTH, Icons.Filled.Thermostat, { Palette.metricAmber }, null, 1, CatSource.Daily { it.skinTempDevC }, "skin_temp"),
        e("vo2max", R.string.cat_ridge_vo2, "ml/kg", CatCategory.HEALTH, Icons.Filled.Speed, { Palette.accent }, true, 1, CatSource.Registry(MetricId.VO2_MAX)),
        e("fitness_age", R.string.cat_ridge_fitness_age, "yr", CatCategory.HEALTH, Icons.Filled.Speed, { Palette.accent }, false, 0, CatSource.Registry(MetricId.FITNESS_AGE)),
        e("vitality", R.string.cat_ridge_vitality, "", CatCategory.HEALTH, Icons.Filled.Favorite, { Palette.accent }, true, 0, CatSource.Registry(MetricId.VITALITY)),
        e("body_age", R.string.cat_ridge_body_age, "yr", CatCategory.HEALTH, Icons.Filled.Favorite, { Palette.accent }, false, 0, CatSource.Registry(MetricId.BODY_AGE)),
        // Body
        e("weight", R.string.cat_ridge_weight, "kg", CatCategory.BODY, Icons.Filled.MonitorWeight, { Palette.accent }, null, 1, CatSource.Weight),
        e("body_fat", R.string.cat_ridge_body_fat, "%", CatCategory.BODY, Icons.Filled.MonitorWeight, { Palette.metricAmber }, null, 1, CatSource.Series("body_fat", APPLE_HC)),
        e("lean_mass", R.string.cat_ridge_lean_mass, "kg", CatCategory.BODY, Icons.Filled.MonitorWeight, { Palette.accent }, null, 1, CatSource.Series("lean_mass", APPLE_HC)),
        e("bmi", R.string.cat_ridge_bmi, "", CatCategory.BODY, Icons.Filled.MonitorWeight, { Palette.accent }, null, 1, CatSource.Series("bmi", APPLE_HC)),
        // Nutrition
        e("calories_in", R.string.explore_metric_calories_in, "kcal", CatCategory.NUTRITION, Icons.Filled.Restaurant, { Palette.metricAmber }, null, 0, CatSource.Series("calories_in", listOf(NUTRITION))),
        e("protein_g", R.string.explore_metric_protein, "g", CatCategory.NUTRITION, Icons.Filled.Restaurant, { Palette.metricCyan }, null, 0, CatSource.Series("protein_g", listOf(NUTRITION))),
        e("carbs_g", R.string.explore_metric_carbs, "g", CatCategory.NUTRITION, Icons.Filled.Restaurant, { Palette.metricCyan }, null, 0, CatSource.Series("carbs_g", listOf(NUTRITION))),
        e("fat_g", R.string.explore_metric_fat, "g", CatCategory.NUTRITION, Icons.Filled.Restaurant, { Palette.metricCyan }, null, 0, CatSource.Series("fat_g", listOf(NUTRITION))),
        // Mind
        e("mood", R.string.explore_metric_mood, "/5", CatCategory.MIND, Icons.Filled.Mood, { Palette.metricPurple }, true, 0, CatSource.Series("mood", listOf(MOOD))),
    )

    private val byKey: Map<String, CatalogEntry> =
        entries.flatMap { en -> (en.aliases + en.key).map { it to en } }.toMap()

    /** The entry for a deep-link key (a key or an alias), or null. */
    fun forKey(key: String?): CatalogEntry? = key?.let { byKey[it] }

    /** The registry metrics deliberately not charted, each with the reason (checked by the catalogue test). */
    val notCharted: Map<MetricId, String> = mapOf(
        MetricId.READINESS to "a three-level category, not a number",
        MetricId.MAX_HR to "a setting (the zones' maximum), not a daily series",
        MetricId.SLEEP_DURATION to "served as Sleep hours from the daily column",
        MetricId.SLEEP_NEED_REST to "one of four needs the one Sleep need replaces",
        MetricId.SLEEP_NEED_STORED to "one of four needs the one Sleep need replaces",
        MetricId.SLEEP_NEED_SLEEP_TAB to "one of four needs the one Sleep need replaces",
        MetricId.SLEEP_NEED_DEBT to "one of four needs the one Sleep need replaces",
        MetricId.CHARGE to "charted as Charge (daily column)",
        MetricId.EFFORT to "charted as Effort (daily column)",
        MetricId.REST to "charted as Sleep (the Rest score's input)",
        MetricId.HRV to "charted as HRV (daily column)",
        MetricId.RESTING_HR to "charted as Resting HR (daily column)",
        MetricId.RESP_RATE to "charted as Respiratory rate (daily column)",
        MetricId.SKIN_TEMP_DEV to "charted as Skin temperature (daily column)",
    ) + if (com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.SPO2_ONE_SOURCE)) {
        // BS-x2-2 item 9: with the one-source change on, Blood oxygen is charted through the registry, so it is not excluded.
        emptyMap()
    } else {
        mapOf(MetricId.SPO2 to "charted as Blood oxygen (daily column)")
    }

    /** Registry ids the catalogue reads. */
    val registryIds: Set<MetricId> =
        entries.mapNotNull { (it.source as? CatSource.Registry)?.id }.toSet()
}
